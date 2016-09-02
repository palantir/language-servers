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

package com.palantir.ls.server.api;

import io.typefox.lsapi.FileEvent;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.TextDocumentContentChangeEvent;
import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Provides wrapper methods for compiling a workspace, handles incremental changes, and returns Language Server Protocol
 * diagnostics.
 */
public interface WorkspaceCompiler {

    /**
     * Returns the root of the compiled workspace.
     */
    URI getWorkspaceRoot();

    /**
     * Compiles all relevant files in the workspace.
     * @return the compilation warnings and errors by file
     */
    Set<PublishDiagnosticsParams>  compile();

    /**
     * Handle opening a file.
     */
    void handleFileOpened(URI file);

    /**
     * Handle adding incremental changes to open files to be included in compilation.
     */
    void handleFileChanged(URI originalFile, List<TextDocumentContentChangeEvent> contentChanges);

    /**
     * Handle removing non-saved changes to open files from compiled source files.
     * @param originalFile the URI of the original file
     */
    void handleFileClosed(URI originalFile);

    /**
     * Handle saving the accumulated changes to the origin source.
     * @param originalFile the URI of the original file
     */
    void handleFileSaved(URI originalFile);

    /**
     * Handles reconfiguring the compiled files in the event some files were created, changed or deleted outside of the
     * language server.
     */
    void handleChangeWatchedFiles(List<? extends FileEvent> changes);

}
