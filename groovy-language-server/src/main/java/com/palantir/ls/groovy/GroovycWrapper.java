/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.ls.groovy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.palantir.ls.util.DefaultDiagnosticBuilder;
import com.palantir.ls.util.GroovyConstants;
import com.palantir.ls.util.Ranges;
import com.palantir.ls.util.SourceWriter;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.TextDocumentContentChangeEvent;
import io.typefox.lsapi.builders.LocationBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the Groovy compiler and provides Language Server Protocol diagnostics on compile.
 */
public final class GroovycWrapper implements CompilerWrapper {

    private static final Logger logger = LoggerFactory.getLogger(GroovycWrapper.class);

    private static final String GROOVY_DEFAULT_INTERFACE = "groovy.lang.GroovyObject";
    private static final String JAVA_DEFAULT_OBJECT = "java.lang.Object";

    private final Path workspaceRoot;
    private final Path changedFilesRoot;
    private final CompilerConfiguration config;

    private CompilationUnit unit;

    // Maps from source file -> set of symbols in that file
    private Map<String, Set<SymbolInformation>> fileSymbols = Maps.newHashMap();
    // Maps from type -> set of references of this type
    private Map<String, Set<SymbolInformation>> typeReferences = Maps.newHashMap();
    // Map from origin source filename to its changed version source writer
    private Map<Path, SourceWriter> originalSourceToChangedSource = Maps.newHashMap();

    private GroovycWrapper(Path workspaceRoot, Path changedFilesRoot, CompilerConfiguration config) {
        this.workspaceRoot = workspaceRoot;
        this.changedFilesRoot = changedFilesRoot;
        this.config = config;
        unit = new CompilationUnit(config);
    }

    /**
     * Creates a new instance of GroovycWrapper.
     *
     * @param targetDirectory the directory in which to put generated files
     * @param workspaceRoot the directory to compile
     * @param changedFilesRoot the directory in which to temporarily store incrementally changed files
     * @return the newly created GroovycWrapper
     */
    public static GroovycWrapper of(Path targetDirectory, Path workspaceRoot, Path changedFilesRoot) {
        checkNotNull(targetDirectory, "targetDirectory must not be null");
        checkNotNull(workspaceRoot, "workspaceRoot must not be null");
        checkNotNull(changedFilesRoot, "changedFilesRoot must not be null");
        checkArgument(targetDirectory.toFile().isDirectory(), "targetDirectory must be a directory");
        checkArgument(workspaceRoot.toFile().isDirectory(), "workspaceRoot must be a directory");
        checkArgument(changedFilesRoot.toFile().isDirectory(), "changedFilesRoot must be a directory");

        CompilerConfiguration config = new CompilerConfiguration();
        config.setTargetDirectory(targetDirectory.toFile());
        GroovycWrapper wrapper = new GroovycWrapper(workspaceRoot, changedFilesRoot, config);
        wrapper.addAllSourcesToCompilationUnit();

        return wrapper;
    }

    @Override
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @Override
    public Set<Diagnostic> compile() {
        try {
            unit.compile();
            // Symbols are only re-parsed if compilation was successful
            parseAllSymbols();
        } catch (MultipleCompilationErrorsException e) {
            return parseErrors(e.getErrorCollector());
        }
        return Sets.newHashSet();
    }

    @Override
    public void handleFileChanged(Path originalFile, List<TextDocumentContentChangeEvent> contentChanges) {
        try {
            SourceWriter sourceWriter = null;
            if (originalSourceToChangedSource.containsKey(originalFile)) {
                // New change on existing changed source
                sourceWriter = originalSourceToChangedSource.get(originalFile);
            } else {
                // New source to switch out
                Path newSourcePath = changedFilesRoot.resolve(workspaceRoot.relativize(originalFile));
                sourceWriter = SourceWriter.of(originalFile, newSourcePath);
                originalSourceToChangedSource.put(originalFile, sourceWriter);
            }
            // Apply changes to source writer and reset compilation unit
            sourceWriter.applyChanges(contentChanges);
            resetCompilationUnit();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public void handleFileClosed(Path originalFile) {
        if (!originalSourceToChangedSource.containsKey(originalFile)) {
            return;
        }
        Path changedSource = originalSourceToChangedSource.get(originalFile).getDestination();
        originalSourceToChangedSource.remove(originalFile);
        // Deleted the changed file
        if (!changedSource.toFile().delete()) {
            logger.error("Unable to delete file '{}'", changedSource.toAbsolutePath());
        }
        resetCompilationUnit();
    }

    @Override
    public void handleFileSaved(Path originalFile) {
        try {
            if (originalSourceToChangedSource.containsKey(originalFile)) {
                Path changedSource = originalSourceToChangedSource.get(originalFile).getDestination();
                originalSourceToChangedSource.get(originalFile).saveChanges();
                originalSourceToChangedSource.remove(originalFile);
                // Deleted the changed file
                if (!changedSource.toFile().delete()) {
                    logger.error("Unable to delete file '{}'", changedSource.toAbsolutePath());
                }
                resetCompilationUnit();
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public Map<String, Set<SymbolInformation>> getFileSymbols() {
        return fileSymbols;
    }

    @Override
    public Map<String, Set<SymbolInformation>> getTypeReferences() {
        return typeReferences;
    }

    @Override
    public Set<SymbolInformation> findReferences(ReferenceParams params) {
        Set<SymbolInformation> symbols = fileSymbols.get(params.getTextDocument().getUri());
        if (symbols == null) {
            return Sets.newHashSet();
        }
        List<SymbolInformation> foundSymbolInformations = symbols.stream()
                .filter(s -> Ranges.isValid(s.getLocation().getRange())
                        && Ranges.contains(s.getLocation().getRange(), params.getPosition())
                        && (s.getKind() == SymbolKind.Class || s.getKind() == SymbolKind.Interface
                                || s.getKind() == SymbolKind.Enum))
                // If there is more than one result, we want the symbol whose range starts the latest.
                .sorted((s1, s2) -> Ranges.POSITION_COMPARATOR.reversed()
                        .compare(s1.getLocation().getRange().getStart(), s2.getLocation().getRange().getStart()))
                .collect(Collectors.toList());

        if (foundSymbolInformations.isEmpty()) {
            return Sets.newHashSet();
        }

        SymbolInformation foundSymbol = foundSymbolInformations.get(0);
        Set<SymbolInformation> foundReferences = Sets.newHashSet();

        if (params.getContext().isIncludeDeclaration()) {
            foundReferences.add(foundSymbol);
        }
        if (typeReferences.containsKey(foundSymbol.getName())) {
            foundReferences.addAll(typeReferences.get(foundSymbol.getName()));
        }

        return foundReferences;
    }

    @Override
    public Set<SymbolInformation> getFilteredSymbols(String query) {
        Pattern pattern = getQueryPattern(query);
        return fileSymbols.values().stream().flatMap(Collection::stream)
                .filter(symbol -> pattern.matcher(symbol.getName()).matches())
                .collect(Collectors.toSet());
    }

    private Pattern getQueryPattern(String query) {
        String escaped = Pattern.quote(query);
        String newQuery = escaped.replaceAll("\\*", "\\\\E.*\\\\Q").replaceAll("\\?", "\\\\E.\\\\Q");
        newQuery = "^" + newQuery;
        try {
            return Pattern.compile(newQuery);
        } catch (PatternSyntaxException e) {
            logger.warn("Could not create valid pattern from query '{}'", query);
        }
        return Pattern.compile("^" + escaped);
    }

    private void addAllSourcesToCompilationUnit() {
        // We don't include the files that have a corresponding SourceWriter since that means they will be replaced.
        for (File file : Files.fileTreeTraverser().preOrderTraversal(workspaceRoot.toFile())) {
            if (!originalSourceToChangedSource.containsKey(file.toPath()) && file.isFile() && Files
                    .getFileExtension(file.getAbsolutePath()).equals(GroovyConstants.GROOVY_LANGUAGE_EXTENSION)) {
                unit.addSource(file);
            }
        }
        // Add the replaced sources
        originalSourceToChangedSource.values()
                .forEach(sourceWriter -> unit.addSource(sourceWriter.getDestination().toFile()));
    }

    private void resetCompilationUnit() {
        try {
            FileUtils.deleteDirectory(config.getTargetDirectory());
            if (!config.getTargetDirectory().mkdir()) {
                logger.error("Could not recreate target directory: '{}'",
                        config.getTargetDirectory().getAbsolutePath());
                throw new RuntimeException("Could not reset compiled files after changes. "
                        + "Make sure you have permission to modify your target directory.");
            }
            unit = new CompilationUnit(config);
            addAllSourcesToCompilationUnit();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    private Set<Diagnostic> parseErrors(ErrorCollector collector) {
        Set<Diagnostic> diagnostics = Sets.newHashSet();
        for (int i = 0; i < collector.getWarningCount(); i++) {
            WarningMessage message = collector.getWarning(i);

            diagnostics.add(new DefaultDiagnosticBuilder(message.getMessage(), DiagnosticSeverity.Warning).build());
        }
        for (int i = 0; i < collector.getErrorCount(); i++) {
            Message message = collector.getError(i);
            Diagnostic diagnostic;
            if (message instanceof SyntaxErrorMessage) {
                SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
                SyntaxException cause = syntaxErrorMessage.getCause();

                Range range =
                        Ranges.createZeroBasedRange(cause.getStartLine(), cause.getStartColumn(), cause.getEndLine(),
                                cause.getEndColumn());

                diagnostic = new DefaultDiagnosticBuilder(cause.getMessage(), DiagnosticSeverity.Error)
                        .range(range)
                        .source(cause.getSourceLocator())
                        .build();
            } else {
                StringWriter data = new StringWriter();
                PrintWriter writer = new PrintWriter(data);
                message.write(writer);
                diagnostic = new DefaultDiagnosticBuilder(data.toString(), DiagnosticSeverity.Error).build();
            }
            diagnostics.add(diagnostic);
        }
        return diagnostics;
    }

    private void parseAllSymbols() {
        Map<String, Set<SymbolInformation>> newFileSymbols = Maps.newHashMap();
        Map<String, Set<SymbolInformation>> newTypeReferences = Maps.newHashMap();

        unit.iterator().forEachRemaining(sourceUnit -> {
            Set<SymbolInformation> symbols = Sets.newHashSet();
            String sourcePath = sourceUnit.getSource().getURI().getPath();

            // This will iterate through all classes, interfaces and enums, including inner ones.
            sourceUnit.getAST().getClasses().forEach(clazz -> {
                // Add class symbol
                SymbolInformation classSymbol =
                        createSymbolInformation(clazz.getName(), getKind(clazz), createLocation(sourcePath, clazz),
                                Optional.fromNullable(clazz.getOuterClass()).transform(ClassNode::getName));
                symbols.add(classSymbol);

                // Add implemented interfaces reference
                Stream.of(clazz.getInterfaces())
                        .filter(node -> !node.getName().equals(GROOVY_DEFAULT_INTERFACE))
                        .forEach(node -> addToValueSet(newTypeReferences, node.getName(), classSymbol));

                // Add extended class reference
                if (clazz.getSuperClass() != null && !clazz.getSuperClass().getName().equals(JAVA_DEFAULT_OBJECT)) {
                    addToValueSet(newTypeReferences, clazz.getSuperClass().getName(), classSymbol);
                }

                // Add all the class's field symbols
                clazz.getFields().forEach(field -> {
                    SymbolInformation symbol = getVariableSymbolInformation(clazz.getName(), sourcePath, field);
                    addToValueSet(newTypeReferences, field.getType().getName(), symbol);
                    symbols.add(symbol);
                });

                // Add all method symbols
                clazz.getAllDeclaredMethods()
                        .forEach(method -> {
                            symbols.addAll(getMethodSymbolInformations(newTypeReferences, sourcePath, clazz, method));
                        });
            });

            // Add symbols declared within the statement block variable scope which includes script
            // defined variables.
            ClassNode scriptClass = sourceUnit.getAST().getScriptClassDummy();
            if (scriptClass != null) {
                sourceUnit.getAST().getStatementBlock().getVariableScope().getDeclaredVariables().values().forEach(
                        variable -> {
                            SymbolInformation symbol =
                                    getVariableSymbolInformation(scriptClass.getName(), sourcePath, variable);
                            addToValueSet(newTypeReferences, variable.getType().getName(), symbol);
                            symbols.add(symbol);
                        });
            }
            newFileSymbols.put(getWorkspaceUri(sourcePath), symbols);
        });
        // Set the new references and new symbols
        typeReferences = newTypeReferences;
        fileSymbols = newFileSymbols;
    }

    private static SymbolKind getKind(ClassNode node) {
        if (node.isInterface()) {
            return SymbolKind.Interface;
        } else if (node.isEnum()) {
            return SymbolKind.Enum;
        }
        return SymbolKind.Class;
    }

    private SymbolInformation getVariableSymbolInformation(String parentName, String sourcePath, Variable variable) {
        SymbolInformationBuilder builder =
                new SymbolInformationBuilder().name(variable.getName()).containerName(parentName);
        if (variable instanceof DynamicVariable) {
            builder.kind(SymbolKind.Field);
            builder.location(new LocationBuilder().uri(sourcePath).range(Ranges.UNDEFINED_RANGE).build());
        } else if (variable instanceof FieldNode) {
            builder.kind(SymbolKind.Field);
            builder.location(createLocation(sourcePath, (FieldNode) variable));
        } else if (variable instanceof Parameter) {
            builder.kind(SymbolKind.Variable);
            builder.location(createLocation(sourcePath, (Parameter) variable));
        } else if (variable instanceof PropertyNode) {
            builder.kind(SymbolKind.Field);
            builder.location(createLocation(sourcePath, (PropertyNode) variable));
        } else if (variable instanceof VariableExpression) {
            builder.kind(SymbolKind.Variable);
            builder.location(createLocation(sourcePath, (VariableExpression) variable));
        } else {
            throw new IllegalArgumentException(String.format("Unknown type of variable: %s", variable));
        }
        return builder.build();
    }

    private Set<SymbolInformation> getMethodSymbolInformations(Map<String, Set<SymbolInformation>> newTypeReferences,
            String sourcePath, ClassNode parent, MethodNode method) {
        Set<SymbolInformation> symbols = Sets.newHashSet();

        SymbolInformation methodSymbol =
                createSymbolInformation(method.getName(), SymbolKind.Method, createLocation(sourcePath, method),
                        Optional.of(parent.getName()));
        symbols.add(methodSymbol);
        addToValueSet(newTypeReferences, method.getReturnType().getName(), methodSymbol);

        // Method parameters
        method.getVariableScope().getDeclaredVariables().values().forEach(variable -> {
            SymbolInformation variableSymbol = getVariableSymbolInformation(method.getName(), sourcePath, variable);
            addToValueSet(newTypeReferences, variable.getType().getName(), variableSymbol);
            symbols.add(variableSymbol);
        });

        // Locally defined variables
        if (method.getCode() instanceof BlockStatement) {
            BlockStatement blockStatement = (BlockStatement) method.getCode();
            blockStatement.getVariableScope().getDeclaredVariables().values().forEach(variable -> {
                SymbolInformation variableSymbol = getVariableSymbolInformation(method.getName(), sourcePath, variable);
                addToValueSet(newTypeReferences, variable.getType().getName(), variableSymbol);
                symbols.add(variableSymbol);
            });
        }
        return symbols;
    }

    private String getWorkspaceUri(String uri) {
        return uri.startsWith(workspaceRoot.toString()) ? uri
                : workspaceRoot.resolve(changedFilesRoot.relativize(Paths.get(uri))).toString();
    }

    private Location createLocation(String uri, ASTNode node) {
        return new LocationBuilder()
                .uri(getWorkspaceUri(uri))
                .range(Ranges.createZeroBasedRange(node.getLineNumber(), node.getColumnNumber(),
                        node.getLastLineNumber(), node.getLastColumnNumber()))
                .build();
    }

    private static SymbolInformation createSymbolInformation(String name, SymbolKind kind, Location location,
            Optional<String> parentName) {
        return new SymbolInformationBuilder()
                .containerName(parentName.orNull())
                .kind(kind)
                .location(location)
                .name(name)
                .build();
    }

    private static void addToValueSet(Map<String, Set<SymbolInformation>> map, String key, SymbolInformation symbol) {
        map.computeIfAbsent(key, (value) -> Sets.newHashSet()).add(symbol);
    }

}
