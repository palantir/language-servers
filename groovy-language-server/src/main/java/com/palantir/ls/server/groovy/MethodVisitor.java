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

import com.google.common.base.Optional;
import com.palantir.ls.groovy.util.GroovyConstants;
import com.palantir.ls.groovy.util.GroovyLocations;
import com.palantir.ls.util.Ranges;
import com.palantir.ls.util.UriSupplier;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.CatchStatement;

public final class MethodVisitor extends CodeVisitorSupport {

    private final Indexer indexer;
    private final URI uri;
    private final ClassNode clazz;
    private final Map<String, Location> classes;
    // Don't use this to get the field's types location, it is invalid when obtained through their references.
    private final Map<String, FieldNode> classFields;
    private final Optional<MethodNode> methodNode;
    private final UriSupplier workspaceUriSupplier;

    public MethodVisitor(Indexer indexer, URI uri, ClassNode clazz, Map<String, Location> classes,
            Map<String, FieldNode> classFields, Optional<MethodNode> methodNode, UriSupplier workspaceUriSupplier) {
        this.indexer = indexer;
        this.uri = uri;
        this.clazz = clazz;
        this.classes = classes;
        this.classFields = classFields;
        this.methodNode = methodNode;
        this.workspaceUriSupplier = workspaceUriSupplier;
    }

    @Override
    public void visitCatchStatement(CatchStatement statement) {
        // Add reference to class exception
        if (classes.containsKey(statement.getExceptionType().getName())) {
            indexer.addReference(classes.get(statement.getExceptionType().getName()),
                    createLocation(statement.getExceptionType()));
        }
        // TODO(#125): add a symbol for the exception variables. Right now statement.getVariable() returns a Parameter
        // with an invalid location.
        super.visitCatchStatement(statement);
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        List<MethodNode> possibleMethods = null;
        ClassNode potentialParentClass = clazz;
        if (call.getObjectExpression() instanceof ClassExpression) {
            ClassExpression expression = (ClassExpression) call.getObjectExpression();
            // This means it's an expression like this: SomeClass.someMethod
            potentialParentClass = expression.getType();
            possibleMethods = expression.getType().getMethods(call.getMethod().getText());
        } else if (call.getObjectExpression() instanceof ConstructorCallExpression) {
            ConstructorCallExpression expression = (ConstructorCallExpression) call.getObjectExpression();
            // Local function, no class used (or technically this used).
            potentialParentClass = expression.getType();
            // Local function, no class used (or technically this used).
            possibleMethods = potentialParentClass.getMethods(call.getMethod().getText());
        } else if (call.getObjectExpression() instanceof VariableExpression) {
            // function called on instance of some class
            VariableExpression var = (VariableExpression) call.getObjectExpression();
            if (var.getOriginType() != null) {
                potentialParentClass = var.getOriginType();
                if (potentialParentClass.getText().equals(GroovyConstants.JAVA_DEFAULT_OBJECT)) {
                    // This means it might actually be referring to a global field but not getting the right type
                    // because this is the default value and not necessarily set even if it has a real type.
                    if (classFields.containsKey(var.getText())) {
                        possibleMethods =
                                classFields.get(var.getText()).getType().getMethods(call.getMethod().getText());
                    }
                } else {
                    possibleMethods = var.getOriginType().getMethods(call.getMethod().getText());
                }
            }
        } else {
            // function called on instance of this class
            possibleMethods = clazz.getMethods(call.getMethod().getText());
        }

        final ClassNode parentClass = potentialParentClass;
        if (possibleMethods != null && !possibleMethods.isEmpty()
                && call.getArguments() instanceof ArgumentListExpression) {
            ArgumentListExpression actualArguments = (ArgumentListExpression) call.getArguments();
            possibleMethods.stream()
                    .filter(method -> areEquals(method.getParameters(), actualArguments))
                    .forEach(method -> {
                        indexer.addReference(
                                createLocation(URI.create(classes.get(parentClass.getName()).getUri()), method),
                                createLocation(call));
                    });
        }
        super.visitMethodCallExpression(call);
    }

    @Override
    public void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
        List<MethodNode> possibleMethods = expression.getOwnerType().getMethods(expression.getMethodAsString());

        if (!possibleMethods.isEmpty() && expression.getArguments() instanceof ArgumentListExpression) {
            ArgumentListExpression actualArguments = (ArgumentListExpression) expression.getArguments();
            possibleMethods.stream()
                    .filter(method -> areEquals(method.getParameters(), actualArguments))
                    .forEach(method -> indexer.addReference(createLocation(method), createLocation(expression)));
        }

        super.visitStaticMethodCallExpression(expression);
    }

    @Override
    public void visitConstructorCallExpression(ConstructorCallExpression expression) {
        if (expression.getType() != null && classes.containsKey(expression.getType().getName())) {
            indexer.addReference(classes.get(expression.getType().getName()), createLocation(expression));
        }
        super.visitConstructorCallExpression(expression);
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expression) {
        if (expression.getObjectExpression() instanceof VariableExpression) {
            // This means it's a non static reference to a class variable
            VariableExpression var = (VariableExpression) expression.getObjectExpression();
            FieldNode field = var.getType().getField(expression.getProperty().getText());
            indexer.addReference(createLocation(URI.create(classes.get(var.getType().getName()).getUri()), field),
                    createLocation(expression.getProperty()));
        } else if (expression.getObjectExpression() instanceof ClassExpression) {
            // This means it's a static reference to a class variable
            ClassExpression classExpression = (ClassExpression) expression.getObjectExpression();
            FieldNode field = classExpression.getType().getField(expression.getProperty().getText());
            indexer.addReference(
                    createLocation(URI.create(classes.get(classExpression.getType().getName()).getUri()), field),
                    createLocation(expression.getProperty()));
        }
        super.visitPropertyExpression(expression);
    }

    @Override
    public void visitClassExpression(ClassExpression expression) {
        // Add reference to class
        if (expression.getType() != null && classes.containsKey(expression.getType().getName())) {
            indexer.addReference(classes.get(expression.getType().getName()), createLocation(expression));
        }
        super.visitClassExpression(expression);
    }

    @Override
    public void visitVariableExpression(VariableExpression expression) {
        if (expression.getAccessedVariable() != null) {
            SymbolInformation symbol = getVariableSymbolInformation(expression.getAccessedVariable());
            if (Ranges.isValid(symbol.getLocation().getRange())) {
                indexer.addReference(symbol.getLocation(), createLocation(expression));

            } else if (classFields.containsKey(expression.getAccessedVariable().getName())) {
                Location location = createLocation(classFields.get(expression.getAccessedVariable().getName()));
                indexer.addReference(location, createLocation(expression));
            }
        }
        super.visitVariableExpression(expression);
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        if (expression.getLeftExpression() instanceof Variable) {
            Variable var = (Variable) expression.getLeftExpression();
            SymbolInformation symbol = getVariableSymbolInformation(var);
            indexer.addSymbol(uri, symbol);
            if (var.getType() != null && classes.containsKey(var.getType().getName())) {
                indexer.addReference(classes.get(var.getType().getName()), createLocation(var.getType()));
            }
        }
        super.visitDeclarationExpression(expression);
    }

    private Location createLocation(ASTNode node) {
        return GroovyLocations.createLocation(workspaceUriSupplier.get(uri), node);
    }

    private Location createLocation(URI locationUri, ASTNode node) {
        return GroovyLocations.createLocation(workspaceUriSupplier.get(locationUri), node);
    }

    private SymbolInformation getVariableSymbolInformation(Variable variable) {
        SymbolInformationBuilder builder =
                new SymbolInformationBuilder().name(variable.getName());
        if (methodNode.isPresent()) {
            builder.containerName(methodNode.get().getName());
        } else {
            builder.containerName(clazz.getName());
        }

        if (variable instanceof DynamicVariable) {
            builder.kind(SymbolKind.Field);
            builder.location(GroovyLocations.createLocation(workspaceUriSupplier.get(uri)));
        } else if (variable instanceof FieldNode) {
            builder.kind(SymbolKind.Field);
            builder.location(createLocation(uri, (FieldNode) variable));
        } else if (variable instanceof Parameter) {
            builder.kind(SymbolKind.Variable);
            builder.location(createLocation(uri, (Parameter) variable));
        } else if (variable instanceof PropertyNode) {
            builder.kind(SymbolKind.Field);
            builder.location(createLocation(uri, (PropertyNode) variable));
        } else if (variable instanceof VariableExpression) {
            builder.kind(SymbolKind.Variable);
            builder.location(createLocation(uri, (VariableExpression) variable));
        } else {
            throw new IllegalArgumentException(String.format("Unknown type of variable: %s", variable));
        }
        return builder.build();
    }

    private static boolean areEquals(Parameter[] parameters, ArgumentListExpression arguments) {
        if (parameters.length != arguments.getExpressions().size()) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            // If they aren't the same type return false
            if (!parameters[i].getType().equals(arguments.getExpression(i).getType())) {
                return false;
            }
        }
        return true;
    }

}
