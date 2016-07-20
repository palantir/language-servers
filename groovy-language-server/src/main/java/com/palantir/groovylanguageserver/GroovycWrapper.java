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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.palantir.groovylanguageserver.util.DiagnosticBuilder;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticImpl;
import io.typefox.lsapi.RangeImpl;
import io.typefox.lsapi.util.LsapiFactories;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.syntax.SyntaxException;

/**
 * Wraps the Groovy compiler and provides Language Server Protocol diagnostics on compile.
 */
public final class GroovycWrapper implements CompilerWrapper {

    private static final String GROOVY_EXTENSION = "groovy";
    private final Path workspaceRoot;
    private final CompilationUnit unit;

    private GroovycWrapper(CompilationUnit unit, Path workspaceRoot) {
        this.unit = unit;
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Creates a new instance of GroovycWrapper.
     * @param targetDirectory the directory in which to put generated files
     * @param workspaceRoot the directory to compile
     * @return the newly created GroovycWrapper
     */
    public static GroovycWrapper of(Path targetDirectory, Path workspaceRoot) {
        Preconditions.checkNotNull(workspaceRoot, "workspaceRoot must not be null");
        Preconditions.checkArgument(workspaceRoot.toFile().isDirectory(), "workspaceRoot must be a directory");

        CompilerConfiguration config = new CompilerConfiguration();
        config.setTargetDirectory(targetDirectory.toFile());
        GroovycWrapper wrapper = new GroovycWrapper(new CompilationUnit(config), workspaceRoot);
        wrapper.addAllSourcesToCompilationUnit();

        return wrapper;
    }

    @Override
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @Override
    public List<DiagnosticImpl> compile() {
        List<DiagnosticImpl> diagnostics = Lists.newArrayList();
        try {
            unit.compile();
        } catch (MultipleCompilationErrorsException e) {
            parseErrors(e.getErrorCollector(), diagnostics);
        }
        return diagnostics;
    }

    private void addAllSourcesToCompilationUnit() {
        for (File file : Files.fileTreeTraverser().preOrderTraversal(workspaceRoot.toFile())) {
            if (file.isFile() && Files.getFileExtension(file.getAbsolutePath()).equals(GROOVY_EXTENSION)) {
                unit.addSource(file);
            }
        }
    }

    private void parseErrors(ErrorCollector collector, List<DiagnosticImpl> diagnostics) {
        for (int i = 0; i < collector.getWarningCount(); i++) {
            WarningMessage message = collector.getWarning(i);
            diagnostics.add(new DiagnosticBuilder(message.getMessage(), Diagnostic.SEVERITY_WARNING).build());
        }
        for (int i = 0; i < collector.getErrorCount(); i++) {
            Message message = collector.getError(i);
            DiagnosticImpl diagnostic;
            if (message instanceof SyntaxErrorMessage) {
                SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
                SyntaxException cause = syntaxErrorMessage.getCause();

                RangeImpl range = LsapiFactories.newRange(
                                    LsapiFactories.newPosition(cause.getStartLine(), cause.getStartColumn()),
                                    LsapiFactories.newPosition(cause.getEndLine(), cause.getEndColumn()));
                diagnostic = new DiagnosticBuilder(cause.getMessage(), Diagnostic.SEVERITY_ERROR)
                                    .range(range).source(cause.getSourceLocator()).build();
            } else {
                StringWriter data = new StringWriter();
                PrintWriter writer = new PrintWriter(data);
                message.write(writer);
                diagnostic = new DiagnosticBuilder(data.toString(), Diagnostic.SEVERITY_ERROR).build();
            }
            diagnostics.add(diagnostic);
        }
    }

}
