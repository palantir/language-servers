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

package com.palantir.ls.server.groovy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.palantir.ls.server.api.TreeParser;
import com.palantir.ls.server.groovy.util.GroovyConstants;
import com.palantir.ls.server.groovy.util.GroovyLocations;
import com.palantir.ls.server.util.Ranges;
import com.palantir.ls.server.util.UriSupplier;
import com.palantir.ls.server.util.Uris;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.Position;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Groovy implementation of the TreeParser. Depends on a supplier of a Groovy CompilationUnit.
 */
public final class GroovyTreeParser implements TreeParser {

    private static final Logger logger = LoggerFactory.getLogger(GroovyTreeParser.class);

    private static final String GROOVY_DEFAULT_INTERFACE = "groovy.lang.GroovyObject";

    // Maps from source file path -> set of symbols in that file
    private Indexer indexer = new Indexer();

    private final Supplier<CompilationUnit> unitSupplier;
    private final Path workspaceRoot;
    private final UriSupplier workspaceUriSupplier;

    private GroovyTreeParser(Supplier<CompilationUnit> unitSupplier, Path workspaceRoot,
            UriSupplier workspaceUriSupplier) {
        this.unitSupplier = unitSupplier;
        this.workspaceRoot = workspaceRoot;
        this.workspaceUriSupplier = workspaceUriSupplier;
    }

    /**
     * Creates a new instance of GroovyTreeParser.
     *
     * @param unitSupplier
     *        the supplier of compilation unit to be parsed
     * @param workspaceRoot
     *        the directory to compile
     * @param workspaceUriSupplier
     *        the provider use to resolve uris
     * @return the newly created GroovyTreeParser
     */
    public static GroovyTreeParser of(Supplier<CompilationUnit> unitSupplier, Path workspaceRoot,
            UriSupplier workspaceUriSupplier) {
        checkNotNull(unitSupplier, "unitSupplier must not be null");
        checkNotNull(workspaceRoot, "workspaceRoot must not be null");
        checkNotNull(workspaceUriSupplier, "workspaceUriSupplier must not be null");
        checkArgument(workspaceRoot.toFile().isDirectory(), "workspaceRoot must be a directory");

        return new GroovyTreeParser(unitSupplier, workspaceRoot, workspaceUriSupplier);
    }

    @Override
    public void parseAllSymbols() {
        Indexer newIndexer = new Indexer();
        CompilationUnit unit = unitSupplier.get();

        Map<String, Location> classes = Maps.newHashMap();
        unit.iterator().forEachRemaining(sourceUnit -> {
            sourceUnit.getAST().getClasses().forEach(clazz -> {
                classes.put(clazz.getName(), GroovyLocations.createClassDefinitionLocation(
                        workspaceUriSupplier.get(sourceUnit.getSource().getURI()), clazz));
            });
        });

        unit.iterator().forEachRemaining(sourceUnit -> {
            URI sourceUri = workspaceUriSupplier.get(sourceUnit.getSource().getURI());
            // This will iterate through all classes, interfaces and enums, including inner ones.
            sourceUnit.getAST().getClasses().forEach(clazz -> {
                if (!clazz.isScript()) {
                    // Add class symbol
                    SymbolInformation classSymbol =
                            createSymbolInformation(clazz.getName(), getKind(clazz),
                                    GroovyLocations.createClassDefinitionLocation(sourceUri, clazz),
                                    Optional.fromNullable(clazz.getOuterClass()).transform(ClassNode::getName));
                    newIndexer.addSymbol(sourceUri, classSymbol);

                    // Add implemented interfaces reference
                    Stream.of(clazz.getInterfaces())
                            .filter(node -> !node.getName().equals(GROOVY_DEFAULT_INTERFACE)
                                    && classes.containsKey(node.getName()))
                            .forEach(node -> newIndexer.addReference(classes.get(node.getName()),
                                    GroovyLocations.createLocation(sourceUri, node)));
                    // Add extended class reference
                    if (clazz.getSuperClass() != null
                            && !clazz.getSuperClass().getName().equals(GroovyConstants.JAVA_DEFAULT_OBJECT)
                            && classes.containsKey(clazz.getSuperClass().getName())) {
                        newIndexer.addReference(classes.get(clazz.getSuperClass().getName()),
                                classSymbol.getLocation());
                    }
                }

                Map<String, FieldNode> classFields = Maps.newHashMap();
                // Add all the class's field symbols
                clazz.getFields().forEach(field -> {
                    SymbolInformation symbol = getVariableSymbolInformation(clazz.getName(), sourceUri, field);
                    newIndexer.addSymbol(sourceUri, symbol);
                    if (classes.containsKey(field.getType().getName())) {
                        newIndexer.addReference(classes.get(field.getType().getName()),
                                GroovyLocations.createLocation(sourceUri, field.getType()));
                        classFields.put(field.getName(), field);
                    }
                });

                // Add all method symbols
                clazz.getAllDeclaredMethods()
                        .forEach(method -> {
                            parseMethod(newIndexer, sourceUri, clazz, classes, classFields, method);
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
                            newIndexer.addSymbol(sourceUnit.getSource().getURI(), symbol);
                            if (classes.containsKey(variable.getType().getName())) {
                                newIndexer.addReference(classes.get(variable.getType().getName()),
                                        GroovyLocations.createLocation(sourceUri, variable.getType()));
                            }
                        });
                sourceUnit.getAST().getStatementBlock()
                        .visit(new MethodVisitor(newIndexer, sourceUri, sourceUnit.getAST().getScriptClassDummy(),
                                classes, Maps.newHashMap(), Optional.absent(), workspaceUriSupplier));
            }
        });
        // Set the new indexer
        indexer = newIndexer;
    }

    @Override
    public Map<URI, Set<SymbolInformation>> getFileSymbols() {
        return indexer.getFileSymbols();
    }

    @Override
    public Map<Location, Set<Location>> getReferences() {
        return indexer.getReferences();
    }

    @Override
    public Set<Location> findReferences(ReferenceParams params) {
        URI paramsUri = Uris.resolveToRoot(workspaceRoot, params.getTextDocument().getUri());
        Set<SymbolInformation> symbols = indexer.getFileSymbols().get(paramsUri);
        if (symbols == null) {
            return Sets.newHashSet();
        }
        List<ReferenceLocation> foundReferencedLocations = symbols.stream()
                .map(symbol -> symbol.getLocation())
                .filter(l -> Ranges.isValid(l.getRange())
                        && Ranges.contains(l.getRange(), params.getPosition()))
                .map(l -> new ReferenceLocation(l, false))
                .collect(Collectors.toList());

        // It might be a location on top of a reference instead of a definition.
        // Ex: Test test; - clicking on Test
        Set<ReferenceLocation> locations =
                indexer.getGotoReferenced().keySet().stream()
                        .filter(l -> paramsUri.toString().equals(l.getUri())
                                && Ranges.isValid(l.getRange())
                                && Ranges.contains(l.getRange(), params.getPosition())
                                && indexer.gotoReferenced(l).isPresent())
                        .map(l -> new ReferenceLocation(l, true))
                        .collect(Collectors.toSet());
        foundReferencedLocations.addAll(locations);

        if (foundReferencedLocations.isEmpty()) {
            return Sets.newHashSet();
        }

        foundReferencedLocations = foundReferencedLocations.stream()
                // If there is more than one result, we want the symbol whose range starts the latest, with a secondary
                // sort of earliest end range.
                .sorted((l1, l2) -> Ranges.POSITION_COMPARATOR.compare(l1.getLocation().getRange().getEnd(),
                        l2.getLocation().getRange().getEnd()))
                .sorted((l1, l2) -> Ranges.POSITION_COMPARATOR.reversed()
                        .compare(l1.getLocation().getRange().getStart(), l2.getLocation().getRange().getStart()))
                .collect(Collectors.toList());

        ReferenceLocation foundReferencedLocation = foundReferencedLocations.get(0);
        Set<Location> foundReferences;
        Location referredLocation = foundReferencedLocation.getLocation();
        if (foundReferencedLocation.getIsReferencedLocation()) {
            referredLocation = indexer.gotoReferenced(foundReferencedLocation.getLocation()).get();
            foundReferences =  indexer.findReferences(referredLocation).or(Sets.newHashSet());
        } else {
            foundReferences = indexer.findReferences(foundReferencedLocation.getLocation()).or(Sets.newHashSet());
        }

        if (params.getContext().isIncludeDeclaration()) {
            foundReferences.add(referredLocation);
        } else {
            foundReferences.remove(referredLocation);
        }

        return foundReferences;
    }

    @Override
    public Optional<Location> gotoDefinition(URI uri, Position position) {
        List<Location> possibleLocations = indexer.getGotoReferenced().keySet().stream()
                .filter(l -> uri.equals(URI.create(l.getUri())) && Ranges.contains(l.getRange(), position))
                // If there is more than one result, we want the symbol whose range starts the latest, with a secondary
                // sort of earliest end range.
                .sorted((l1, l2) -> Ranges.POSITION_COMPARATOR.compare(l1.getRange().getEnd(), l2.getRange().getEnd()))
                .sorted((l1, l2) -> Ranges.POSITION_COMPARATOR.reversed().compare(l1.getRange().getStart(),
                        l2.getRange().getStart()))
                .collect(Collectors.toList());
        if (possibleLocations.isEmpty()) {
            return Optional.absent();
        }

        return indexer.gotoReferenced(possibleLocations.get(0));
    }

    @Override
    public Set<SymbolInformation> getFilteredSymbols(String query) {
        checkNotNull(query, "query must not be null");
        Pattern pattern = getQueryPattern(query);
        return indexer.getFileSymbols().values().stream().flatMap(Collection::stream)
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

    // sourceUri should already have been converted to a workspace URI
    private SymbolInformation getVariableSymbolInformation(String parentName, URI sourceUri, Variable variable) {
        SymbolInformationBuilder builder =
                new SymbolInformationBuilder().name(variable.getName()).containerName(parentName);
        if (variable instanceof DynamicVariable) {
            builder.kind(SymbolKind.Field);
            builder.location(GroovyLocations.createLocation(sourceUri));
        } else if (variable instanceof FieldNode) {
            builder.kind(SymbolKind.Field);
            builder.location(GroovyLocations.createLocation(sourceUri, (FieldNode) variable));
        } else if (variable instanceof Parameter) {
            builder.kind(SymbolKind.Variable);
            builder.location(GroovyLocations.createLocation(sourceUri, (Parameter) variable));
        } else if (variable instanceof PropertyNode) {
            builder.kind(SymbolKind.Field);
            builder.location(GroovyLocations.createLocation(sourceUri, (PropertyNode) variable));
        } else if (variable instanceof VariableExpression) {
            builder.kind(SymbolKind.Variable);
            builder.location(GroovyLocations.createLocation(sourceUri, (VariableExpression) variable));
        } else {
            throw new IllegalArgumentException(String.format("Unknown type of variable: %s", variable));
        }
        return builder.build();
    }

    // sourceUri should already have been converted to a workspace URI
    private void parseMethod(Indexer newIndexer, URI sourceUri, ClassNode parent, Map<String, Location> classes,
            Map<String, FieldNode> classFields, MethodNode method) {
        SymbolInformation methodSymbol =
                createSymbolInformation(method.getName(), SymbolKind.Method,
                        GroovyLocations.createLocation(sourceUri, method), Optional.of(parent.getName()));
        newIndexer.addSymbol(sourceUri, methodSymbol);

        // Method parameters
        method.getVariableScope().getDeclaredVariables().values().forEach(variable -> {
            SymbolInformation variableSymbol = getVariableSymbolInformation(method.getName(), sourceUri, variable);
            newIndexer.addSymbol(sourceUri, variableSymbol);
            if (classes.containsKey(variable.getType().getName())) {
                newIndexer.addReference(classes.get(variable.getType().getName()),
                        GroovyLocations.createLocation(sourceUri, variable.getType()));
            }
        });

        // Return type
        if (classes.containsKey(method.getReturnType().getName())) {
            newIndexer.addReference(classes.get(method.getReturnType().getName()),
                    GroovyLocations.createLocation(sourceUri, method.getReturnType()));
        }

        // We only want to visit the method if its not generated
        if (Ranges.isValid(methodSymbol.getLocation().getRange())) {
            // Visit the method
            if (method.getCode() instanceof BlockStatement) {
                BlockStatement blockStatement = (BlockStatement) method.getCode();
                blockStatement.visit(new MethodVisitor(newIndexer, sourceUri, parent, classes, classFields,
                        Optional.of(method), workspaceUriSupplier));
            }
        }
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

    private static class ReferenceLocation {

        private final Location location;
        private final boolean isReferencedLocation;

        ReferenceLocation(Location location, boolean isReferencedLocation) {
            this.location = location;
            this.isReferencedLocation = isReferencedLocation;
        }

        Location getLocation() {
            return location;
        }

        boolean getIsReferencedLocation() {
            return isReferencedLocation;
        }

    }

}
