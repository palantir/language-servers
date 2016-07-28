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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.palantir.groovylanguageserver.util.DiagnosticBuilder;
import com.palantir.groovylanguageserver.util.PositionUtil;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticImpl;
import io.typefox.lsapi.SymbolInformation;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
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
        List<DiagnosticImpl> diagnostics = wrapper.compile();
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
        List<DiagnosticImpl> diagnostics = wrapper.compile();

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
                + "   double getAt(int idx) {\n"
                + "      def someString = \"Not in symbols\"\n"
                + "      println someString\n"
                + "      if (idx == 0) latitude\n"
                + "      else if (idx == 1) longitude\n"
                + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                + "   }\n"
                + "}\n");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        List<DiagnosticImpl> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
        // The symbols will contain a lot of inherited fields and methods, so we just check to make sure it contains our
        // custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "Coordinates", SymbolInformation.KIND_CLASS));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "getAt", SymbolInformation.KIND_METHOD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "latitude", SymbolInformation.KIND_FIELD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "longitude", SymbolInformation.KIND_FIELD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "name", SymbolInformation.KIND_FIELD));
    }

    @Test
    public void testComputeAllSymbols_interface() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        addFileToFolder(newFolder1, "ICoordinates.groovy",
                "interface ICoordinates {\n"
                + "   abstract double getAt(int idx);\n"
                + "}\n");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        List<DiagnosticImpl> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
        // The symbols will contain a lot of inherited and default fields and methods, so we just check to make sure it
        // contains our custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "ICoordinates", SymbolInformation.KIND_INTERFACE));
        assertTrue(mapHasSymbol(symbols, Optional.of("ICoordinates"), "getAt", SymbolInformation.KIND_METHOD));
    }

    @Test
    public void testComputeAllSymbols_enum() throws InterruptedException, ExecutionException, IOException {
        addFileToFolder(root.getRoot(), "Type.groovy",
                "enum Type {\n"
                + "   ONE, TWO, THREE\n"
                + "}\n");

        GroovycWrapper wrapper = GroovycWrapper.of(output.getRoot().toPath(), root.getRoot().toPath());
        List<DiagnosticImpl> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
        // The symbols will contain a lot of inherited and default fields and methods, so we just check to make sure it
        // contains our custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "Type", SymbolInformation.KIND_ENUM));
        assertTrue(mapHasSymbol(symbols, Optional.of("Type"), "ONE", SymbolInformation.KIND_FIELD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Type"), "TWO", SymbolInformation.KIND_FIELD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Type"), "THREE", SymbolInformation.KIND_FIELD));
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
        List<DiagnosticImpl> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
        // The symbols will contain a lot of inherited fields and methods, so we just check to make sure it contains our
        // custom fields and methods.
        assertTrue(mapHasSymbol(symbols, Optional.absent(), "Coordinates", SymbolInformation.KIND_CLASS));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "getAt", SymbolInformation.KIND_METHOD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "latitude", SymbolInformation.KIND_FIELD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "longitude", SymbolInformation.KIND_FIELD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "name", SymbolInformation.KIND_FIELD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "Coordinates$MyInnerClass",
                SymbolInformation.KIND_CLASS));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "Coordinates$MyInnerInterface",
                SymbolInformation.KIND_INTERFACE));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates"), "Coordinates$MyInnerEnum",
                SymbolInformation.KIND_ENUM));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates$MyInnerEnum"), "ONE", SymbolInformation.KIND_FIELD));
        assertTrue(mapHasSymbol(symbols, Optional.of("Coordinates$MyInnerEnum"), "TWO", SymbolInformation.KIND_FIELD));
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
        List<DiagnosticImpl> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());
        Map<String, Set<SymbolInformation>> symbols = wrapper.getFileSymbols();
        assertTrue(mapHasSymbol(symbols, Optional.of("test"), "myMethod", SymbolInformation.KIND_METHOD));
        // TODO(#28) add check for name variable once we support script declared variables
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
        List<DiagnosticImpl> diagnostics = wrapper.compile();
        assertEquals(0, diagnostics.size());

        Set<SymbolInformation> filteredSymbols = wrapper.getFilteredSymbols("Coordinates");
        assertEquals(1, filteredSymbols.size());
        SymbolInformation foundSymbol = Iterables.getOnlyElement(filteredSymbols);
        assertThat(foundSymbol.getName(), is("Coordinates"));
        assertThat(foundSymbol.getKind(), is(SymbolInformation.KIND_CLASS));

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
        assertThat(foundSymbol.getKind(), is(SymbolInformation.KIND_FIELD));

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
        List<DiagnosticImpl> diagnostics = wrapper.compile();

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
        List<DiagnosticImpl> diagnostics = wrapper.compile();

        assertEquals(2, diagnostics.size());
        Set<Diagnostic> actualDiagnostics = Sets.newHashSet(diagnostics);
        Set<Diagnostic> expectedDiagnostics = Sets.newHashSet();
        expectedDiagnostics.add(new DiagnosticBuilder("unable to resolve class ExceptionNew1 \n @ line 7, column 18.",
                Diagnostic.SEVERITY_ERROR).range(PositionUtil.createRange(7, 18, 7, 73)).source(test1.getAbsolutePath())
                        .build());
        expectedDiagnostics.add(new DiagnosticBuilder("unable to resolve class ExceptionNew222 \n @ line 7, column 18.",
                Diagnostic.SEVERITY_ERROR).range(PositionUtil.createRange(7, 18, 7, 75)).source(test2.getAbsolutePath())
                        .build());
        assertEquals(expectedDiagnostics, actualDiagnostics);
    }

    private boolean mapHasSymbol(Map<String, Set<SymbolInformation>> map, Optional<String> container, String fieldName,
            int kind) {
        return map.values().stream().flatMap(Collection::stream)
                .anyMatch(symbol -> symbol.getKind() == kind
                        && container.transform(c -> c.equals(symbol.getContainer())).or(true)
                        && symbol.getName().equals(fieldName));
    }

    private File addFileToFolder(File parent, String filename, String contents) throws IOException {
        File file = Files.createFile(Paths.get(parent.getAbsolutePath(), filename)).toFile();
        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.toString());
        writer.println(contents);
        writer.close();
        return file;
    }

}
