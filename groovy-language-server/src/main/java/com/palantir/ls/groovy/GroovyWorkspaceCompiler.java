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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.palantir.ls.api.WorkspaceCompiler;
import com.palantir.ls.groovy.util.GroovyConstants;
import com.palantir.ls.util.FileBackedContentsManager;
import com.palantir.ls.util.Ranges;
import com.palantir.ls.util.Uris;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Groovy workspace compiler which supplies a Groovy CompilationUnit.
 */
public final class GroovyWorkspaceCompiler implements WorkspaceCompiler, Supplier<CompilationUnit> {

    private static final Logger logger = LoggerFactory.getLogger(GroovyWorkspaceCompiler.class);

    private final Path workspaceRoot;
    private final Path changedFilesRoot;
    private final CompilerConfiguration config;

    private CompilationUnit unit;

    // Map from origin source filename to its changed version source writer
    private Map<URI, FileBackedContentsManager> originalSourceToChangedSource = Maps.newHashMap();

    private GroovyWorkspaceCompiler(Path workspaceRoot, Path changedFilesRoot, CompilerConfiguration config) {
        this.workspaceRoot = workspaceRoot;
        this.changedFilesRoot = changedFilesRoot;
        this.config = config;
        this.unit = new CompilationUnit(config);
    }

    /**
     * Creates a new instance of GroovyWorkspaceCompiler.
     *
     * @param targetDirectory the directory in which to put generated files
     * @param workspaceRoot the directory to compile
     * @param changedFilesRoot the directory in which to temporarily store incrementally changed files
     * @return the newly created GroovyWorkspaceCompiler
     */
    @SuppressFBWarnings("PT_FINAL_TYPE_RETURN")
    public static GroovyWorkspaceCompiler of(Path targetDirectory, Path workspaceRoot, Path changedFilesRoot) {
        checkNotNull(targetDirectory, "targetDirectory must not be null");
        checkNotNull(workspaceRoot, "workspaceRoot must not be null");
        checkNotNull(changedFilesRoot, "changedFilesRoot must not be null");
        checkArgument(targetDirectory.toFile().isDirectory(), "targetDirectory must be a directory");
        checkArgument(workspaceRoot.toFile().isDirectory(), "workspaceRoot must be a directory");
        checkArgument(changedFilesRoot.toFile().isDirectory(), "changedFilesRoot must be a directory");

        CompilerConfiguration config = new CompilerConfiguration();
        config.setTargetDirectory(targetDirectory.toFile());
        GroovyWorkspaceCompiler workspaceCompiler =
                new GroovyWorkspaceCompiler(workspaceRoot, changedFilesRoot, config);
        workspaceCompiler.addAllSourcesToCompilationUnit();

        return workspaceCompiler;
    }

    @Override
    public CompilationUnit get() {
        return unit;
    }

    @Override
    public URI getWorkspaceRoot() {
        return workspaceRoot.toUri();
    }

    @Override
    public Set<PublishDiagnosticsParams> compile(Set<URI> files) {
        try {
            unit.compile();
        } catch (MultipleCompilationErrorsException e) {
            return parseErrors(e.getErrorCollector());
        }
        return ImmutableSet.of();
    }

    @Override
    public void handleFileOpened(URI file) {

    }

    @Override
    public void handleFileChanged(URI originalFile, List<TextDocumentContentChangeEvent> contentChanges) {
        try {
            FileBackedContentsManager fileBackedContentsManager;
            if (originalSourceToChangedSource.containsKey(originalFile)) {
                // New change on existing changed source
                fileBackedContentsManager = originalSourceToChangedSource.get(originalFile);
            } else {
                // New source to switch out
                Path newChangedFilePath = changedFilesRoot.resolve(workspaceRoot.relativize(Paths.get(originalFile)));
                fileBackedContentsManager = FileBackedContentsManager.of(Paths.get(originalFile), newChangedFilePath);
                originalSourceToChangedSource.put(originalFile, fileBackedContentsManager);
            }
            // Apply changes to source writer and reset compilation unit
            fileBackedContentsManager.applyChanges(contentChanges);
            resetCompilationUnit();
        } catch (IOException e) {
            logger.error("Error occurred while handling file changes", e);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void handleFileClosed(URI originalFile) {
        if (!originalSourceToChangedSource.containsKey(originalFile)) {
            return;
        }
        Path changedSource = originalSourceToChangedSource.get(originalFile).getDestination();
        originalSourceToChangedSource.remove(originalFile);
        // Deleted the changed file
        if (!changedSource.toFile().delete()) {
            logger.error("Unable to delete file '{}'", changedSource.toAbsolutePath());
        }
        resetCompilationUnit();
    }

    @Override
    public void handleFileSaved(URI originalFile) {
        if (originalSourceToChangedSource.containsKey(originalFile)) {
            originalSourceToChangedSource.get(originalFile).reload();
            resetCompilationUnit();
        }
    }

    @Override
    public void handleChangeWatchedFiles(List<? extends FileEvent> changes) {
        changes.forEach(change -> {
            URI uri = Uris.resolveToRoot(workspaceRoot, change.getUri());
            switch (change.getType()) {
                case Changed:
                case Deleted:
                    if (originalSourceToChangedSource.containsKey(uri)) {
                        Path changedSource = originalSourceToChangedSource.get(uri).getDestination();
                        // Deleted the changed file
                        if (!changedSource.toFile().delete()) {
                            logger.error("Unable to delete file '{}'", changedSource.toAbsolutePath());
                        }
                        originalSourceToChangedSource.remove(uri);
                    }
                    break;
                default:
                    // Nothing to do in other cases
                    break;
            }
        });
        resetCompilationUnit();
    }

    private void addAllSourcesToCompilationUnit() {
        // We don't include the files that have a corresponding FileBackedContentsManager
        // since that means they will be replaced.
        for (File file : Files.fileTreeTraverser().preOrderTraversal(workspaceRoot.toFile())) {
            String fileExtension = Files.getFileExtension(file.getAbsolutePath());
            if (!originalSourceToChangedSource.containsKey(file.toURI()) && file.isFile()
                    && GroovyConstants.GROOVY_ALLOWED_EXTENSIONS.contains(fileExtension)) {
                unit.addSource(file);
            }
        }
        // Add the replaced sources
        originalSourceToChangedSource.values()
                .forEach(fileBackedContentsManager ->
                        unit.addSource(fileBackedContentsManager.getDestination().toFile()));
    }

    private void resetCompilationUnit() {
        try {
            FileUtils.deleteDirectory(config.getTargetDirectory());
        } catch (IOException e) {
            logger.error("Could not delete directory '" + config.getTargetDirectory().toString() + "'", e);
            throw new RuntimeException("Could not reset compiled files after changes. "
                    + "Make sure you have permission to modify your target directory.");
        }
        if (!config.getTargetDirectory().mkdir()) {
            logger.error("Could not recreate target directory: '{}'",
                    config.getTargetDirectory().getAbsolutePath());
            throw new RuntimeException("Could not reset compiled files after changes. "
                    + "User may not have permission to modify target directory.");
        }
        unit = new CompilationUnit(config);
        addAllSourcesToCompilationUnit();
    }

    private Set<PublishDiagnosticsParams> parseErrors(ErrorCollector collector) {
        Map<URI, List<Diagnostic>> diagnosticsByFile = Maps.newHashMap();

        for (int i = 0; i < collector.getWarningCount(); i++) {
            WarningMessage message = collector.getWarning(i);
            String message1 = message.getMessage() == null
                    ? ""
                    : message.getMessage();
            Diagnostic diag = new Diagnostic(
                    Ranges.UNDEFINED_RANGE, message1, DiagnosticSeverity.Warning, GroovyConstants.GROOVY_COMPILER);
            diagnosticsByFile.computeIfAbsent(workspaceRoot.toUri(), (ignored) -> Lists.newArrayList()).add(diag);
        }
        for (int i = 0; i < collector.getErrorCount(); i++) {
            Message message = collector.getError(i);
            URI uri;
            Diagnostic diagnostic;
            if (message instanceof SyntaxErrorMessage) {
                SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
                SyntaxException cause = syntaxErrorMessage.getCause();

                Range range =
                        Ranges.createZeroBasedRange(cause.getStartLine(), cause.getStartColumn(), cause.getEndLine(),
                                cause.getEndColumn());
                uri = Paths.get(cause.getSourceLocator()).toUri();
                diagnostic = new Diagnostic(
                        range, cause.getMessage(), DiagnosticSeverity.Error, GroovyConstants.GROOVY_COMPILER);
            } else {
                StringWriter data = new StringWriter();
                PrintWriter writer = new PrintWriter(data);
                message.write(writer);
                uri = workspaceRoot.toUri();
                String message1 = data.toString();
                diagnostic = new Diagnostic(
                        Ranges.UNDEFINED_RANGE, message1, DiagnosticSeverity.Error, GroovyConstants.GROOVY_COMPILER);
            }
            diagnosticsByFile.computeIfAbsent(uri, (ignored) -> Lists.newArrayList()).add(diagnostic);
        }
        return diagnosticsByFile.entrySet()
                .stream()
                .map(entry -> new PublishDiagnosticsParams(entry.getKey().toString(), entry.getValue()))
                .collect(Collectors.toSet());
    }

}
