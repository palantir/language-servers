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

package com.palantir.groovylanguageserver;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.palantir.groovylanguageserver.util.DefaultDiagnosticBuilder;
import com.palantir.groovylanguageserver.util.Ranges;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.Position;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.builders.LocationBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.VariableExpression;
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

    private static final Logger log = LoggerFactory.getLogger(GroovycWrapper.class);

    private static final String GROOVY_DEFAULT_INTERFACE = "groovy.lang.GroovyObject";
    private static final String GROOVY_EXTENSION = "groovy";
    private static final String JAVA_DEFAULT_OBJECT = "java.lang.Object";

    private final Path workspaceRoot;
    private final CompilationUnit unit;
    // Maps from source file -> set of symbols in that file
    private Map<String, Set<SymbolInformation>> fileSymbols = Maps.newHashMap();
    // Maps from type -> set of references of this type
    private Map<String, Set<SymbolInformation>> references = Maps.newHashMap();

    private GroovycWrapper(CompilationUnit unit, Path workspaceRoot) {
        this.unit = unit;
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Creates a new instance of GroovycWrapper.
     * @param targetDirectory the directory in which to put generated files
     * @param workspaceRoot the directory to compile
     * @return the newly created GroovycWrapper
     */
    public static GroovycWrapper of(Path targetDirectory, Path workspaceRoot) {
        Preconditions.checkNotNull(workspaceRoot, "workspaceRoot must not be null");
        Preconditions.checkNotNull(targetDirectory, "targetDirectory must not be null");
        Preconditions.checkArgument(workspaceRoot.toFile().isDirectory(), "workspaceRoot must be a directory");
        Preconditions.checkArgument(targetDirectory.toFile().isDirectory(), "targetDirectory must be a directory");

        CompilerConfiguration config = new CompilerConfiguration();
        config.setTargetDirectory(targetDirectory.toFile());
        GroovycWrapper wrapper = new GroovycWrapper(new CompilationUnit(config), workspaceRoot);
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
    public Map<String, Set<SymbolInformation>> getFileSymbols() {
        return fileSymbols;
    }

    @Override
    public Map<String, Set<SymbolInformation>> getReferences() {
        return references;
    }

    @Override
    public Set<SymbolInformation> findReferences(ReferenceParams params) {
        Set<SymbolInformation> symbols = fileSymbols.get(params.getTextDocument().getUri());
        if (symbols == null) {
            return Sets.newHashSet();
        }
        Set<SymbolInformation> foundSymbolInformations = symbols.stream()
                .filter(s -> {
                    // If we are given a position that is located within the first line of this symbol's location, then
                    // this is its definition and is likely to be the symbol we are trying to find.
                    Position startPosition = s.getLocation().getRange().getStart();
                    Range definitionRange =
                            Ranges.createRange(startPosition.getLine(), startPosition.getCharacter(),
                                    startPosition.getLine() + 1, 0);
                    return Ranges.isValid(s.getLocation().getRange())
                            && Ranges.contains(definitionRange, params.getPosition())
                            && (s.getKind() == SymbolKind.Class || s.getKind() == SymbolKind.Interface
                                    || s.getKind() == SymbolKind.Enum);
                })
                .collect(Collectors.toSet());

        if (foundSymbolInformations.isEmpty()) {
            return Sets.newHashSet();
        }

        SymbolInformation foundSymbol = Iterables.getOnlyElement(foundSymbolInformations);
        Set<SymbolInformation> foundReferences = Sets.newHashSet();

        if (params.getContext().isIncludeDeclaration()) {
            foundReferences.add(foundSymbol);
        }
        if (references.containsKey(foundSymbol.getName())) {
            foundReferences.addAll(references.get(foundSymbol.getName()));
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
            log.warn("Could not create valid pattern from query {}", query);
        }
        return Pattern.compile("^" + escaped);
    }

    private void addAllSourcesToCompilationUnit() {
        for (File file : Files.fileTreeTraverser().preOrderTraversal(workspaceRoot.toFile())) {
            if (file.isFile() && Files.getFileExtension(file.getAbsolutePath()).equals(GROOVY_EXTENSION)) {
                unit.addSource(file);
            }
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

                Range range = Ranges.createRange(
                        cause.getStartLine(), cause.getStartColumn(), cause.getEndLine(), cause.getEndColumn());
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
        Map<String, Set<SymbolInformation>> newReferences = Maps.newHashMap();

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
                for (ClassNode node : clazz.getInterfaces()) {
                    if (!node.getName().equals(GROOVY_DEFAULT_INTERFACE)) {
                        newReferences.computeIfAbsent(node.getName(), (value) -> Sets.newHashSet()).add(classSymbol);
                    }
                }
                // Add extended class reference
                if (clazz.getSuperClass() != null && !clazz.getSuperClass().getName().equals(JAVA_DEFAULT_OBJECT)) {
                    newReferences.computeIfAbsent(clazz.getSuperClass().getName(), (value) -> Sets.newHashSet())
                            .add(classSymbol);
                }

                // Add all the class's field symbols
                clazz.getFields().forEach(field -> {
                    SymbolInformation symbol = getVariableSymbolInformation(clazz.getName(), sourcePath, field);
                    newReferences.computeIfAbsent(field.getType().getName(), (value) -> Sets.newHashSet()).add(symbol);
                    symbols.add(symbol);
                });

                // Add all method symbols
                clazz.getAllDeclaredMethods()
                        .forEach(method -> {
                            symbols.addAll(getMethodSymbolInformations(newReferences, sourcePath, clazz, method));
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
                            newReferences.computeIfAbsent(variable.getType().getName(), (value) -> Sets.newHashSet())
                                    .add(symbol);
                            symbols.add(symbol);
                        });
            }
            newFileSymbols.put(sourcePath, symbols);
        });
        // Set the new references and new symbols
        references = newReferences;
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
        Location location;
        SymbolKind kind;
        if (variable instanceof DynamicVariable) {
            kind = SymbolKind.Field;
            location = new LocationBuilder().uri(sourcePath).range(Ranges.UNDEFINED_RANGE).build();
        } else if (variable instanceof FieldNode) {
            kind = SymbolKind.Field;
            location = createLocation(sourcePath, (FieldNode) variable);
        } else if (variable instanceof Parameter) {
            kind = SymbolKind.Variable;
            location = createLocation(sourcePath, (Parameter) variable);
        } else if (variable instanceof PropertyNode) {
            kind = SymbolKind.Field;
            location = createLocation(sourcePath, (PropertyNode) variable);
        } else if (variable instanceof VariableExpression) {
            kind = SymbolKind.Variable;
            location = createLocation(sourcePath, (VariableExpression) variable);
        } else {
            throw new IllegalArgumentException(String.format("Unknown type of variable: %s", variable));
        }
        return createSymbolInformation(variable.getName(), kind, location,
                Optional.of(parentName));
    }

    private Set<SymbolInformation> getMethodSymbolInformations(Map<String, Set<SymbolInformation>> newReferences,
            String sourcePath, ClassNode parent, MethodNode method) {
        Set<SymbolInformation> symbols = Sets.newHashSet();

        SymbolInformation methodSymbol =
                createSymbolInformation(method.getName(), SymbolKind.Method, createLocation(sourcePath, method),
                        Optional.of(parent.getName()));
        symbols.add(methodSymbol);
        newReferences.computeIfAbsent(method.getReturnType().getName(), (value) -> Sets.newHashSet()).add(methodSymbol);

        method.getVariableScope().getDeclaredVariables().values().forEach(variable -> {
            SymbolInformation variableSymbol = getVariableSymbolInformation(method.getName(), sourcePath, variable);
            newReferences.computeIfAbsent(variable.getType().getName(), (value) -> Sets.newHashSet())
                    .add(variableSymbol);
            symbols.add(variableSymbol);
        });
        return symbols;
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

    private static Location createLocation(String uri, ASTNode node) {
        return new LocationBuilder()
                .uri(uri)
                .range(Ranges.createRange(node.getLineNumber(), node.getColumnNumber(), node.getLastLineNumber(),
                        node.getLastColumnNumber()))
                .build();
    }

}
