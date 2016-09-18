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

package com.palantir.ls.services;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import com.palantir.ls.api.LanguageServerState;
import com.palantir.ls.util.Uris;
import io.typefox.lsapi.DidChangeTextDocumentParams;
import io.typefox.lsapi.DidCloseTextDocumentParams;
import io.typefox.lsapi.DidOpenTextDocumentParams;
import io.typefox.lsapi.DidSaveTextDocumentParams;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.services.TextDocumentService;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public abstract class AbstractTextDocumentService implements TextDocumentService {

    abstract protected LanguageServerState getState();

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        getState().getCompilerWrapper().handleFileOpened(uri);
        getState().publishDiagnostics(getState().getCompilerWrapper().compile());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        if (params.getContentChanges() == null || params.getContentChanges().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Calling didChange with no changes on uri '%s'", uri.toString()));
        }
        getState().getCompilerWrapper().handleFileChanged(uri, Lists.newArrayList(params.getContentChanges()));
        getState().publishDiagnostics(getState().getCompilerWrapper().compile());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        getState().getCompilerWrapper().handleFileClosed(uri);
        getState().publishDiagnostics(getState().getCompilerWrapper().compile());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        getState().getCompilerWrapper().handleFileSaved(uri);
        getState().publishDiagnostics(getState().getCompilerWrapper().compile());
    }

    @Override
    public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback) {
        getState().setPublishDiagnostics(callback);
    }

    protected Path getWorkspacePath() {
        return Paths.get(getState().getCompilerWrapper().getWorkspaceRoot());
    }

    protected void assertFileExists(URI uri) {
        checkArgument(new File(uri).exists(), String.format("Uri '%s' does not exist", uri));
    }

}
