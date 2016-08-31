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

import com.palantir.ls.groovy.api.CompilerWrapper;
import com.palantir.ls.groovy.api.TreeParser;
import com.palantir.ls.groovy.api.WorkspaceCompiler;
import io.typefox.lsapi.FileEvent;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.TextDocumentContentChangeEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wraps the Groovy compiler and provides Language Server Protocol diagnostics on compile.
 */
public final class GroovycWrapper implements CompilerWrapper {

    private final WorkspaceCompiler compiler;
    private final TreeParser parser;

    public GroovycWrapper(WorkspaceCompiler compiler, TreeParser parser) {
        this.compiler = compiler;
        this.parser = parser;
    }

    @Override
    public void parseAllSymbols() {
        parser.parseAllSymbols();
    }

    @Override
    public Map<URI, Set<SymbolInformation>> getFileSymbols() {
        return parser.getFileSymbols();
    }

    @Override
    public Map<String, Set<SymbolInformation>> getTypeReferences() {
        return parser.getTypeReferences();
    }

    @Override
    public Set<SymbolInformation> findReferences(ReferenceParams params) {
        return parser.findReferences(params);
    }

    @Override
    public Set<SymbolInformation> getFilteredSymbols(String query) {
        return parser.getFilteredSymbols(query);
    }

    @Override
    public Path getWorkspaceRoot() {
        return compiler.getWorkspaceRoot();
    }

    @Override
    public Set<PublishDiagnosticsParams> compile() {
        Set<PublishDiagnosticsParams> diagnostics = compiler.compile();
        if (diagnostics.isEmpty()) {
            parser.parseAllSymbols();
        }
        return diagnostics;
    }

    @Override
    public void handleFileChanged(URI originalFile, List<TextDocumentContentChangeEvent> contentChanges) {
        compiler.handleFileChanged(originalFile, contentChanges);
    }

    @Override
    public void handleFileClosed(URI originalFile) {
        compiler.handleFileClosed(originalFile);
    }

    @Override
    public void handleFileSaved(URI originalFile) {
        compiler.handleFileSaved(originalFile);
    }

    @Override
    public void handleChangeWatchedFiles(List<? extends FileEvent> changes) {
        compiler.handleChangeWatchedFiles(changes);
    }

}
