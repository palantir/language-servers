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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Sets;
import com.palantir.groovylanguageserver.util.DiagnosticBuilder;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.PositionImpl;
import io.typefox.lsapi.RangeImpl;
import io.typefox.lsapi.util.LsapiFactories;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public final class GroovycWrapperTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public TemporaryFolder root = new TemporaryFolder();

    @Test
    public void testWorkspaceRootNotFolder() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        new GroovycWrapper(root.newFile().toPath());
    }

    @Test
    public void testCompile() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File newFolder2 = root.newFolder();
        addFileToFolder(newFolder1, "test1.groovy", "class Coordinates {\n"
                + "double latitude\n"
                + "double longitude\n"
                + "double getAt(int idx) {\n"
                + "if (idx == 0) latitude\n"
                + "else if (idx == 1) longitude\n"
                + "else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")}}");
        addFileToFolder(newFolder2, "test2.groovy", "class Coordinates2 {\n"
                + "double latitude\n"
                + "double longitude\n"
                + "double getAt(int idx) {\n"
                + "if (idx == 0) latitude\n"
                + "else if (idx == 1) longitude\n"
                + "else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")}}");
        addFileToFolder(newFolder2, "test3.groovy", "class Coordinates3 {\n"
                + "double latitude\n"
                + "double longitude\n"
                + "double getAt(int idx) {\n"
                + "if (idx == 0) latitude\n"
                + "else if (idx == 1) longitude\n"
                + "else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")}}");
        addFileToRootFolder("test4.groovy", "class ExceptionNew {}");

        GroovycWrapper wrapper = new GroovycWrapper(root.getRoot().toPath());
        wrapper.compile();

        assertEquals(0, wrapper.getDiagnostics().size());
    }

    @Test
    public void testCompile_error() throws InterruptedException, ExecutionException, IOException {
        File newFolder1 = root.newFolder();
        File newFolder2 = root.newFolder();
        File test1 = addFileToFolder(newFolder1, "test1.groovy", "class Coordinates {\n"
                + "double latitude\n"
                + "double longitude\n"
                + "double getAt(int idx) {\n"
                + "if (idx == 0) latitude\n"
                + "else if (idx == 1) longitude\n"
                + "else throw new ExceptionNew1(\"Wrong coordinate index, use 0 or 1\")}}");
        File test2 = addFileToFolder(newFolder2, "test2.groovy", "class Coordinates2 {\n"
                + "double latitude\n"
                + "double longitude\n"
                + "double getAt(int idx) {\n"
                + "if (idx == 0) latitude\n"
                + "else if (idx == 1) longitude\n"
                + "else throw new ExceptionNew222(\"Wrong coordinate index, use 0 or 1\")}}");
        addFileToFolder(newFolder2, "test3.groovy", "class Coordinates3 {\n"
                + "double latitude\n"
                + "double longitude\n"
                + "double getAt(int idx) {\n"
                + "if (idx == 0) latitude\n"
                + "else if (idx == 1) longitude\n"
                + "else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")}}");
        addFileToRootFolder("test4.groovy", "class ExceptionNew {}");

        GroovycWrapper wrapper = new GroovycWrapper(root.getRoot().toPath());
        wrapper.compile();

        assertEquals(2, wrapper.getDiagnostics().size());
        Set<Diagnostic> actualDiagnostics = Sets.newHashSet();
        actualDiagnostics.add(wrapper.getDiagnostics().get(0));
        actualDiagnostics.add(wrapper.getDiagnostics().get(1));
        Set<Diagnostic> expectedDiagnostics = Sets.newHashSet();
        expectedDiagnostics.add(new DiagnosticBuilder("unable to resolve class ExceptionNew1 \n @ line 7, column 12.",
                Diagnostic.SEVERITY_ERROR).range(makeRange(7, 12, 7, 67)).source(test1.getAbsolutePath()).build());
        expectedDiagnostics.add(new DiagnosticBuilder("unable to resolve class ExceptionNew222 \n @ line 7, column 12.",
                Diagnostic.SEVERITY_ERROR).range(makeRange(7, 12, 7, 69)).source(test2.getAbsolutePath()).build());
        assertEquals(expectedDiagnostics, actualDiagnostics);
    }

    private File addFileToRootFolder(String filename, String contents) throws IOException {
        File file = root.newFile(filename);
        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.toString());
        writer.println(contents);
        writer.close();
        return file;
    }

    private File addFileToFolder(File parent, String filename, String contents) throws IOException {
        File file = Files.createFile(Paths.get(parent.getAbsolutePath(), filename)).toFile();
        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.toString());
        writer.println(contents);
        writer.close();
        return file;
    }

    private static RangeImpl makeRange(int startLine, int startColumn, int endLine, int endColumn) {
        PositionImpl start = LsapiFactories.newPosition(startLine, startColumn);
        PositionImpl end = LsapiFactories.newPosition(endLine, endColumn);
        return LsapiFactories.newRange(start, end);
    }
}
