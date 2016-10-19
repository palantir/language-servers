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
import io.typefox.lsapi.CodeActionParams;
import io.typefox.lsapi.CodeLens;
import io.typefox.lsapi.CodeLensParams;
import io.typefox.lsapi.Command;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionList;
import io.typefox.lsapi.DidChangeTextDocumentParams;
import io.typefox.lsapi.DidCloseTextDocumentParams;
import io.typefox.lsapi.DidOpenTextDocumentParams;
import io.typefox.lsapi.DidSaveTextDocumentParams;
import io.typefox.lsapi.DocumentFormattingParams;
import io.typefox.lsapi.DocumentHighlight;
import io.typefox.lsapi.DocumentOnTypeFormattingParams;
import io.typefox.lsapi.DocumentRangeFormattingParams;
import io.typefox.lsapi.DocumentSymbolParams;
import io.typefox.lsapi.Hover;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.RenameParams;
import io.typefox.lsapi.SignatureHelp;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.TextDocumentPositionParams;
import io.typefox.lsapi.TextEdit;
import io.typefox.lsapi.WorkspaceEdit;
import io.typefox.lsapi.services.TextDocumentService;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Provides a default implemented not dissimilar to to antlr generated visitors.
 * Markedly differs in throwing exceptions rather than more benign logs etc.
 */
public abstract class AbstractTextDocumentService implements TextDocumentService {

    protected abstract LanguageServerState getState();

    @Override
    public final void didOpen(DidOpenTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        getState().getCompilerWrapper().handleFileOpened(uri);
        getState().publishDiagnostics(getState().getCompilerWrapper().compile());
    }

    @Override
    public final void didChange(DidChangeTextDocumentParams params) {
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
    public final void didClose(DidCloseTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        getState().getCompilerWrapper().handleFileClosed(uri);
        getState().publishDiagnostics(getState().getCompilerWrapper().compile());
    }

    @Override
    public final void didSave(DidSaveTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        getState().getCompilerWrapper().handleFileSaved(uri);
        getState().publishDiagnostics(getState().getCompilerWrapper().compile());
    }

    @Override
    public final void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback) {
        getState().setPublishDiagnostics(callback);
    }

    final Path getWorkspacePath() {
        return Paths.get(getState().getCompilerWrapper().getWorkspaceRoot());
    }

    final void assertFileExists(URI uri) {
        checkArgument(new File(uri).exists(), String.format("Uri '%s' does not exist", uri));
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        throw new UnsupportedOperationException();
    }
}
