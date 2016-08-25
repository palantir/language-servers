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

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.palantir.ls.groovy.CompilationUnitProvider;
import com.palantir.ls.groovy.api.WorkspaceCompiler;
import com.palantir.ls.groovy.util.DefaultDiagnosticBuilder;
import com.palantir.ls.groovy.util.GroovyConstants;
import com.palantir.ls.groovy.util.Ranges;
import com.palantir.ls.groovy.util.SourceWriter;
import com.palantir.ls.groovy.util.Uris;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.FileEvent;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.TextDocumentContentChangeEvent;
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
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GroovyWorkspaceCompiler implements WorkspaceCompiler {

    private static final Logger logger = LoggerFactory.getLogger(GroovyWorkspaceCompiler.class);

    private final CompilationUnitProvider unitProvider;
    private final Path workspaceRoot;
    private final Path changedFilesRoot;
    private final CompilerConfiguration config;

    // Map from origin source filename to its changed version source writer
    private Map<URI, SourceWriter> originalSourceToChangedSource = Maps.newHashMap();

    private GroovyWorkspaceCompiler(CompilationUnitProvider unitProvider, Path workspaceRoot, Path changedFilesRoot,
            CompilerConfiguration config) {
        this.unitProvider = unitProvider;
        this.workspaceRoot = workspaceRoot;
        this.changedFilesRoot = changedFilesRoot;
        this.config = config;
    }

    /**
     * Creates a new instance of GroovyWorkspaceCompiler.
     *
     * @param unitProvider the compilation unit provider in which to store our current unit
     * @param targetDirectory the directory in which to put generated files
     * @param workspaceRoot the directory to compile
     * @param changedFilesRoot the directory in which to temporarily store incrementally changed files
     * @return the newly created GroovyWorkspaceCompiler
     */
    public static GroovyWorkspaceCompiler of(CompilationUnitProvider unitProvider, Path targetDirectory,
            Path workspaceRoot, Path changedFilesRoot) {
        checkNotNull(unitProvider, "unitProvider must not be null");
        checkNotNull(targetDirectory, "targetDirectory must not be null");
        checkNotNull(workspaceRoot, "workspaceRoot must not be null");
        checkNotNull(changedFilesRoot, "changedFilesRoot must not be null");
        checkArgument(targetDirectory.toFile().isDirectory(), "targetDirectory must be a directory");
        checkArgument(workspaceRoot.toFile().isDirectory(), "workspaceRoot must be a directory");
        checkArgument(changedFilesRoot.toFile().isDirectory(), "changedFilesRoot must be a directory");

        CompilerConfiguration config = new CompilerConfiguration();
        config.setTargetDirectory(targetDirectory.toFile());
        GroovyWorkspaceCompiler wrapper =
                new GroovyWorkspaceCompiler(unitProvider, workspaceRoot, changedFilesRoot, config);
        unitProvider.set(new CompilationUnit(config));
        wrapper.addAllSourcesToCompilationUnit(unitProvider.get());

        return wrapper;
    }

    @Override
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @Override

    public Set<Diagnostic> compile() {
        try {
            unitProvider.get().compile();
        } catch (MultipleCompilationErrorsException e) {
            return parseErrors(e.getErrorCollector());
        }
        return Sets.newHashSet();
    }

    @Override
    public void handleFileChanged(URI originalFile, List<TextDocumentContentChangeEvent> contentChanges) {
        try {
            SourceWriter sourceWriter = null;
            if (originalSourceToChangedSource.containsKey(originalFile)) {
                // New change on existing changed source
                sourceWriter = originalSourceToChangedSource.get(originalFile);
            } else {
                // New source to switch out
                Path newChangedFilePath = changedFilesRoot.resolve(workspaceRoot.relativize(Paths.get(originalFile)));
                sourceWriter = SourceWriter.of(Paths.get(originalFile), newChangedFilePath);
                originalSourceToChangedSource.put(originalFile, sourceWriter);
            }
            // Apply changes to source writer and reset compilation unit
            sourceWriter.applyChanges(contentChanges);
            resetCompilationUnit();
        } catch (IOException e) {
            Throwables.propagate(e);
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
        try {
            if (originalSourceToChangedSource.containsKey(originalFile)) {
                Path changedSource = originalSourceToChangedSource.get(originalFile).getDestination();
                originalSourceToChangedSource.get(originalFile).saveChanges();
                originalSourceToChangedSource.remove(originalFile);
                // Deleted the changed file
                if (!changedSource.toFile().delete()) {
                    logger.error("Unable to delete file '{}'", changedSource.toAbsolutePath());
                }
                resetCompilationUnit();
            }
        } catch (IOException e) {
            Throwables.propagate(e);
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

    private void addAllSourcesToCompilationUnit(CompilationUnit unit) {
        // We don't include the files that have a corresponding SourceWriter since that means they will be replaced.
        for (File file : Files.fileTreeTraverser().preOrderTraversal(workspaceRoot.toFile())) {
            if (!originalSourceToChangedSource.containsKey(file.toURI()) && file.isFile() && Files
                    .getFileExtension(file.getAbsolutePath()).equals(GroovyConstants.GROOVY_LANGUAGE_EXTENSION)) {
                unit.addSource(file);
            }
        }
        // Add the replaced sources
        originalSourceToChangedSource.values()
                .forEach(sourceWriter -> unit.addSource(sourceWriter.getDestination().toFile()));
    }

    private void resetCompilationUnit() {
        try {
            FileUtils.deleteDirectory(config.getTargetDirectory());
            if (!config.getTargetDirectory().mkdir()) {
                logger.error("Could not recreate target directory: '{}'",
                        config.getTargetDirectory().getAbsolutePath());
                throw new RuntimeException("Could not reset compiled files after changes. "
                        + "Make sure you have permission to modify your target directory.");
            }
            CompilationUnit unit = new CompilationUnit(config);
            addAllSourcesToCompilationUnit(unit);
            unitProvider.set(unit);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    private Set<Diagnostic> parseErrors(ErrorCollector collector) {
        Set<Diagnostic> diagnostics = Sets.newHashSet();
        for (int i = 0; i < collector.getWarningCount(); i++) {
            WarningMessage message = collector.getWarning(i);

            diagnostics.add(new DefaultDiagnosticBuilder(message.getMessage(), DiagnosticSeverity.Warning).build());
        }
        for (int i = 0; i < collector.getErrorCount(); i++) {
            Message message = collector.getError(i);
            Diagnostic diagnostic;
            if (message instanceof SyntaxErrorMessage) {
                SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
                SyntaxException cause = syntaxErrorMessage.getCause();

                Range range =
                        Ranges.createZeroBasedRange(cause.getStartLine(), cause.getStartColumn(), cause.getEndLine(),
                                cause.getEndColumn());

                diagnostic = new DefaultDiagnosticBuilder(cause.getMessage(), DiagnosticSeverity.Error)
                        .range(range)
                        .source(cause.getSourceLocator())
                        .build();
            } else {
                StringWriter data = new StringWriter();
                PrintWriter writer = new PrintWriter(data);
                message.write(writer);
                diagnostic = new DefaultDiagnosticBuilder(data.toString(), DiagnosticSeverity.Error).build();
            }
            diagnostics.add(diagnostic);
        }
        return diagnostics;
    }

}
