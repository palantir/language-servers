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

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import io.typefox.lsapi.CodeActionParams;
import io.typefox.lsapi.CodeLens;
import io.typefox.lsapi.CodeLensParams;
import io.typefox.lsapi.Command;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionList;
import io.typefox.lsapi.Diagnostic;
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
import io.typefox.lsapi.builders.PublishDiagnosticsParamsBuilder;
import io.typefox.lsapi.services.TextDocumentService;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class GroovyTextDocumentService implements TextDocumentService {

    private final CompilerWrapperProvider provider;

    private Consumer<PublishDiagnosticsParams> publishDiagnostics = p -> { };

    public GroovyTextDocumentService(CompilerWrapperProvider provider) {
        this.provider = provider;
    }

    @Override
    public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position) {
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
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFuture.completedFuture(Lists.newArrayList(provider.get().findReferences(params)));
    }

    @Override
    public CompletableFuture<DocumentHighlight> documentHighlight(TextDocumentPositionParams position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        Path filePath = Paths.get(uri);
        if (!filePath.isAbsolute()) {
            uri = provider.get().getWorkspaceRoot().resolve(uri).toAbsolutePath().toString();
        }
        List<SymbolInformation> symbols =
                Optional.fromNullable(provider.get().getFileSymbols().get(uri).stream().collect(Collectors.toList()))
                        .or(Lists.newArrayList());
        return CompletableFuture.completedFuture(symbols);
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
    public void didOpen(DidOpenTextDocumentParams params) {
        publishDiagnostics(provider.get().compile());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        publishDiagnostics(provider.get().compile());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        publishDiagnostics(provider.get().compile());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        publishDiagnostics(provider.get().compile());
    }

    @Override
    public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback) {
        publishDiagnostics = callback;
    }

    private void publishDiagnostics(Set<Diagnostic> diagnostics) {
        PublishDiagnosticsParamsBuilder paramsBuilder =
                new PublishDiagnosticsParamsBuilder()
                    .uri(provider.get().getWorkspaceRoot().toAbsolutePath().toString());
        diagnostics.stream().forEach(d -> paramsBuilder.diagnostic(d));
        publishDiagnostics.accept(paramsBuilder.build());
    }

}
