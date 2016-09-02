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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.palantir.ls.server.groovy.util.DefaultDiagnosticBuilder;
import com.palantir.ls.server.util.Ranges;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.FileChangeType;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.builders.FileEventBuilder;
import io.typefox.lsapi.builders.PublishDiagnosticsParamsBuilder;
import io.typefox.lsapi.builders.TextDocumentContentChangeEventBuilder;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.control.SourceUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public final class GroovyWorkspaceCompilerTest {

    private static final Set<Diagnostic> NO_ERRORS = Sets.newHashSet();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public TemporaryFolder output = new TemporaryFolder();
    @Rule
    public TemporaryFolder changedOutput = new TemporaryFolder();

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    @Test
    public void testTargetDirectoryNotFolder() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("targetDirectory must be a directory");
        GroovyWorkspaceCompiler.of(output.newFile().toPath(), root.getRoot().toPath(),
                changedOutput.getRoot().toPath());
    }

    @Test
    public void testWorkspaceRootNotFolder() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("workspaceRoot must be a directory");
        GroovyWorkspaceCompiler.of(output.getRoot().toPath(), root.newFile().toPath(),
                changedOutput.getRoot().toPath());
    }

    @Test
    public void testChangedFilesRootNotFolder() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("changedFilesRoot must be a directory");
        GroovyWorkspaceCompiler.of(output.getRoot().toPath(), root.getRoot().toPath(),
                changedOutput.newFile().toPath());
    }

    private GroovyWorkspaceCompiler createGroovyWorkspaceCompiler() {
        return GroovyWorkspaceCompiler.of(output.getRoot().toPath(), root.getRoot().toPath(),
                changedOutput.getRoot().toPath());
    }

    @Test
    public void testEmptyWorkspace() throws InterruptedException, ExecutionException, IOException {
        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        assertEquals(NO_ERRORS, compiler.compile());
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

        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        assertEquals(NO_ERRORS, compiler.compile());
    }

    @Test
    public void testCompile_WithJavaExtension() throws InterruptedException, ExecutionException, IOException {
        addFileToFolder(root.getRoot(), "test1.groovy",
                "class Coordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(root.getRoot(), "ExceptionNew.java", "public class ExceptionNew {}");

        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        assertEquals(NO_ERRORS, compiler.compile());
    }

    @Test
    public void testCompile_WithJavaExtensionAndGroovySyntax()
            throws InterruptedException, ExecutionException, IOException {
        addFileToFolder(root.getRoot(), "ExceptionNewNotSameName.java", "public class ExceptionNew {}");
        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        assertEquals(NO_ERRORS, compiler.compile());
    }

    @Test
    public void testCompile_WithJavaExtensionError() throws InterruptedException, ExecutionException, IOException {
        File test = addFileToFolder(root.getRoot(), "Test.java", "public class Test {"
                + "Foo foo;"
                + "}");

        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        Set<PublishDiagnosticsParams> diagnostics = compiler.compile();

        assertEquals(Sets.newHashSet(new PublishDiagnosticsParamsBuilder()
                .uri(test.toPath().toUri().toString())
                .diagnostic(new DefaultDiagnosticBuilder(
                        "unable to resolve class Foo \n @ line 1, column 20.", DiagnosticSeverity.Error)
                                .range(Ranges.createRange(0, 19, 0, 26))
                                .source(test.getAbsolutePath())
                                .build())
                .build()),
                diagnostics);
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
        addFileToFolder(newFolder2, "Test.foo", "public class Test {}\n");

        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        assertEquals(NO_ERRORS, compiler.compile());
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

        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        Set<PublishDiagnosticsParams> diagnostics = compiler.compile();

        Set<PublishDiagnosticsParams> expectedDiagnostics = Sets.newHashSet(
                new PublishDiagnosticsParamsBuilder()
                        .uri(test1.toPath().toUri().toString())
                        .diagnostic(
                                new DefaultDiagnosticBuilder(
                                        "unable to resolve class ExceptionNew1 \n @ line 7, column 18.",
                                        DiagnosticSeverity.Error)
                                                .range(Ranges.createRange(6, 17, 6, 72))
                                                .source(test1.getAbsolutePath())
                                                .build())
                        .build(),
                new PublishDiagnosticsParamsBuilder()
                        .uri(test2.toPath().toUri().toString())
                        .diagnostic(
                                new DefaultDiagnosticBuilder(
                                        "unable to resolve class ExceptionNew222 \n @ line 7, column 18.",
                                        DiagnosticSeverity.Error)
                                                .range(Ranges.createRange(6, 17, 6, 74))
                                                .source(test2.getAbsolutePath())
                                                .build())
                        .build());
        assertEquals(expectedDiagnostics, diagnostics);
    }

    @Test
    public void testHandleFileChanged() throws IOException {
        File newFolder1 = root.newFolder();
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        File catChangedFile =
                changedOutput.getRoot().toPath().resolve(newFolder1.getName()).resolve(catFile.getName()).toFile();
        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        // Compile
        assertEquals(NO_ERRORS, compiler.compile());
        // Assert the original file is in the compilation unit
        assertSingleSourceFileUri(catFile, compiler.get().iterator());

        // First change
        compiler.handleFileChanged(catFile.toURI(), Lists.newArrayList(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 6, 0, 9)).rangeLength(3).text("Dog").build()));

        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert file contents
        assertEquals("class Cat {\n}\n", FileUtils.readFileToString(catFile));
        assertEquals("class Dog {\n}\n", FileUtils.readFileToString(catChangedFile));

        // Assert the changed file is in the compilation unit
        assertSingleSourceFileUri(catChangedFile, compiler.get().iterator());

        // Second change
        compiler.handleFileChanged(catFile.toURI(), Lists.newArrayList(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 6, 0, 9)).rangeLength(6).text("Turtle").build()));

        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert file contents
        assertEquals("class Cat {\n}\n", FileUtils.readFileToString(catFile));
        assertEquals("class Turtle {\n}\n", FileUtils.readFileToString(
                changedOutput.getRoot().toPath().resolve(newFolder1.getName()).resolve(catFile.getName()).toFile()));

        // Assert the changed file is in the compilation unit
        assertSingleSourceFileUri(catChangedFile, compiler.get().iterator());
    }

    @Test
    public void testHandleFileClosed() throws IOException {
        File newFolder1 = root.newFolder();
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        File catChangedFile =
                changedOutput.getRoot().toPath().resolve(newFolder1.getName()).resolve(catFile.getName()).toFile();
        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        // Compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the original file is in the compilation unit
        assertSingleSourceFileUri(catFile, compiler.get().iterator());


        // First change
        compiler.handleFileChanged(catFile.toURI(), Lists.newArrayList(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 6, 0, 9)).rangeLength(3).text("Dog").build()));
        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert file contents
        assertEquals("class Cat {\n}\n", FileUtils.readFileToString(catFile));
        assertEquals("class Dog {\n}\n", FileUtils.readFileToString(catChangedFile));

        // Assert the changed file is in the compilation unit
        assertSingleSourceFileUri(catChangedFile, compiler.get().iterator());

        // Call handleClose
        compiler.handleFileClosed(catFile.toURI());
        // Assert file contents
        assertEquals("class Cat {\n}\n", FileUtils.readFileToString(catFile));
        // The changed file should have been deleted
        assertFalse(changedOutput.getRoot().toPath().resolve(root.getRoot().toPath().relativize(catFile.toPath()))
                .toFile().exists());
        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the original file is in the compilation unit
        assertSingleSourceFileUri(catFile, compiler.get().iterator());
    }

    @Test
    public void testHandleFileSaved() throws IOException {
        File newFolder1 = root.newFolder();
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        File catChangedFile =
                changedOutput.getRoot().toPath().resolve(newFolder1.getName()).resolve(catFile.getName()).toFile();

        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        // Compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the original file is in the compilation unit
        assertSingleSourceFileUri(catFile, compiler.get().iterator());

        // First change
        compiler.handleFileChanged(catFile.toURI(), Lists.newArrayList(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 6, 0, 9)).rangeLength(3).text("Dog").build()));
        // Assert file contents
        assertEquals("class Cat {\n}\n", FileUtils.readFileToString(catFile));
        assertEquals("class Dog {\n}\n", FileUtils.readFileToString(catChangedFile));

        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the changed file is in the compilation unit
        assertSingleSourceFileUri(catChangedFile, compiler.get().iterator());

        // Call handleClose
        compiler.handleFileSaved(catFile.toURI());
        // Assert the new file contents
        assertEquals("class Dog {\n}\n", FileUtils.readFileToString(catFile));
        // The changed file should have been deleted
        assertFalse(changedOutput.getRoot().toPath().resolve(root.getRoot().toPath().relativize(catFile.toPath()))
                .toFile().exists());

        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert new symbols persist
        // Assert the original file is in the compilation unit
        assertSingleSourceFileUri(catFile, compiler.get().iterator());
    }

    @Test
    public void testHandleChangeWatchedFiles_changed() throws IOException {
        File newFolder1 = root.newFolder();
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        File catChangedFile =
                changedOutput.getRoot().toPath().resolve(newFolder1.getName()).resolve(catFile.getName()).toFile();

        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        // Compile
        assertEquals(NO_ERRORS, compiler.compile());
        // Assert the original file is in the compilation unit
        assertSingleSourceFileUri(catFile, compiler.get().iterator());

        // First change
        compiler.handleFileChanged(catFile.toURI(), Lists.newArrayList(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 6, 0, 9)).rangeLength(3).text("Dog").build()));
        // Assert file contents
        assertEquals("class Cat {\n}\n", FileUtils.readFileToString(catFile));
        assertEquals("class Dog {\n}\n", FileUtils.readFileToString(catChangedFile));

        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the changed file is in the compilation unit
        assertSingleSourceFileUri(catChangedFile, compiler.get().iterator());

        // Call handleChangeWatchedFile with a change saying this file has been changed outside this language server
        compiler.handleChangeWatchedFiles(Lists.newArrayList(
                new FileEventBuilder().uri(root.getRoot().toPath().relativize(catFile.toPath()).toString())
                        .type(FileChangeType.Changed).build()));
        // Assert file contents
        assertEquals("class Cat {\n}\n", FileUtils.readFileToString(catFile));
        // The changed file should have been deleted
        assertFalse(changedOutput.getRoot().toPath().resolve(root.getRoot().toPath().relativize(catFile.toPath()))
                .toFile().exists());

        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the original file is in the compilation unit
        assertSingleSourceFileUri(catFile, compiler.get().iterator());
    }

    @Test
    public void testHandleChangeWatchedFiles_deleted() throws IOException {
        File newFolder1 = root.newFolder();
        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");
        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        // Compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the original file is in the compilation unit
        assertSingleSourceFileUri(catFile, compiler.get().iterator());

        // Delete the file
        assertTrue(catFile.delete());

        // Call handleChangeWatchedFile with a change saying this file has been deleted outside this language server
        compiler.handleChangeWatchedFiles(Lists.newArrayList(
                new FileEventBuilder().uri(root.getRoot().toPath().relativize(catFile.toPath()).toString())
                        .type(FileChangeType.Changed).build()));

        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the compiler has no source units
        assertFalse(compiler.get().iterator().hasNext());
    }

    @Test
    public void testHandleChangeWatchedFiles_created() throws IOException {
        File newFolder1 = root.newFolder();
        GroovyWorkspaceCompiler compiler = createGroovyWorkspaceCompiler();
        // Compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the compiler has no source units
        assertFalse(compiler.get().iterator().hasNext());

        File catFile = addFileToFolder(newFolder1, "Cat.groovy",
                "class Cat {\n"
                        + "}\n");

        // Call handleChangeWatchedFile with a change saying this file has been changed outside this language server
        compiler.handleChangeWatchedFiles(Lists.newArrayList(
                new FileEventBuilder().uri(root.getRoot().toPath().relativize(catFile.toPath()).toString())
                        .type(FileChangeType.Created).build()));

        // Re-compile
        assertEquals(NO_ERRORS, compiler.compile());

        // Assert the compilation unit now contains cat file
        assertSingleSourceFileUri(catFile, compiler.get().iterator());
    }

    private void assertSingleSourceFileUri(File file, Iterator<SourceUnit> sourceUnits) {
        List<SourceUnit> sourceUnitUris = Lists.newArrayList();
        Iterators.addAll(sourceUnitUris, sourceUnits);
        assertEquals(Lists.newArrayList(file.toPath().toUri()), sourceUnitUris.stream()
                .map(sourceUnit -> sourceUnit.getSource().getURI()).collect(Collectors.toList()));
    }

    private static File addFileToFolder(File parent, String filename, String contents) throws IOException {
        File file = Files.createFile(Paths.get(parent.getAbsolutePath(), filename)).toFile();
        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.toString());
        writer.print(contents);
        writer.close();
        return file;
    }

}
