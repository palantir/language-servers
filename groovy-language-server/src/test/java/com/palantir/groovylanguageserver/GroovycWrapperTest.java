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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.palantir.groovylanguageserver.util.DefaultDiagnosticBuilder;
import com.palantir.groovylanguageserver.util.Ranges;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public final class GroovycWrapperTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public TemporaryFolder output = new TemporaryFolder();

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    @Test
    public void testTargetDirectoryNotFolder() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("targetDirectory must be a directory");
        GroovycWrapper.of(output.newFile().toPath(), root.getRoot().toPath());
    }

    @Test
    public void testWorkspaceRootNotFolder() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("workspaceRoot must be a directory");
        GroovycWrapper.of(output.getRoot().toPath(), root.newFile().toPath());
    }

    @Test
    public void testEmptyWorkspace() throws InterruptedException, ExecutionException, IOException {
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
        assertEquals(0, symbols.values().size());
    }

    @Test
    public void testCompile() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File newFolder2 = root.newFolder();
        addFileToFolder(newFolder1, "test1.groovy",
                "class Coordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(newFolder2, "test2.groovy",
                "class Coordinates2 {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(newFolder2, "test3.groovy",
                "class Coordinates3 {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(root.getRoot(), "test4.groovy", "class ExceptionNew {}");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();

        assertEquals(0, diagnostics.size());
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
                        + "      def someString = \"Not in symbols\"\n"
                        + "      println someString\n"
                        + "      if (idx1 == 0) latitude\n"
                        + "      else if (idx1 == 1) longitude\n"
                        + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                        + "   }\n"
                        + "}\n");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
        // The symbols will contain a lot of inherited fields and methods, so we just check to make sure it contains our
        // custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "Coordinates", SymbolKind.Class));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "getAt", SymbolKind.Method));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "latitude", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "longitude", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "name", SymbolKind.Field));
        assertTrue(mapHasSymbol(symbols, Optional.of("getAt"), "idx1", SymbolKind.Variable));
        assertTrue(mapHasSymbol(symbols, Optional.of("getAt"), "idx2", SymbolKind.Variable));
    }

    @Test
    public void testComputeAllSymbols_interface() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        addFileToFolder(newFolder1, "ICoordinates.groovy",
                "interface ICoordinates {\n"
                        + "   abstract double getAt(int idx);\n"
                        + "}\n");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
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

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
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
                        + "      def someString = \"Not in symbols\"\n"
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

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
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
    }

    @Test
    public void testComputeAllSymbols_script()
            throws InterruptedException, ExecutionException, IOException {
        addFileToFolder(root.getRoot(), "test.groovy",
                "def name = \"Natacha\"\n"
                        + "def myMethod() {\n"
                        + "   println \"Hello World\"\n"
                        + "}\n"
                        + "println name\n"
                        + "myMethod()\n");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
        assertTrue(mapHasSymbol(symbols, Optional.of("test"), "myMethod", SymbolKind.Method));
        assertTrue(mapHasSymbol(symbols, Optional.of("test"), "name", SymbolKind.Variable));
    }

    @Test
    public void testGetFilteredSymbols() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        addFileToFolder(newFolder1, "Coordinates.groovy",
                "class Coordinates implements ICoordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double longitude2\n"
                        + "   private double CoordinatesVar\n"
                        + "   double getAt(int idx) {\n"
                        + "      def someString = \"Not in symbols\"\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(newFolder1, "ICoordinates.groovy",
                "interface ICoordinates {\n"
                        + "   abstract double getAt(int idx);\n"
                        + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        Set<SymbolInformation> filteredSymbols = wrapper.getFilteredSymbols("Coordinates");
        assertEquals(1, filteredSymbols.size());
        SymbolInformation foundSymbol = Iterables.getOnlyElement(filteredSymbols);
        assertThat(foundSymbol.getName(), is("Coordinates"));
        assertThat(foundSymbol.getKind(), is(SymbolKind.Class));

        filteredSymbols = wrapper.getFilteredSymbols("Coordinates*");
        assertEquals(2, filteredSymbols.size());
        assertEquals(Sets.newHashSet("Coordinates", "CoordinatesVar"),
                filteredSymbols.stream().map(symbol -> symbol.getName()).collect(Collectors.toSet()));

        filteredSymbols = wrapper.getFilteredSymbols("Coordinates?");
        assertEquals(0, filteredSymbols.size());

        filteredSymbols = wrapper.getFilteredSymbols("*Coordinates*");
        assertEquals(3, filteredSymbols.size());
        assertEquals(Sets.newHashSet("Coordinates", "CoordinatesVar", "ICoordinates"),
                filteredSymbols.stream().map(symbol -> symbol.getName()).collect(Collectors.toSet()));

        filteredSymbols = wrapper.getFilteredSymbols("Coordinates???");
        assertEquals(1, filteredSymbols.size());
        foundSymbol = Iterables.getOnlyElement(filteredSymbols);
        assertThat(foundSymbol.getName(), is("CoordinatesVar"));
        assertThat(foundSymbol.getKind(), is(SymbolKind.Field));

        filteredSymbols = wrapper.getFilteredSymbols("Coordinates...");
        assertEquals(0, filteredSymbols.size());
        filteredSymbols = wrapper.getFilteredSymbols("*Coordinates...*");
        assertEquals(0, filteredSymbols.size());
        filteredSymbols = wrapper.getFilteredSymbols("*Coordinates.??*");
        assertEquals(0, filteredSymbols.size());
    }

    @Test
    public void testCompile_withExtraFiles() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File newFolder2 = root.newFolder();
        addFileToFolder(newFolder1, "coordinates.groovy",
                "class Coordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(newFolder2, "file.txt", "Something that is not groovy");
        addFileToFolder(newFolder2, "Test.java", "public class Test {}\n");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();

        assertEquals(0, diagnostics.size());
    }

    @Test
    public void testCompile_error() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File newFolder2 = root.newFolder();
        File test1 = addFileToFolder(newFolder1, "test1.groovy",
                "class Coordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew1(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        File test2 = addFileToFolder(newFolder2, "test2.groovy",
                "class Coordinates2 {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew222(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(newFolder2, "test3.groovy",
                "class Coordinates3 {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(root.getRoot(), "test4.groovy", "class ExceptionNew {}\n");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();

        assertEquals(2, diagnostics.size());
        Set<Diagnostic> actualDiagnostics = Sets.newHashSet(diagnostics);
        Set<Diagnostic> expectedDiagnostics = Sets.newHashSet();
        expectedDiagnostics
                .add(new DefaultDiagnosticBuilder(
                        "unable to resolve class ExceptionNew1 \n @ line 7, column 18.", DiagnosticSeverity.Error)
                                .range(Ranges.createRange(7, 18, 7, 73))
                                .source(test1.getAbsolutePath())
                                .build());
        expectedDiagnostics
                .add(new DefaultDiagnosticBuilder(
                        "unable to resolve class ExceptionNew222 \n @ line 7, column 18.", DiagnosticSeverity.Error)
                                .range(Ranges.createRange(7, 18, 7, 75))
                                .source(test2.getAbsolutePath())
                                .build());
        assertEquals(expectedDiagnostics, actualDiagnostics);
    }

    @Test
    public void testReferences_edgeCases() throws IOException {
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
        // edge case on one line
        File enumFile = addFileToFolder(newFolder1, "MyEnum.groovy",
                "enum MyEnum {ONE,TWO}\n");
        // edge case on one line
        File innerClass = addFileToFolder(newFolder1, "AandB.groovy",
                "public class A {public static class B {}\n"
                        + "A a\n"
                        + "B b\n"
                + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        // InnerCat2 references - testing finding more specific symbols that are contained inside another symbol's
        // range.
        assertEquals(Sets.newHashSet(
                createSymbolInformation("myFriend",
                        createLocation(file.getAbsolutePath(), Ranges.createRange(13, 4, 13, 22)), SymbolKind.Field,
                        Optional.of("Cat2")),
                createSymbolInformation("getMyFriend", createLocation(file.getAbsolutePath(), Ranges.UNDEFINED_RANGE),
                        SymbolKind.Method, Optional.of("Cat2")),
                createSymbolInformation("value", createLocation(file.getAbsolutePath(), Ranges.UNDEFINED_RANGE),
                        SymbolKind.Variable, Optional.of("setMyFriend"))),
                wrapper.getTypeReferences().get("Cat2$InnerCat2"));
        // Find one line enum correctly
        assertEquals(Sets.newHashSet(
                createSymbolInformation("ONE",
                        createLocation(enumFile.getAbsolutePath(), Ranges.createRange(1, 14, 1, 17)), SymbolKind.Field,
                        Optional.of("MyEnum")),
                createSymbolInformation("TWO",
                        createLocation(enumFile.getAbsolutePath(), Ranges.createRange(1, 18, 1, 21)), SymbolKind.Field,
                        Optional.of("MyEnum"))),
                wrapper.getTypeReferences().get("MyEnum").stream()
                        .filter(symbol -> Ranges.isValid(symbol.getLocation().getRange())).collect(Collectors.toSet()));
        // Identify type A correctly
        assertEquals(Sets.newHashSet(
                createSymbolInformation("a", innerClass.getAbsolutePath(),
                        SymbolKind.Field, 2, 1, 2, 4, Optional.of("A"))),
                wrapper.getTypeReferences().get("A").stream()
                        .filter(symbol -> Ranges.isValid(symbol.getLocation().getRange())).collect(Collectors.toSet()));
        // Identify type B correctly
        assertEquals(Sets.newHashSet(
                createSymbolInformation("b", innerClass.getAbsolutePath(),
                        SymbolKind.Field, 3, 1, 3, 4, Optional.of("A"))),
                wrapper.getTypeReferences().get("A$B").stream()
                        .filter(symbol -> Ranges.isValid(symbol.getLocation().getRange())).collect(Collectors.toSet()));
    }

    @Test
    public void testFindReferences_edgeCases() throws IOException {
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
        // edge case on one line
        File enumFile = addFileToFolder(newFolder1, "MyEnum.groovy",
                "enum MyEnum {ONE,TWO}\n");
        // edge case on one line
        File innerClass = addFileToFolder(newFolder1, "AandB.groovy",
                "public class A {public static class B {}\n"
                        + "A a\n"
                        + "B b\n"
                + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        // Right before "Cat", therefore should not find any symbol
        assertEquals(0, wrapper.findReferences(createReferenceParams(file.getAbsolutePath(), 8, 1, false)).size());
        // Right after "Cat", therefore should not find any symbol
        assertEquals(0, wrapper.findReferences(createReferenceParams(file.getAbsolutePath(), 11, 3, false)).size());

        // InnerCat2 references - testing finding more specific symbols that are contained inside another symbol's
        // range.
        assertEquals(Sets.newHashSet(
                createSymbolInformation("myFriend", file.getAbsolutePath(),
                        SymbolKind.Field, 13, 4, 13, 22, Optional.of("Cat2")),
                createSymbolInformation("getMyFriend", file.getAbsolutePath(),
                        SymbolKind.Method, -1, -1, -1, -1, Optional.of("Cat2")),
                createSymbolInformation("value", file.getAbsolutePath(),
                        SymbolKind.Variable, -1, -1, -1, -1, Optional.of("setMyFriend"))),
                wrapper.findReferences(createReferenceParams(file.getAbsolutePath(), 14, 10, false)));
        // Find one line enum correctly
        assertEquals(Sets.newHashSet(
                createSymbolInformation("ONE", enumFile.getAbsolutePath(),
                        SymbolKind.Field, 1, 14, 1, 17, Optional.of("MyEnum")),
                createSymbolInformation("TWO", enumFile.getAbsolutePath(),
                        SymbolKind.Field, 1, 18, 1, 21, Optional.of("MyEnum"))),
                wrapper.findReferences(createReferenceParams(enumFile.getAbsolutePath(), 1, 7, false)).stream()
                        .filter(symbol -> Ranges.isValid(symbol.getLocation().getRange())).collect(Collectors.toSet()));
        // Identify type A correctly
        assertEquals(Sets.newHashSet(
                createSymbolInformation("a", innerClass.getAbsolutePath(),
                        SymbolKind.Field, 2, 1, 2, 4, Optional.of("A"))),
                wrapper.findReferences(createReferenceParams(innerClass.getAbsolutePath(), 1, 7, false)).stream()
                .filter(symbol -> Ranges.isValid(symbol.getLocation().getRange())).collect(Collectors.toSet()));
        // Identify type B correctly
        assertEquals(Sets.newHashSet(
                createSymbolInformation("b", innerClass.getAbsolutePath(),
                        SymbolKind.Field, 3, 1, 3, 4, Optional.of("A"))),
                wrapper.findReferences(createReferenceParams(innerClass.getAbsolutePath(), 1, 18, false)).stream()
                .filter(symbol -> Ranges.isValid(symbol.getLocation().getRange())).collect(Collectors.toSet()));
    }

    @Test
    public void testReferences_classesAndInterfaces() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File extendedCoordinatesFile = addFileToFolder(newFolder1, "ExtendedCoordinates.groovy",
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
                                + "      def someString = \"Not in symbols\"\n"
                                + "      if (idx == 0) latitude\n"
                                + "      else if (idx == 1) longitude\n"
                                + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                                + "   }\n"
                                + "   void superInterfaceMethod() {\n"
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
        addFileToFolder(newFolder1, "ICoordinatesSuper.groovy",
                "interface ICoordinatesSuper {\n"
                        + "   abstract void superInterfaceMethod()\n"
                        + "}\n");
        addFileToFolder(newFolder1, "AbstractCoordinates.groovy",
                "abstract class AbstractCoordinates {\n"
                        + "   abstract void something();\n"
                        + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> references = wrapper.getTypeReferences();
        // ExtendedCoordinates should have no references
        assertNull(references.get("ExtendedCoordinates"));
        // ExtendedCoordinates2 should have no references
        assertNull(references.get("ExtendedCoordinates2"));
        // Coordinates is only referenced in ExtendedCoordinates
        assertEquals(Sets.newHashSet(
                createSymbolInformation("ExtendedCoordinates", extendedCoordinatesFile.getAbsolutePath(),
                        SymbolKind.Class, 1, 1, 5, 2, Optional.absent()),
                createSymbolInformation("ExtendedCoordinates2", extendedCoordinates2File.getAbsolutePath(),
                        SymbolKind.Class, 1, 1, 5, 2, Optional.absent())),
                references.get("Coordinates"));
        // ICoordinates is only referenced in Coordinates
        assertEquals(Sets.newHashSet(
                createSymbolInformation("Coordinates", coordinatesFile.getAbsolutePath(),
                        SymbolKind.Class, 1, 1, 18, 2, Optional.absent())),
                references.get("ICoordinates"));
        // AbstractCoordinates is only references in Coordinates
        assertEquals(Sets.newHashSet(
                createSymbolInformation("Coordinates", coordinatesFile.getAbsolutePath(),
                        SymbolKind.Class, 1, 1, 18, 2, Optional.absent())),
                references.get("AbstractCoordinates"));
        // ICoordinatesSuper is only references in ICoordinates
        assertEquals(Sets.newHashSet(
                createSymbolInformation("ICoordinates", icoordinatesFile.getAbsolutePath(),
                        SymbolKind.Interface, 1, 1, 3, 2, Optional.absent())),
                references.get("ICoordinatesSuper"));
    }


    @Test
    public void testFindReferences_classesAndInterfaces() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File extendedCoordinatesFile = addFileToFolder(newFolder1, "ExtendedCoordinates.groovy",
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
                                + "      def someString = \"Not in symbols\"\n"
                                + "      if (idx == 0) latitude\n"
                                + "      else if (idx == 1) longitude\n"
                                + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                                + "   }\n"
                                + "   void superInterfaceMethod() {\n"
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
        File abstractCoordinatesFile = addFileToFolder(newFolder1, "AbstractCoordinates.groovy",
                "abstract class AbstractCoordinates {\n"
                        + "   abstract void something();\n"
                                + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        // ExtendedCoordinates has no references
        assertEquals(0, wrapper
                .findReferences(createReferenceParams(extendedCoordinatesFile.getAbsolutePath(), 1, 8, false))
                .size());
        // ExtendedCoordinates2 has no references
        assertEquals(0, wrapper
                .findReferences(createReferenceParams(extendedCoordinates2File.getAbsolutePath(), 1, 8, false))
                .size());
        // Coordinates reference
        assertEquals(
                Sets.newHashSet(
                        createSymbolInformation("ExtendedCoordinates", extendedCoordinatesFile.getAbsolutePath(),
                                SymbolKind.Class, 1, 1, 5, 2, Optional.absent()),
                        createSymbolInformation("ExtendedCoordinates2", extendedCoordinates2File.getAbsolutePath(),
                                SymbolKind.Class, 1, 1, 5, 2, Optional.absent())),
                wrapper.findReferences(createReferenceParams(coordinatesFile.getAbsolutePath(), 1, 10, false)));
        // ICoordinates reference
        assertEquals(
                Sets.newHashSet(createSymbolInformation("Coordinates", coordinatesFile.getAbsolutePath(),
                        SymbolKind.Class, 1, 1, 18, 2, Optional.absent())),
                wrapper.findReferences(createReferenceParams(icoordinatesFile.getAbsolutePath(), 1, 15, false)));
        // AbstractCoordinates reference
        assertEquals(
                Sets.newHashSet(createSymbolInformation("Coordinates", coordinatesFile.getAbsolutePath(),
                        SymbolKind.Class, 1, 1, 18, 2, Optional.absent())),
                wrapper.findReferences(
                        createReferenceParams(abstractCoordinatesFile.getAbsolutePath(), 1, 20, false)));
        // ICoordinatesSuper reference
        assertEquals(
                Sets.newHashSet(createSymbolInformation("ICoordinates", icoordinatesFile.getAbsolutePath(),
                        SymbolKind.Interface, 1, 1, 3, 2, Optional.absent())),
                wrapper.findReferences(
                        createReferenceParams(icoordinatesSuperFile.getAbsolutePath(), 1, 14, false)));
    }

    @Test
    public void testReferences_fields() throws IOException {
        File newFolder1 = root.newFolder();
        File dogFile = addFileToFolder(newFolder1, "Dog.groovy",
                        "class Dog {\n"
                                + "   Cat friend1;\n"
                                + "   Cat friend2;\n"
                                + "   Cat bark(Cat enemy) {\n"
                                + "      println \"Bark! \" + enemy.name\n"
                                + "      return friend1\n"
                                + "   }\n"
                                + "}\n");

        addFileToFolder(newFolder1, "Cat.groovy",
                        "class Cat {\n"
                                + "   public String name = \"Bobby\"\n"
                                + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        Map<String, Set<SymbolInformation>> references = wrapper.getTypeReferences();
        // Dog should have no references
        assertNull(references.get("Dog"));
        assertEquals(Sets.newHashSet(
                createSymbolInformation("friend1", dogFile.getAbsolutePath(),
                        SymbolKind.Field, 2, 4, 2, 15, Optional.of("Dog")),
                createSymbolInformation("friend2", dogFile.getAbsolutePath(),
                        SymbolKind.Field, 3, 4, 3, 15, Optional.of("Dog")),
                createSymbolInformation("enemy", dogFile.getAbsolutePath(),
                        SymbolKind.Variable, 4, 13, 4, 22, Optional.of("bark")),
                // Bark method returns a Cat
                createSymbolInformation("bark", dogFile.getAbsolutePath(),
                        SymbolKind.Method, 4, 4, 7, 5, Optional.of("Dog")),
                // Generated getters and setter
                // These two function take in a Cat value
                createSymbolInformation("value", dogFile.getAbsolutePath(),
                        SymbolKind.Variable, -1, -1, -1, -1, Optional.of("setFriend1")),
                createSymbolInformation("value", dogFile.getAbsolutePath(),
                        SymbolKind.Variable, -1, -1, -1, -1, Optional.of("setFriend2")),
                // Return values of these functions are of type Cat
                createSymbolInformation("getFriend1", dogFile.getAbsolutePath(),
                        SymbolKind.Method, -1, -1, -1, -1, Optional.of("Dog")),
                createSymbolInformation("getFriend2", dogFile.getAbsolutePath(),
                        SymbolKind.Method, -1, -1, -1, -1, Optional.of("Dog"))),
                references.get("Cat"));
    }

    @Test
    public void testFindReferences_fields() throws IOException {
        File newFolder1 = root.newFolder();
        File dogFile = addFileToFolder(newFolder1, "Dog.groovy",
                        "class Dog {\n"
                                + "   Cat friend1;\n"
                                + "   Cat friend2;\n"
                                + "   Cat bark(Cat enemy) {\n"
                                + "      println \"Bark! \" + enemy.name\n"
                                + "      return friend1\n"
                                + "   }\n"
                                + "}\n");

        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                        "class Cat {\n"
                                + "   public String name = \"Bobby\"\n"
                                + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        // Find references on position on top of Cat
        Set<SymbolInformation> referenceLocations =
                wrapper.findReferences(createReferenceParams(catFile.getAbsolutePath(), 1, 8, false));

        assertEquals(Sets.newHashSet(Sets.newHashSet(
                createSymbolInformation("friend1", dogFile.getAbsolutePath(),
                        SymbolKind.Field, 2, 4, 2, 15, Optional.of("Dog")),
                createSymbolInformation("friend2", dogFile.getAbsolutePath(),
                        SymbolKind.Field, 3, 4, 3, 15, Optional.of("Dog")),
                createSymbolInformation("enemy", dogFile.getAbsolutePath(),
                        SymbolKind.Variable, 4, 13, 4, 22, Optional.of("bark")),
                // Bark method returns a Cat
                createSymbolInformation("bark", dogFile.getAbsolutePath(),
                        SymbolKind.Method, 4, 4, 7, 5, Optional.of("Dog")),
                // Generated getters and setter
                // These two function take in a Cat value
                createSymbolInformation("value", dogFile.getAbsolutePath(),
                        SymbolKind.Variable, -1, -1, -1, -1, Optional.of("setFriend1")),
                createSymbolInformation("value", dogFile.getAbsolutePath(),
                        SymbolKind.Variable, -1, -1, -1, -1, Optional.of("setFriend2")),
                // Return values of these functions are of type Cat
                createSymbolInformation("getFriend1", dogFile.getAbsolutePath(),
                        SymbolKind.Method, -1, -1, -1, -1, Optional.of("Dog")),
                createSymbolInformation("getFriend2", dogFile.getAbsolutePath(),
                        SymbolKind.Method, -1, -1, -1, -1, Optional.of("Dog")))), referenceLocations);

        // No references for Dog
        assertEquals(0,
                wrapper.findReferences(createReferenceParams(dogFile.getAbsolutePath(), 1, 8, false)).size());
    }

    @Test
    public void testReferences_script() throws IOException {
        File newFolder1 = root.newFolder();
        File scriptFile = addFileToFolder(newFolder1, "MyScript.groovy",
                        "Cat friend1;\n"
                                + "bark(friend1)\n"
                                + "Cat bark(Cat enemy) {\n"
                                + "   println \"Bark! \"\n"
                                + "   return enemy\n"
                                + "}\n"
                                + "\n");
        addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        Map<String, Set<SymbolInformation>> references = wrapper.getTypeReferences();
        assertEquals(Sets.newHashSet(
                createSymbolInformation("friend1", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 1, 5, 1, 12, Optional.of("MyScript")),
                createSymbolInformation("enemy", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 3, 10, 3, 19, Optional.of("bark")),
                // Bark method returns a Cat
                createSymbolInformation("bark", scriptFile.getAbsolutePath(),
                        SymbolKind.Method, 3, 1, 6, 2, Optional.of("MyScript"))),
                references.get("Cat"));
    }

    @Test
    public void testFindReferences_script() throws IOException {
        File newFolder1 = root.newFolder();
        File scriptFile = addFileToFolder(newFolder1, "MyScript.groovy",
                        "Cat friend1;\n"
                                + "bark(friend1)\n"
                                + "Cat bark(Cat enemy) {\n"
                                + "   println \"Bark! \"\n"
                                + "   return enemy\n"
                                + "}\n"
                                + "\n");
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        Set<SymbolInformation> referenceLocations =
                wrapper.findReferences(createReferenceParams(catFile.getAbsolutePath(), 1, 8, false));
        assertEquals(Sets.newHashSet(
                createSymbolInformation("friend1", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 1, 5, 1, 12, Optional.of("MyScript")),
                createSymbolInformation("enemy", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 3, 10, 3, 19, Optional.of("bark")),
                // Bark method returns a Cat
                createSymbolInformation("bark", scriptFile.getAbsolutePath(),
                        SymbolKind.Method, 3, 1, 6, 2, Optional.of("MyScript"))), referenceLocations);
    }

    @Test
    public void testReferences_enum() throws IOException {
        File newFolder1 = root.newFolder();
        File scriptFile = addFileToFolder(newFolder1, "MyScript.groovy",
                        "Animal friend = Animal.CAT;\n"
                                + "pet(friend1)\n"
                                + "Animal pet(Animal animal) {\n"
                                + "   println \"Pet the \" + animal\n"
                                + "   return animal\n"
                                + "}\n"
                                + "\n");
        File animalFile = addFileToFolder(newFolder1, "Animal.groovy",
                "enum Animal {\n"
                        + "CAT, DOG, BUNNY\n"
                        + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        Map<String, Set<SymbolInformation>> references = wrapper.getTypeReferences();
        // We check the references, after filtering out the generated ones.
        assertEquals(Sets.newHashSet(
                createSymbolInformation("CAT", animalFile.getAbsolutePath(),
                        SymbolKind.Field, 2, 1, 2, 4, Optional.of("Animal")),
                createSymbolInformation("DOG", animalFile.getAbsolutePath(),
                        SymbolKind.Field, 2, 6, 2, 9, Optional.of("Animal")),
                createSymbolInformation("BUNNY", animalFile.getAbsolutePath(),
                        SymbolKind.Field, 2, 11, 2, 16, Optional.of("Animal")),
                createSymbolInformation("friend", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 1, 8, 1, 14, Optional.of("MyScript")),
                createSymbolInformation("animal", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 3, 12, 3, 25, Optional.of("pet")),
                // pet method returns a Animal
                createSymbolInformation("pet", scriptFile.getAbsolutePath(),
                        SymbolKind.Method, 3, 1, 6, 2, Optional.of("MyScript"))),
                references.get("Animal").stream().filter(symbol -> Ranges.isValid(symbol.getLocation().getRange()))
                        .collect(Collectors.toSet()));
    }

    @Test
    public void testFindReferences_enum() throws IOException {
        File newFolder1 = root.newFolder();
        File scriptFile = addFileToFolder(newFolder1, "MyScript.groovy",
                        "Animal friend = Animal.CAT;\n"
                                + "pet(friend1)\n"
                                + "Animal pet(Animal animal) {\n"
                                + "   println \"Pet the \" + animal\n"
                                + "   return animal\n"
                                + "}\n"
                                + "\n");
        File animalFile = addFileToFolder(newFolder1, "Animal.groovy",
                "enum Animal {\n"
                        + "CAT, DOG, BUNNY\n"
                        + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        Set<SymbolInformation> referenceLocations =
                wrapper.findReferences(createReferenceParams(animalFile.getAbsolutePath(), 1, 6, false));
        // We check the references, after filtering out the generated ones.
        assertEquals(Sets.newHashSet(
                createSymbolInformation("CAT", animalFile.getAbsolutePath(),
                        SymbolKind.Field, 2, 1, 2, 4, Optional.of("Animal")),
                createSymbolInformation("DOG", animalFile.getAbsolutePath(),
                        SymbolKind.Field, 2, 6, 2, 9, Optional.of("Animal")),
                createSymbolInformation("BUNNY", animalFile.getAbsolutePath(),
                        SymbolKind.Field, 2, 11, 2, 16, Optional.of("Animal")),
                createSymbolInformation("friend", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 1, 8, 1, 14, Optional.of("MyScript")),
                createSymbolInformation("animal", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 3, 12, 3, 25, Optional.of("pet")),
                // pet method returns a Animal
                createSymbolInformation("pet", scriptFile.getAbsolutePath(),
                        SymbolKind.Method, 3, 1, 6, 2, Optional.of("MyScript"))),
                referenceLocations.stream().filter(symbol -> Ranges.isValid(symbol.getLocation().getRange()))
                        .collect(Collectors.toSet()));
    }

    @Test
    public void testFindReferences_includeDeclaration() throws IOException {
        File newFolder1 = root.newFolder();
        File scriptFile = addFileToFolder(newFolder1, "MyScript.groovy",
                        "Cat friend1;\n"
                                + "bark(friend1)\n"
                                + "Cat bark(Cat enemy) {\n"
                                + "   println \"Bark! \"\n"
                                + "   return enemy\n"
                                + "}\n"
                                + "\n");
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        Set<Diagnostic> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        Set<SymbolInformation> referenceLocations =
                wrapper.findReferences(createReferenceParams(catFile.getAbsolutePath(), 1, 8, true));
        assertEquals(Sets.newHashSet(
                createSymbolInformation("friend1", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 1, 5, 1, 12, Optional.of("MyScript")),
                createSymbolInformation("enemy", scriptFile.getAbsolutePath(),
                        SymbolKind.Variable, 3, 10, 3, 19, Optional.of("bark")),
                // Bark method returns a Cat
                createSymbolInformation("bark", scriptFile.getAbsolutePath(),
                        SymbolKind.Method, 3, 1, 6, 2, Optional.of("MyScript")),
                createSymbolInformation("Cat", catFile.getAbsolutePath(),
                        SymbolKind.Class, 1, 1, 2, 2, Optional.absent())), referenceLocations);
    }

    private boolean mapHasSymbol(Map<String, Set<SymbolInformation>> map, Optional<String> container, String fieldName,
            SymbolKind kind) {
        return map.values().stream().flatMap(Collection::stream)
                .anyMatch(symbol -> symbol.getKind() == kind
                        && container.transform(c -> c.equals(symbol.getContainerName())).or(true)
                        && symbol.getName().equals(fieldName));
    }

    private File addFileToFolder(File parent, String filename, String contents) throws IOException {
        File file = Files.createFile(Paths.get(parent.getAbsolutePath(), filename)).toFile();
        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.toString());
        writer.println(contents);
        writer.close();
        return file;
    }

    private static SymbolInformation createSymbolInformation(String name, Location location, SymbolKind kind,
            Optional<String> parentName) {
        return new SymbolInformationBuilder()
                .containerName(parentName.orNull())
                .kind(kind)
                .location(location)
                .name(name)
                .build();
    }

    private static Location createLocation(String uri, Range range) {
        return new LocationBuilder().uri(uri).range(range).build();
    }

    private static ReferenceParams createReferenceParams(String uri, int line, int col, boolean includeDeclaration) {
        // HACK, blocked on https://github.com/TypeFox/ls-api/issues/39
        return (ReferenceParams) new ReferenceParamsBuilder()
                .context(includeDeclaration)
                .textDocument(uri)
                .position(line, col)
                .uri(uri).build();
    }

}
