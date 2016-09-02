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

package com.palantir.ls.groovy.util;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.TextDocumentContentChangeEvent;
import io.typefox.lsapi.builders.TextDocumentContentChangeEventBuilder;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public final class SourceWriterTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public TemporaryFolder sourceFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder destinationFolder = new TemporaryFolder();

    @Test
    public void testInitialize_noNewLine() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "my file contents");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter.of(source, destination);
        assertEquals("my file contents", FileUtils.readFileToString(source.toFile()));
    }

    @Test
    public void testInitialize_withNewline() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "my file contents\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter.of(source, destination);
        assertEquals("my file contents\n", FileUtils.readFileToString(source.toFile()));
        assertEquals("my file contents\n", FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_noChanges() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        writer.applyChanges(changes);
        assertEquals("first line\nsecond line\n", FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_nullRangeChange() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .text("foo")
                .build());
        writer.applyChanges(changes);
        assertEquals("foo", FileUtils.readFileToString(destination.toFile()));
    }


    @Test
    public void testDidChanges_nullRangeWithMultipleChanges() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .text("foo")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(1, 0, 1, 0))
                .rangeLength(1)
                .text("notfoo")
                .build());
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(String.format("Cannot have many changes when a change contains a null range "
                + "which means it replaces the whole contents of the file: %s", changes.get(0).toString()));
        writer.applyChanges(changes);
    }

    @Test
    public void testDidChanges_insertionBeginningOfLine() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\necond line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(1, 0, 1, 0))
                .rangeLength(1)
                .text("s")
                .build());
        writer.applyChanges(changes);
        assertEquals("first line\nsecond line\n", FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_insertionEndOfLine() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(1, 20, 1, 20))
                .rangeLength(13)
                .text("small change\n")
                .build());
        writer.applyChanges(changes);
        // Two new lines expected, one from the original contents and one from the change
        assertEquals("first line\nsecond linesmall change\n\n", FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_oneLineRange() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 6, 0, 10))
                .rangeLength(12)
                .text("small change")
                .build());
        writer.applyChanges(changes);
        assertEquals("first small change\nsecond line\n", FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_multiLineRange() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\nthird line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 6, 1, 6))
                .rangeLength(12)
                .text("small change")
                .build());
        writer.applyChanges(changes);
        assertEquals("first small change line\nthird line\n", FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_multipleRangesWholeLines() throws IOException {
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\nthird line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 0, 0, 20))
                .rangeLength(16)
                .text("new line number 1")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(1, 0, 1, 20))
                .rangeLength(16)
                .text("new line number 2")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(2, 0, 2, 20))
                .rangeLength(16)
                .text("new line number 3")
                .build());
        writer.applyChanges(changes);
        assertEquals("new line number 1\nnew line number 2\nnew line number 3\n",
                FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_multipleRangesSpecific() throws IOException {
        // Tests replacing the whole lines
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\nthird line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 1, 0, 9))
                .rangeLength(16)
                .text("new line number 1")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(1, 1, 1, 10))
                .rangeLength(16)
                .text("new line number 2")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(2, 1, 2, 9))
                .rangeLength(16)
                .text("new line number 3")
                .build());
        writer.applyChanges(changes);

        assertEquals("fnew line number 1e\nsnew line number 2e\ntnew line number 3e\n",
                FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_beforeFile() throws IOException {
        // Should be appended to the start of the file
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\nthird line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 0, 0, 0))
                .rangeLength(7)
                .text("change\n")
                .build());
        writer.applyChanges(changes);
        assertEquals("change\nfirst line\nsecond line\nthird line\n",
                FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_afterFile() throws IOException {
        // Should be appended to the end of the file
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\nthird line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(30, 1, 30, 1))
                .rangeLength(6)
                .text("first ")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(31, 1, 31, 1))
                .rangeLength(6)
                .text("second")
                .build());
        writer.applyChanges(changes);
        assertEquals("first line\nsecond line\nthird line\nfirst second\n",
                FileUtils.readFileToString(destination.toFile()));
    }

    @Test
    public void testDidChanges_invalidRanges() throws IOException {
        // Should be appended to the end of the file
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\nthird line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(-1, 0, 0, 0))
                .rangeLength(3)
                .text("one")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 0, 0, 0))
                .rangeLength(3)
                .text("two")
                .build());
        expectedException.expect(IllegalArgumentException.class);
        expectedException
                .expectMessage(String.format("range1 is not valid: %s", Ranges.createRange(-1, 0, 0, 0).toString()));
        writer.applyChanges(changes);
    }

    @Test
    public void testDidChanges_intersectingRanges() throws IOException {
        // Should be appended to the end of the file
        Path source = addFileToFolder(sourceFolder.getRoot(), "myfile.txt", "first line\nsecond line\nthird line\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        Range range = Ranges.createRange(0, 0, 0, 1);
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(range)
                .rangeLength(3)
                .text("one")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(range)
                .rangeLength(3)
                .text("two")
                .build());
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
                String.format("Cannot apply changes with intersecting ranges in changes: %s", changes));
        writer.applyChanges(changes);
    }

    @Test
    public void testDidChanges_rangesStartAndEndOnSameLine() throws IOException {
        // Should be appended to the end of the file
        Path source =
                addFileToFolder(sourceFolder.getRoot(), "myfile.txt",
                        "0123456789\n0123456789\n0123456789\n0123456789\n0123456789\n");
        Path destination = destinationFolder.getRoot().toPath().resolve("myfile.txt");
        SourceWriter writer = SourceWriter.of(source, destination);
        List<TextDocumentContentChangeEvent> changes = Lists.newArrayList();
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 0, 0, 1))
                .rangeLength(1)
                .text("a")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 2, 0, 3))
                .rangeLength(1)
                .text("b")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 5, 0, 7))
                .rangeLength(1)
                .text("c")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(0, 8, 1, 2))
                .rangeLength(1)
                .text("d")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(1, 4, 2, 2))
                .rangeLength(1)
                .text("e")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(2, 4, 2, 6))
                .rangeLength(1)
                .text("f")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(2, 9, 2, 9))
                .rangeLength(1)
                .text("g")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(4, 2, 4, 3))
                .rangeLength(1)
                .text("h")
                .build());
        changes.add(new TextDocumentContentChangeEventBuilder()
                .range(Ranges.createRange(4, 3, 4, 4))
                .rangeLength(1)
                .text("i")
                .build());
        writer.applyChanges(changes);
        assertEquals("a1b34c7d23e23f678g9\n0123456789\n01hi456789\n",
                FileUtils.readFileToString(destination.toFile()));
    }

    private Path addFileToFolder(File parent, String filename, String contents) throws IOException {
        File file = Files.createFile(Paths.get(parent.getAbsolutePath(), filename)).toFile();
        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.toString());
        writer.print(contents);
        writer.close();
        return file.toPath();
    }

}
