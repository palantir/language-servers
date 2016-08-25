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

package com.palantir.ls.groovy.compilation;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.palantir.ls.api.TreeParser;
import com.palantir.ls.groovy.CompilationUnitProvider;
import com.palantir.ls.util.Ranges;
import com.palantir.ls.util.UriSupplier;
import com.palantir.ls.util.Uris;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.builders.LocationBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GroovyTreeParser implements TreeParser {

    private static final Logger logger = LoggerFactory.getLogger(GroovyTreeParser.class);

    private static final String GROOVY_DEFAULT_INTERFACE = "groovy.lang.GroovyObject";
    private static final String JAVA_DEFAULT_OBJECT = "java.lang.Object";

    // Maps from source file path -> set of symbols in that file
    private Map<URI, Set<SymbolInformation>> fileSymbols = Maps.newHashMap();
    // Maps from type -> set of references of this type
    private Map<String, Set<SymbolInformation>> typeReferences = Maps.newHashMap();

    private final CompilationUnitProvider unitProvider;
    private final Path workspaceRoot;
    private final UriSupplier workspaceUriSupplier;


    private GroovyTreeParser(CompilationUnitProvider unitProvider, Path workspaceRoot,
            UriSupplier workspaceUriSupplier) {
        this.unitProvider = unitProvider;
        this.workspaceRoot = workspaceRoot;
        this.workspaceUriSupplier = workspaceUriSupplier;
    }

    /**
     * Creates a new instance of GroovyTreeParser.
     *
     * @param unitProvider the compilation unit provider in which to store our current unit
     * @param workspaceRoot the directory to compile
     * @param workspaceUriSupplier the provider use to resolve uris
     * @return the newly created GroovyTreeParser
     */
    public static GroovyTreeParser of(CompilationUnitProvider unitProvider, Path workspaceRoot,
            UriSupplier workspaceUriSupplier) {
        checkNotNull(unitProvider, "unitProvider must not be null");
        checkNotNull(workspaceRoot, "workspaceRoot must not be null");
        checkNotNull(workspaceUriSupplier, "workspaceUriSupplier must not be null");
        checkArgument(workspaceRoot.toFile().isDirectory(), "workspaceRoot must be a directory");

        return new GroovyTreeParser(unitProvider, workspaceRoot, workspaceUriSupplier);
    }

    @Override
    public void parseAllSymbols() {
        Map<URI, Set<SymbolInformation>> newFileSymbols = Maps.newHashMap();
        Map<String, Set<SymbolInformation>> newTypeReferences = Maps.newHashMap();

        unitProvider.get().iterator().forEachRemaining(sourceUnit -> {
            Set<SymbolInformation> symbols = Sets.newHashSet();
            URI sourceUri = workspaceUriSupplier.get(sourceUnit.getSource().getURI());
            // This will iterate through all classes, interfaces and enums, including inner ones.
            sourceUnit.getAST().getClasses().forEach(clazz -> {
                // Add class symbol
                SymbolInformation classSymbol =
                        createSymbolInformation(clazz.getName(), getKind(clazz), createLocation(sourceUri, clazz),
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
                    SymbolInformation symbol = getVariableSymbolInformation(clazz.getName(), sourceUri, field);
                    addToValueSet(newTypeReferences, field.getType().getName(), symbol);
                    symbols.add(symbol);
                });

                // Add all method symbols
                clazz.getAllDeclaredMethods()
                        .forEach(method -> {
                            symbols.addAll(getMethodSymbolInformations(newTypeReferences, sourceUri, clazz, method));
                        });
            });

            // Add symbols declared within the statement block variable scope which includes script
            // defined variables.
            ClassNode scriptClass = sourceUnit.getAST().getScriptClassDummy();
            if (scriptClass != null) {
                sourceUnit.getAST().getStatementBlock().getVariableScope().getDeclaredVariables().values().forEach(
                        variable -> {
                            SymbolInformation symbol =
                                    getVariableSymbolInformation(scriptClass.getName(), sourceUri, variable);
                            addToValueSet(newTypeReferences, variable.getType().getName(), symbol);
                            symbols.add(symbol);
                        });
            }
            newFileSymbols.put(workspaceUriSupplier.get(sourceUri), symbols);
        });
        // Set the new references and new symbols
        typeReferences = newTypeReferences;
        fileSymbols = newFileSymbols;
    }

    @Override
    public Map<URI, Set<SymbolInformation>> getFileSymbols() {
        return fileSymbols;
    }

    @Override
    public Map<String, Set<SymbolInformation>> getTypeReferences() {
        return typeReferences;
    }

    @Override
    public Set<SymbolInformation> findReferences(ReferenceParams params) {
        Set<SymbolInformation> symbols =
                fileSymbols.get(Uris.resolveToRoot(workspaceRoot, params.getTextDocument().getUri()));
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
        checkNotNull(query, "query must not be null");
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

    private static SymbolKind getKind(ClassNode node) {
        if (node.isInterface()) {
            return SymbolKind.Interface;
        } else if (node.isEnum()) {
            return SymbolKind.Enum;
        }
        return SymbolKind.Class;
    }

    private SymbolInformation getVariableSymbolInformation(String parentName, URI sourceUri, Variable variable) {
        SymbolInformationBuilder builder =
                new SymbolInformationBuilder().name(variable.getName()).containerName(parentName);
        if (variable instanceof DynamicVariable) {
            builder.kind(SymbolKind.Field);
            builder.location(createLocation(sourceUri));
        } else if (variable instanceof FieldNode) {
            builder.kind(SymbolKind.Field);
            builder.location(createLocation(sourceUri, (FieldNode) variable));
        } else if (variable instanceof Parameter) {
            builder.kind(SymbolKind.Variable);
            builder.location(createLocation(sourceUri, (Parameter) variable));
        } else if (variable instanceof PropertyNode) {
            builder.kind(SymbolKind.Field);
            builder.location(createLocation(sourceUri, (PropertyNode) variable));
        } else if (variable instanceof VariableExpression) {
            builder.kind(SymbolKind.Variable);
            builder.location(createLocation(sourceUri, (VariableExpression) variable));
        } else {
            throw new IllegalArgumentException(String.format("Unknown type of variable: %s", variable));
        }
        return builder.build();
    }

    private Set<SymbolInformation> getMethodSymbolInformations(Map<String, Set<SymbolInformation>> newTypeReferences,
            URI sourceUri, ClassNode parent, MethodNode method) {
        Set<SymbolInformation> symbols = Sets.newHashSet();

        SymbolInformation methodSymbol =
                createSymbolInformation(method.getName(), SymbolKind.Method, createLocation(sourceUri, method),
                        Optional.of(parent.getName()));
        symbols.add(methodSymbol);
        addToValueSet(newTypeReferences, method.getReturnType().getName(), methodSymbol);

        // Method parameters
        method.getVariableScope().getDeclaredVariables().values().forEach(variable -> {
            SymbolInformation variableSymbol = getVariableSymbolInformation(method.getName(), sourceUri, variable);
            addToValueSet(newTypeReferences, variable.getType().getName(), variableSymbol);
            symbols.add(variableSymbol);
        });

        // Locally defined variables
        if (method.getCode() instanceof BlockStatement) {
            BlockStatement blockStatement = (BlockStatement) method.getCode();
            blockStatement.getVariableScope().getDeclaredVariables().values().forEach(variable -> {
                SymbolInformation variableSymbol = getVariableSymbolInformation(method.getName(), sourceUri, variable);
                addToValueSet(newTypeReferences, variable.getType().getName(), variableSymbol);
                symbols.add(variableSymbol);
            });
        }
        return symbols;
    }

    private Location createLocation(URI uri) {
        return new LocationBuilder()
                .uri(workspaceUriSupplier.get(uri).toString())
                .range(Ranges.UNDEFINED_RANGE)
                .build();
    }

    private Location createLocation(URI uri, ASTNode node) {
        return new LocationBuilder()
                .uri(workspaceUriSupplier.get(uri).toString())
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
