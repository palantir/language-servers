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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.palantir.ls.groovy.util.Ranges;
import com.palantir.ls.groovy.util.UriSupplier;
import com.palantir.ls.groovy.util.WorkspaceUriSupplier;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.builders.LocationBuilder;
import io.typefox.lsapi.builders.ReferenceParamsBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public final class GroovyTreeParserTest {

    private static final Set<SymbolInformation> NO_SYMBOLS = Sets.newHashSet();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public TemporaryFolder changedOutput = new TemporaryFolder();
    @Rule
    public TemporaryFolder root = new TemporaryFolder();
    @Rule
    public TemporaryFolder output = new TemporaryFolder();

    private UriSupplier uriSupplier;

    private GroovyTreeParser parser;

    @Before
    public void setup() {
        uriSupplier = new WorkspaceUriSupplier(root.getRoot().toPath(), changedOutput.getRoot().toPath());
        parser = GroovyTreeParser.of(() -> {
            GroovyWorkspaceCompiler compiler =
                    GroovyWorkspaceCompiler.of(output.getRoot().toPath(), root.getRoot().toPath(),
                            changedOutput.getRoot().toPath());
            compiler.compile();
            return compiler.get();
        }, root.getRoot().toPath(), uriSupplier);
    }

    @Test
    public void testWorkspaceRootNotFolder() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("workspaceRoot must be a directory");
        GroovyTreeParser.of(() -> null, root.newFile().toPath(), uriSupplier);
    }

    @Test
    public void testNotParsedYet() throws IOException {
        assertEquals(NO_SYMBOLS,
                parser.getFileSymbols().values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }

    @Test
    public void testComputeAllSymbols_class() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        addFileToFolder(newFolder1, "Coordinates.groovy",
                "class Coordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   def name = \"Natacha\"\n"
                        + "   double getAt(int idx1, int idx2) {\n"
                        + "      def someString = \"Also in symbols\"\n"
                        + "      println someString\n"
                        + "      if (idx1 == 0) latitude\n"
                        + "      else if (idx1 == 1) longitude\n"
                        + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                        + "   }\n"
                        + "}\n");
        parser.parseAllSymbols();

        Map<URI, Set<SymbolInformation>> symbols = parser.getFileSymbols();

        // Assert that the format of the URI doesn't change the result (i.e whether it starts with file:/ or file:///)
        assertEquals(parser.getFileSymbols().get(newFolder1.toURI()),
                parser.getFileSymbols().get(newFolder1.toPath().toUri()));

        // The symbols will contain a lot of inherited fields and methods, so we just check to make sure it contains our
        // custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "Coordinates", SymbolKind.Class));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "getAt", SymbolKind.Method));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "latitude", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "longitude", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "name", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("getAt"), "idx1", SymbolKind.Variable));
        assertTrue(mapHasSymbol(symbols, Optional.of("getAt"), "idx2", SymbolKind.Variable));
        assertTrue(mapHasSymbol(symbols, Optional.of("getAt"), "someString", SymbolKind.Variable));
    }

    @Test
    public void testComputeAllSymbols_interface() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        addFileToFolder(newFolder1, "ICoordinates.groovy",
                "interface ICoordinates {\n"
                        + "   abstract double getAt(int idx);\n"
                        + "}\n");
        parser.parseAllSymbols();

        Map<URI, Set<SymbolInformation>> symbols = parser.getFileSymbols();
        // The symbols will contain a lot of inherited and default fields and methods, so we just check to make sure it
        // contains our custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "ICoordinates", SymbolKind.Interface));
        assertTrue(mapHasSymbol(symbols, Optional.of("ICoordinates"), "getAt", SymbolKind.Method));
        assertTrue(mapHasSymbol(symbols, Optional.of("getAt"), "idx", SymbolKind.Variable));
    }

    @Test
    public void testComputeAllSymbols_enum() throws InterruptedException, ExecutionException, IOException {
        addFileToFolder(root.getRoot(), "Type.groovy",
                "enum Type {\n"
                        + "   ONE, TWO, THREE\n"
                        + "}\n");
        parser.parseAllSymbols();

        Map<URI, Set<SymbolInformation>> symbols = parser.getFileSymbols();
        // The symbols will contain a lot of inherited and default fields and methods, so we just check to make sure it
        // contains our custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "Type", SymbolKind.Enum));
        assertTrue(mapHasSymbol(symbols, Optional.of("Type"), "ONE", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Type"), "TWO", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Type"), "THREE", SymbolKind.Field));
    }

    @Test
    public void testComputeAllSymbols_innerClassInterfaceEnum()
            throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        addFileToFolder(newFolder1, "Coordinates.groovy",
                "class Coordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   def name = \"Natacha\"\n"
                        + "   double getAt(int idx) {\n"
                        + "      def someString = \"Also in symbols\"\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                        + "   }\n"
                        + "   class MyInnerClass {}\n"
                        + "   interface MyInnerInterface{}\n"
                        + "   enum MyInnerEnum{\n"
                        + "      ONE, TWO\n"
                        + "   }\n"
                        + "}\n");
        parser.parseAllSymbols();

        Map<URI, Set<SymbolInformation>> symbols = parser.getFileSymbols();
        // The symbols will contain a lot of inherited fields and methods, so we just check to make sure it contains our
        // custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "Coordinates", SymbolKind.Class));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "getAt", SymbolKind.Method));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "latitude", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "longitude", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "name", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "Coordinates$MyInnerClass", SymbolKind.Class));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "Coordinates$MyInnerInterface",
                SymbolKind.Interface));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "Coordinates$MyInnerEnum", SymbolKind.Enum));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates$MyInnerEnum"), "ONE", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates$MyInnerEnum"), "TWO", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("getAt"), "idx", SymbolKind.Variable));
        assertTrue(mapHasSymbol(symbols, Optional.of("getAt"), "someString", SymbolKind.Variable));
    }

    @Test
    public void testComputeAllSymbols_script()
            throws InterruptedException, ExecutionException, IOException {
        addFileToFolder(root.getRoot(), "test.groovy",
                "def name = \"Natacha\"\n"
                        + "def myMethod() {\n"
                        + "   def someString = \"Also in symbols\"\n"
                        + "   println \"Hello World\"\n"
                        + "}\n"
                        + "println name\n"
                        + "myMethod()\n");
        parser.parseAllSymbols();

        Map<URI, Set<SymbolInformation>> symbols = parser.getFileSymbols();
        assertTrue(mapHasSymbol(symbols, Optional.of("test"), "myMethod", SymbolKind.Method));
        assertTrue(mapHasSymbol(symbols, Optional.of("test"), "name", SymbolKind.Variable));
        assertTrue(mapHasSymbol(symbols, Optional.of("myMethod"), "someString", SymbolKind.Variable));
    }

    @Test
    public void testGetFilteredSymbols() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File coordinatesFiles = addFileToFolder(newFolder1, "Coordinates.groovy",
                "class Coordinates implements ICoordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double longitude2\n"
                        + "   private double CoordinatesVar\n"
                        + "   double getAt(int idx) {\n"
                        + "      def someString = \"Also in symbols\"\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                        + "   }\n"
                        + "}\n");
        File icoordinatesFiles = addFileToFolder(newFolder1, "ICoordinates.groovy",
                "interface ICoordinates {\n"
                        + "   abstract double getAt(int idx);\n"
                        + "}\n");
        parser.parseAllSymbols();

        Set<SymbolInformation> filteredSymbols = parser.getFilteredSymbols("Coordinates");
        assertEquals(Sets.newHashSet(new SymbolInformationBuilder()
                .name("Coordinates")
                .kind(SymbolKind.Class)
                .location(createLocation(coordinatesFiles.toPath(), Ranges.createRange(0, 0, 11, 1)))
                .build()), filteredSymbols);

        filteredSymbols = parser.getFilteredSymbols("Coordinates*");
        assertEquals(Sets.newHashSet(
                new SymbolInformationBuilder()
                        .name("Coordinates")
                        .kind(SymbolKind.Class)
                        .location(createLocation(coordinatesFiles.toPath(), Ranges.createRange(0, 0, 11, 1)))
                        .build(),
                new SymbolInformationBuilder()
                        .name("CoordinatesVar")
                        .kind(SymbolKind.Field)
                        .location(createLocation(coordinatesFiles.toPath(), Ranges.createRange(4, 3, 4, 32)))
                        .containerName("Coordinates")
                        .build()),
                filteredSymbols);

        filteredSymbols = parser.getFilteredSymbols("Coordinates?");
        assertEquals(NO_SYMBOLS, filteredSymbols);

        filteredSymbols = parser.getFilteredSymbols("*Coordinates*");
        assertEquals(Sets.newHashSet(
                new SymbolInformationBuilder()
                        .name("Coordinates")
                        .kind(SymbolKind.Class)
                        .location(createLocation(coordinatesFiles.toPath(), Ranges.createRange(0, 0, 11, 1)))
                        .build(),
                new SymbolInformationBuilder()
                        .name("CoordinatesVar")
                        .kind(SymbolKind.Field)
                        .location(createLocation(coordinatesFiles.toPath(), Ranges.createRange(4, 3, 4, 32)))
                        .containerName("Coordinates")
                        .build(),
                new SymbolInformationBuilder()
                        .name("ICoordinates")
                        .kind(SymbolKind.Interface)
                        .location(createLocation(icoordinatesFiles.toPath(), Ranges.createRange(0, 0, 2, 1)))
                        .build()),
                filteredSymbols);

        filteredSymbols = parser.getFilteredSymbols("Coordinates???");
        assertEquals(Sets.newHashSet(
                new SymbolInformationBuilder()
                        .name("CoordinatesVar")
                        .kind(SymbolKind.Field)
                        .location(createLocation(coordinatesFiles.toPath(), Ranges.createRange(4, 3, 4, 32)))
                        .containerName("Coordinates")
                        .build()),
                filteredSymbols);
        filteredSymbols = parser.getFilteredSymbols("Coordinates...");
        assertEquals(NO_SYMBOLS, filteredSymbols);
        filteredSymbols = parser.getFilteredSymbols("*Coordinates...*");
        assertEquals(NO_SYMBOLS, filteredSymbols);
        filteredSymbols = parser.getFilteredSymbols("*Coordinates.??*");
        assertEquals(NO_SYMBOLS, filteredSymbols);
    }

    @Test
    public void testReferences_innerClass() throws IOException {
        File newFolder1 = root.newFolder();
        // edge cases, intersecting ranges
        File file = addFileToFolder(newFolder1, "Dog.groovy",
                "class Dog {\n"
                        + "   Cat friend1;\n"
                        + "   Cat2 friend2;\n"
                        + "   Cat bark(Cat enemy) {\n"
                        + "      println \"Bark! \" + enemy.name\n"
                        + "      return friend1\n"
                        + "   }\n"
                        + "}\n"
                        + "class Cat {\n"
                        + "   public String name = \"Bobby\"\n"
                        + "}\n"
                        + "class Cat2 {\n"
                        + "   InnerCat2 myFriend;\n"
                        + "   class InnerCat2 {\n"
                        + "   }\n"
                        + "}\n");
        parser.parseAllSymbols();

        // Right before "Cat", therefore should not find any symbol
        assertEquals(NO_SYMBOLS, parser.findReferences(createReferenceParams(file.toURI(), 7, 0, false)));
        // Right after "Cat", therefore should not find any symbol
        assertEquals(NO_SYMBOLS, parser.findReferences(createReferenceParams(file.toURI(), 10, 2, false)));

        // InnerCat2 references - testing finding more specific symbols that are contained inside another symbol's
        // range.
        Set<SymbolInformation> innerCat2ExpectedResult = Sets.newHashSet(
                createSymbolInformation("myFriend", SymbolKind.Field,
                        createLocation(file.toPath(), Ranges.createRange(12, 3, 12, 21)),
                        Optional.of("Cat2")),
                createSymbolInformation("getMyFriend", SymbolKind.Method,
                        createLocation(file.toPath(), Ranges.UNDEFINED_RANGE), Optional.of("Cat2")),
                createSymbolInformation("value", SymbolKind.Variable,
                        createLocation(file.toPath(), Ranges.UNDEFINED_RANGE), Optional.of("setMyFriend")));
        assertEquals(innerCat2ExpectedResult, parser.getTypeReferences().get("Cat2$InnerCat2"));
        assertEquals(innerCat2ExpectedResult,
                parser.findReferences(createReferenceParams(file.toURI(), 13, 9, false)));
    }

    @Test
    public void testReferences_enumOneLine() throws IOException {
        File newFolder1 = root.newFolder();
        // edge case on one line
        File enumFile = addFileToFolder(newFolder1, "MyEnum.groovy",
                "enum MyEnum {ONE,TWO}\n");
        parser.parseAllSymbols();

        // Find one line enum correctly
        Set<SymbolInformation> myEnumExpectedResult = Sets.newHashSet(
                createSymbolInformation("ONE", SymbolKind.Field,
                        createLocation(enumFile.toPath(), Ranges.createRange(0, 13, 0, 16)),
                        Optional.of("MyEnum")),
                createSymbolInformation("TWO", SymbolKind.Field,
                        createLocation(enumFile.toPath(), Ranges.createRange(0, 17, 0, 20)),
                        Optional.of("MyEnum")),
                createSymbolInformation("MIN_VALUE", SymbolKind.Field,
                        createLocation(enumFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("MyEnum")),
                createSymbolInformation("MAX_VALUE", SymbolKind.Field,
                        createLocation(enumFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("MyEnum")),
                createSymbolInformation("$INIT", SymbolKind.Method,
                        createLocation(enumFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("MyEnum")),
                createSymbolInformation("previous", SymbolKind.Method,
                        createLocation(enumFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("MyEnum")),
                createSymbolInformation("next", SymbolKind.Method,
                        createLocation(enumFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("MyEnum")),
                createSymbolInformation("valueOf", SymbolKind.Method,
                        createLocation(enumFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("MyEnum")));
        assertEquals(myEnumExpectedResult, parser.getTypeReferences().get("MyEnum"));
        assertEquals(myEnumExpectedResult,
                parser.findReferences(createReferenceParams(enumFile.toURI(), 0, 6, false)));
    }

    @Test
    public void testReferences_innerClassOneLine() throws IOException {
        File newFolder1 = root.newFolder();
        // edge case on one line
        File innerClass = addFileToFolder(newFolder1, "AandB.groovy",
                "public class A {public static class B {}\n"
                        + "A a\n"
                        + "B b\n"
                        + "}\n");
        parser.parseAllSymbols();

        // Identify type A correctly
        Set<SymbolInformation> typeAExpectedResult = Sets.newHashSet(
                createSymbolInformation("a", SymbolKind.Field,
                        createLocation(innerClass.toPath(), Ranges.createRange(1, 0, 1, 3)),
                        Optional.of("A")),
                createSymbolInformation("getA", SymbolKind.Method,
                        createLocation(innerClass.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("A")),
                createSymbolInformation("value", SymbolKind.Variable,
                        createLocation(innerClass.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("setA")));
        assertEquals(typeAExpectedResult, parser.getTypeReferences().get("A"));
        assertEquals(typeAExpectedResult,
                parser.findReferences(createReferenceParams(innerClass.toURI(), 0, 6, false)));
        // Identify type B correctly
        Set<SymbolInformation> typeBExpectedResult = Sets.newHashSet(
                createSymbolInformation("b", SymbolKind.Field,
                        createLocation(innerClass.toPath(), Ranges.createRange(2, 0, 2, 3)),
                        Optional.of("A")),
                createSymbolInformation("getB", SymbolKind.Method,
                        createLocation(innerClass.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("A")),
                createSymbolInformation("value", SymbolKind.Variable,
                        createLocation(innerClass.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("setB")));
        assertEquals(typeBExpectedResult, parser.getTypeReferences().get("A$B"));
        assertEquals(typeBExpectedResult,
                parser.findReferences(createReferenceParams(innerClass.toURI(), 0, 17, false)));
    }

    @Test
    public void testReferences_classesAndInterfaces() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File extendedcoordinatesFile = addFileToFolder(newFolder1, "ExtendedCoordinates.groovy",
                "class ExtendedCoordinates extends Coordinates{\n"
                        + "   void somethingElse() {\n"
                        + "      println \"Hi again!\"\n"
                        + "   }\n"
                        + "}\n");
        File extendedCoordinates2File = addFileToFolder(newFolder1, "ExtendedCoordinates2.groovy",
                "class ExtendedCoordinates2 extends Coordinates{\n"
                        + "   void somethingElse() {\n"
                        + "      println \"Hi again!\"\n"
                        + "   }\n"
                        + "}\n");
        File coordinatesFile = addFileToFolder(newFolder1, "Coordinates.groovy",
                "class Coordinates extends AbstractCoordinates implements ICoordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double longitude2\n"
                        + "   private double CoordinatesVar\n"
                        + "   double getAt(int idx) {\n"
                        + "      def someString = \"Also in symbols\"\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                        + "   }\n"
                        + "   void superInterfaceMethod() {\n"
                        + "      Coordinates myCoordinate\n"
                        + "      println \"Hi!\"\n"
                        + "   }\n"
                        + "   void something() {\n"
                        + "      println \"Hi!\"\n"
                        + "   }\n"
                        + "}\n");
        File icoordinatesFile = addFileToFolder(newFolder1, "ICoordinates.groovy",
                "interface ICoordinates extends ICoordinatesSuper{\n"
                        + "   abstract double getAt(int idx);\n"
                        + "}\n");
        File icoordinatesSuperFile = addFileToFolder(newFolder1, "ICoordinatesSuper.groovy",
                "interface ICoordinatesSuper {\n"
                        + "   abstract void superInterfaceMethod()\n"
                        + "}\n");
        File abstractcoordinatesFile = addFileToFolder(newFolder1, "AbstractCoordinates.groovy",
                "abstract class AbstractCoordinates {\n"
                        + "   abstract void something();\n"
                        + "}\n");
        parser.parseAllSymbols();

        Map<String, Set<SymbolInformation>> references = parser.getTypeReferences();
        // ExtendedCoordinates should have no references
        assertNull(references.get("ExtendedCoordinates"));
        assertEquals(NO_SYMBOLS,
                parser.findReferences(createReferenceParams(extendedcoordinatesFile.toURI(), 0, 7, false)));
        // ExtendedCoordinates2 should have no references
        assertNull(references.get("ExtendedCoordinates2"));
        assertEquals(NO_SYMBOLS,
                parser.findReferences(createReferenceParams(extendedCoordinates2File.toURI(), 0, 7, false)));

        // Coordinates is only referenced in ExtendedCoordinates and ExtendedCoordinates2
        Set<SymbolInformation> coordinatesExpectedResult = Sets.newHashSet(
                createSymbolInformation("ExtendedCoordinates", SymbolKind.Class,
                        createLocation(extendedcoordinatesFile.toPath(), Ranges.createRange(0, 0, 4, 1)),
                        Optional.absent()),
                createSymbolInformation("ExtendedCoordinates2", SymbolKind.Class,
                        createLocation(extendedCoordinates2File.toPath(), Ranges.createRange(0, 0, 4, 1)),
                        Optional.absent()),
                createSymbolInformation("myCoordinate", SymbolKind.Variable,
                        createLocation(coordinatesFile.toPath(), Ranges.createRange(12, 18, 12, 30)),
                        Optional.of("superInterfaceMethod")),
                createSymbolInformation("myCoordinate", SymbolKind.Variable,
                        createLocation(extendedcoordinatesFile.toPath(), Ranges.createRange(12, 18, 12, 30)),
                        Optional.of("superInterfaceMethod")),
                createSymbolInformation("myCoordinate", SymbolKind.Variable,
                        createLocation(extendedCoordinates2File.toPath(), Ranges.createRange(12, 18, 12, 30)),
                        Optional.of("superInterfaceMethod")));
        assertEquals(coordinatesExpectedResult, references.get("Coordinates"));
        assertEquals(coordinatesExpectedResult,
                parser.findReferences(createReferenceParams(coordinatesFile.toURI(), 0, 9, false)));

        // ICoordinates is only referenced in Coordinates
        Set<SymbolInformation> icoordinatesExpectedResult = Sets.newHashSet(
                createSymbolInformation("Coordinates", SymbolKind.Class,
                        createLocation(coordinatesFile.toPath(), Ranges.createRange(0, 0, 18, 1)),
                        Optional.absent()));
        assertEquals(icoordinatesExpectedResult, references.get("ICoordinates"));
        assertEquals(icoordinatesExpectedResult,
                parser.findReferences(createReferenceParams(icoordinatesFile.toURI(), 0, 14, false)));

        // AbstractCoordinates is only referenced in Coordinates
        Set<SymbolInformation> abstractCoordinatesExpectedResult = Sets.newHashSet(
                createSymbolInformation("Coordinates", SymbolKind.Class,
                        createLocation(coordinatesFile.toPath(), Ranges.createRange(0, 0, 18, 1)),
                        Optional.absent()));
        assertEquals(abstractCoordinatesExpectedResult, references.get("AbstractCoordinates"));
        assertEquals(abstractCoordinatesExpectedResult,
                parser.findReferences(createReferenceParams(abstractcoordinatesFile.toURI(), 0, 19, false)));

        // ICoordinatesSuper is only referenced in ICoordinates
        Set<SymbolInformation> icoordinatesSuperExpectedResult = Sets.newHashSet(
                createSymbolInformation("ICoordinates", SymbolKind.Interface,
                        createLocation(icoordinatesFile.toPath(), Ranges.createRange(0, 0, 2, 1)),
                        Optional.absent()));
        assertEquals(icoordinatesSuperExpectedResult, references.get("ICoordinatesSuper"));
        assertEquals(icoordinatesSuperExpectedResult,
                parser.findReferences(createReferenceParams(icoordinatesSuperFile.toURI(), 0, 13, false)));
    }

    @Test
    public void testReferences_fields() throws IOException {
        File newFolder1 = root.newFolder();
        File dogFile = addFileToFolder(newFolder1, "Dog.groovy",
                "class Dog {\n"
                        + "   Cat friend1;\n"
                        + "   Cat friend2;\n"
                        + "   Cat bark(Cat enemy) {\n"
                        + "      Cat myCat\n"
                        + "      println \"Bark! \" + enemy.name\n"
                        + "      return friend1\n"
                        + "   }\n"
                        + "}\n");

        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "   public String name = \"Bobby\"\n"
                        + "}\n");
        parser.parseAllSymbols();

        // Dog should have no references
        assertNull(parser.getTypeReferences().get("Dog"));
        assertEquals(NO_SYMBOLS, parser.findReferences(createReferenceParams(dogFile.toURI(), 0, 7, false)));

        Set<SymbolInformation> expectedResult = Sets.newHashSet(
                createSymbolInformation("friend1", SymbolKind.Field,
                        createLocation(dogFile.toPath(), Ranges.createRange(1, 3, 1, 14)), Optional.of("Dog")),
                createSymbolInformation("friend2", SymbolKind.Field,
                        createLocation(dogFile.toPath(), Ranges.createRange(2, 3, 2, 14)), Optional.of("Dog")),
                createSymbolInformation("enemy", SymbolKind.Variable,
                        createLocation(dogFile.toPath(), Ranges.createRange(3, 12, 3, 21)),
                        Optional.of("bark")),
                createSymbolInformation("myCat", SymbolKind.Variable,
                        createLocation(dogFile.toPath(), Ranges.createRange(4, 10, 4, 15)),
                        Optional.of("bark")),
                // Bark method returns a Cat
                createSymbolInformation("bark", SymbolKind.Method,
                        createLocation(dogFile.toPath(), Ranges.createRange(3, 3, 7, 4)), Optional.of("Dog")),
                // Generated getters and setter
                // These two function take in a Cat value
                createSymbolInformation("value", SymbolKind.Variable,
                        createLocation(dogFile.toPath(), Ranges.UNDEFINED_RANGE), Optional.of("setFriend1")),
                createSymbolInformation("value", SymbolKind.Variable,
                        createLocation(dogFile.toPath(), Ranges.UNDEFINED_RANGE), Optional.of("setFriend2")),
                // Return values of these functions are of type Cat
                createSymbolInformation("getFriend1", SymbolKind.Method,
                        createLocation(dogFile.toPath(), Ranges.UNDEFINED_RANGE), Optional.of("Dog")),
                createSymbolInformation("getFriend2", SymbolKind.Method,
                        createLocation(dogFile.toPath(), Ranges.UNDEFINED_RANGE), Optional.of("Dog")));
        assertEquals(expectedResult, parser.getTypeReferences().get("Cat"));
        assertEquals(expectedResult,
                parser.findReferences(createReferenceParams(catFile.toURI(), 0, 7, false)));
    }

    @Test
    public void testReferences_script() throws IOException {
        File newFolder1 = root.newFolder();
        File scriptFile = addFileToFolder(newFolder1, "MyScript.groovy",
                "Cat friend1;\n"
                        + "bark(friend1)\n"
                        + "Cat bark(Cat enemy) {\n"
                        + "   Cat myCat\n"
                        + "   println \"Bark! \"\n"
                        + "   return enemy\n"
                        + "}\n"
                        + "\n");
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        parser.parseAllSymbols();

        Set<SymbolInformation> expectedResult = Sets.newHashSet(
                createSymbolInformation("friend1", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(0, 4, 0, 11)),
                        Optional.of("MyScript")),
                createSymbolInformation("friend1", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(0, 4, 0, 11)),
                        Optional.of("run")),
                createSymbolInformation("enemy", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(2, 9, 2, 18)),
                        Optional.of("bark")),
                createSymbolInformation("myCat", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(3, 7, 3, 12)),
                        Optional.of("bark")),
                // Bark method returns a Cat
                createSymbolInformation("bark", SymbolKind.Method,
                        createLocation(scriptFile.toPath(), Ranges.createRange(2, 0, 6, 1)),
                        Optional.of("MyScript")));
        assertEquals(expectedResult, parser.getTypeReferences().get("Cat"));
        assertEquals(expectedResult,
                parser.findReferences(createReferenceParams(catFile.toURI(), 0, 7, false)));
    }

    @Test
    public void testReferences_enum() throws IOException {
        File newFolder1 = root.newFolder();
        File scriptFile = addFileToFolder(newFolder1, "MyScript.groovy",
                "Animal friend = Animal.CAT;\n"
                        + "pet(friend1)\n"
                        + "Animal pet(Animal animal) {\n"
                        + "   Animal myAnimal\n"
                        + "   println \"Pet the \" + animal\n"
                        + "   return animal\n"
                        + "}\n"
                        + "\n");
        File animalFile = addFileToFolder(newFolder1, "Animal.groovy",
                "enum Animal {\n"
                        + "CAT, DOG, BUNNY\n"
                        + "}\n");
        parser.parseAllSymbols();

        Set<SymbolInformation> expectedResult = Sets.newHashSet(
                createSymbolInformation("CAT", SymbolKind.Field,
                        createLocation(animalFile.toPath(), Ranges.createRange(1, 0, 1, 3)),
                        Optional.of("Animal")),
                createSymbolInformation("DOG", SymbolKind.Field,
                        createLocation(animalFile.toPath(), Ranges.createRange(1, 5, 1, 8)),
                        Optional.of("Animal")),
                createSymbolInformation("BUNNY", SymbolKind.Field,
                        createLocation(animalFile.toPath(), Ranges.createRange(1, 10, 1, 15)),
                        Optional.of("Animal")),
                createSymbolInformation("friend", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(0, 7, 0, 13)),
                        Optional.of("MyScript")),
                createSymbolInformation("friend", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(0, 7, 0, 13)),
                        Optional.of("run")),
                createSymbolInformation("animal", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(2, 11, 2, 24)),
                        Optional.of("pet")),
                createSymbolInformation("myAnimal", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(3, 10, 3, 18)),
                        Optional.of("pet")),
                // pet method returns a Animal
                createSymbolInformation("pet", SymbolKind.Method,
                        createLocation(scriptFile.toPath(), Ranges.createRange(2, 0, 6, 1)),
                        Optional.of("MyScript")),
                // generated symbols
                createSymbolInformation("valueOf", SymbolKind.Method,
                        createLocation(animalFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("Animal")),
                createSymbolInformation("MAX_VALUE", SymbolKind.Field,
                        createLocation(animalFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("Animal")),
                createSymbolInformation("previous", SymbolKind.Method,
                        createLocation(animalFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("Animal")),
                createSymbolInformation("next", SymbolKind.Method,
                        createLocation(animalFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("Animal")),
                createSymbolInformation("$INIT", SymbolKind.Method,
                        createLocation(animalFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("Animal")),
                createSymbolInformation("MIN_VALUE", SymbolKind.Field,
                        createLocation(animalFile.toPath(), Ranges.UNDEFINED_RANGE),
                        Optional.of("Animal")));
        // We check the references, after filtering out the generated ones.
        assertEquals(expectedResult, parser.getTypeReferences().get("Animal"));
        assertEquals(expectedResult,
                parser.findReferences(createReferenceParams(animalFile.toURI(), 0, 5, false)));
    }

    @Test
    public void testFindReferences_includeDeclaration() throws IOException {
        File newFolder1 = root.newFolder();
        File scriptFile = addFileToFolder(newFolder1, "MyScript.groovy",
                "Cat friend1;\n"
                        + "bark(friend1)\n"
                        + "Cat bark(Cat enemy) {\n"
                        + "   Cat myCat\n"
                        + "   println \"Bark! \"\n"
                        + "   return enemy\n"
                        + "}\n"
                        + "\n");
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        parser.parseAllSymbols();

        Set<SymbolInformation> expectedResult = Sets.newHashSet(
                createSymbolInformation("friend1", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(0, 4, 0, 11)),
                        Optional.of("MyScript")),
                createSymbolInformation("friend1", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(0, 4, 0, 11)),
                        Optional.of("run")),
                createSymbolInformation("enemy", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(2, 9, 2, 18)),
                        Optional.of("bark")),
                createSymbolInformation("myCat", SymbolKind.Variable,
                        createLocation(scriptFile.toPath(), Ranges.createRange(3, 7, 3, 12)),
                        Optional.of("bark")),
                // Bark method returns a Cat
                createSymbolInformation("bark", SymbolKind.Method,
                        createLocation(scriptFile.toPath(), Ranges.createRange(2, 0, 6, 1)),
                        Optional.of("MyScript")),
                createSymbolInformation("Cat", SymbolKind.Class,
                        createLocation(catFile.toPath(), Ranges.createRange(0, 0, 1, 1)),
                        Optional.absent()));
        assertEquals(expectedResult,
                parser.findReferences(createReferenceParams(catFile.toURI(), 0, 7, true)));
    }

    private boolean mapHasSymbol(Map<URI, Set<SymbolInformation>> map, Optional<String> container, String fieldName,
            SymbolKind kind) {
        return map.values().stream().flatMap(Collection::stream)
                .anyMatch(symbol -> symbol.getKind() == kind
                        && container.transform(c -> c.equals(symbol.getContainerName())).or(true)
                        && symbol.getName().equals(fieldName));
    }

    private static File addFileToFolder(File parent, String filename, String contents) throws IOException {
        File file = Files.createFile(Paths.get(parent.getAbsolutePath(), filename)).toFile();
        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.toString());
        writer.print(contents);
        writer.close();
        return file;
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

    private static Location createLocation(Path path, Range range) {
        return new LocationBuilder().uri(path.toUri().toString()).range(range).build();
    }

    private static ReferenceParams createReferenceParams(URI uri, int line, int col, boolean includeDeclaration) {
        return new ReferenceParamsBuilder()
                .context(includeDeclaration)
                .textDocument(uri.toString())
                .position(line, col).build();
    }

}
