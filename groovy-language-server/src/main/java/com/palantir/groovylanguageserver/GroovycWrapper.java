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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.palantir.groovylanguageserver.util.DiagnosticBuilder;
import com.palantir.groovylanguageserver.util.Ranges;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticImpl;
import io.typefox.lsapi.LocationImpl;
import io.typefox.lsapi.RangeImpl;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolInformationImpl;
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

    private static final String GROOVY_EXTENSION = "groovy";
    private final Path workspaceRoot;
    private final CompilationUnit unit;
    private Map<String, Set<SymbolInformation>> fileSymbols = Maps.newHashMap();

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
    public Set<DiagnosticImpl> compile() {
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

    private Set<DiagnosticImpl> parseErrors(ErrorCollector collector) {
        Set<DiagnosticImpl> diagnostics = Sets.newHashSet();
        for (int i = 0; i < collector.getWarningCount(); i++) {
            WarningMessage message = collector.getWarning(i);
            diagnostics.add(new DiagnosticBuilder(message.getMessage(), Diagnostic.SEVERITY_WARNING).build());
        }
        for (int i = 0; i < collector.getErrorCount(); i++) {
            Message message = collector.getError(i);
            DiagnosticImpl diagnostic;
            if (message instanceof SyntaxErrorMessage) {
                SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
                SyntaxException cause = syntaxErrorMessage.getCause();

                RangeImpl range = Ranges.createRange(
                        cause.getStartLine(), cause.getStartColumn(), cause.getEndLine(), cause.getEndColumn());
                diagnostic = new DiagnosticBuilder(cause.getMessage(), Diagnostic.SEVERITY_ERROR)
                                    .range(range).source(cause.getSourceLocator()).build();
            } else {
                StringWriter data = new StringWriter();
                PrintWriter writer = new PrintWriter(data);
                message.write(writer);
                diagnostic = new DiagnosticBuilder(data.toString(), Diagnostic.SEVERITY_ERROR).build();
            }
            diagnostics.add(diagnostic);
        }
        return diagnostics;
    }

    private void parseAllSymbols() {
        Map<String, Set<SymbolInformation>> newFileSymbols = Maps.newHashMap();
        unit.iterator().forEachRemaining(sourceUnit -> {
            Set<SymbolInformation> symbols = Sets.newHashSet();
            String sourcePath = sourceUnit.getSource().getURI().getPath();
            // This will iterate through all classes, interfaces and enums, including inner ones.
            sourceUnit.getAST().getClasses().forEach(clazz -> {
                // Add class symbol
                symbols.add(createSymbolInformation(clazz.getName(), getKind(clazz),
                        createLocationImpl(sourcePath, clazz), Optional.fromNullable(clazz.getOuterClass())));
                // Add all the class's field symbols
                clazz.getFields().forEach(field -> symbols.add(createSymbolInformation(field.getName(),
                        SymbolInformation.KIND_FIELD, createLocationImpl(sourcePath, field), Optional.of(clazz))));
                // Add all method symbols
                clazz.getAllDeclaredMethods().forEach(method -> symbols.add(createSymbolInformation(method.getName(),
                        SymbolInformation.KIND_METHOD, createLocationImpl(sourcePath, method), Optional.of(clazz))));
            });
            // TODO(#28) Add symbols declared within the statement block variable scope which includes script
            // defined variables.
            newFileSymbols.put(sourcePath, symbols);
        });
        fileSymbols = newFileSymbols;
    }

    private static int getKind(ClassNode node) {
        if (node.isInterface()) {
            return SymbolInformation.KIND_INTERFACE;
        } else if (node.isEnum()) {
            return SymbolInformation.KIND_ENUM;
        }
        return SymbolInformation.KIND_CLASS;
    }

    private static LocationImpl createLocationImpl(String uri, ASTNode node) {
        LocationImpl location = new LocationImpl();
        location.setRange(Ranges.createRange(node.getLineNumber(), node.getColumnNumber(),
                node.getLastLineNumber(), node.getLastColumnNumber()));
        location.setUri(uri);
        return location;
    }

    private static SymbolInformation createSymbolInformation(String name, int kind, LocationImpl location,
            Optional<ClassNode> container) {
        SymbolInformationImpl symbol = new SymbolInformationImpl();
        symbol.setContainer(container.transform(ClassNode::getName).orNull());
        symbol.setKind(kind);
        symbol.setLocation(location);
        symbol.setName(name);
        return symbol;
    }

}
