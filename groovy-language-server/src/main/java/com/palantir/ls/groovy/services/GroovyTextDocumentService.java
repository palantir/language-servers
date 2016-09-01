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

package com.palantir.ls.groovy.services;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.palantir.ls.groovy.LanguageServerState;
import com.palantir.ls.groovy.util.Ranges;
import com.palantir.ls.groovy.util.Uris;
import io.typefox.lsapi.CodeActionParams;
import io.typefox.lsapi.CodeLens;
import io.typefox.lsapi.CodeLensParams;
import io.typefox.lsapi.Command;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionItemKind;
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
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.TextDocumentPositionParams;
import io.typefox.lsapi.TextEdit;
import io.typefox.lsapi.WorkspaceEdit;
import io.typefox.lsapi.builders.CompletionItemBuilder;
import io.typefox.lsapi.builders.CompletionListBuilder;
import io.typefox.lsapi.impl.CompletionListImpl;
import io.typefox.lsapi.services.TextDocumentService;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class GroovyTextDocumentService implements TextDocumentService {

    private final LanguageServerState state;

    public GroovyTextDocumentService(LanguageServerState state) {
        this.state = state;
    }

    private Path getWorkspacePath() {
        return Paths.get(state.getCompilerWrapper().getWorkspaceRoot());
    }

    @Override
    public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position) {
        return CompletableFuture.completedFuture(
                createCompletionListFromSymbols(state.getCompilerWrapper().getFileSymbols()
                        .get(Uris.resolveToRoot(getWorkspacePath(), position.getTextDocument().getUri()))));
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
        return CompletableFuture.completedFuture(
                state.getCompilerWrapper().findReferences(params).stream()
                        .map(symbol -> symbol.getLocation())
                        .filter(location -> Ranges.isValid(location.getRange()))
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<DocumentHighlight> documentHighlight(TextDocumentPositionParams position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        List<SymbolInformation> symbols =
                Optional.fromNullable(
                        state.getCompilerWrapper().getFileSymbols().get(uri).stream().collect(Collectors.toList()))
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
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        state.publishDiagnostics(state.getCompilerWrapper().compile());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        if (params.getContentChanges() == null || params.getContentChanges().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Calling didChange with no changes on uri '%s'", uri.toString()));
        }
        state.getCompilerWrapper().handleFileChanged(uri, Lists.newArrayList(params.getContentChanges()));
        state.publishDiagnostics(state.getCompilerWrapper().compile());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        state.getCompilerWrapper().handleFileClosed(uri);
        state.publishDiagnostics(state.getCompilerWrapper().compile());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), params.getTextDocument().getUri());
        assertFileExists(uri);
        state.getCompilerWrapper().handleFileSaved(uri);
        state.publishDiagnostics(state.getCompilerWrapper().compile());
    }

    @Override
    public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback) {
        state.setPublishDiagnostics(callback);
    }

    private void assertFileExists(URI uri) {
        checkArgument(new File(uri).exists(), String.format("Uri '%s' does not exist", uri));
    }

    private static CompletionList createCompletionListFromSymbols(Set<SymbolInformation> symbols) {
        if (symbols == null) {
            return new CompletionListImpl(false, Lists.newArrayList());
        }
        CompletionListBuilder builder = new CompletionListBuilder().incomplete(false);
        symbols.forEach(symbol -> {
            builder.item(new CompletionItemBuilder()
                    .label(symbol.getName())
                    .kind(symbolKindToCompletionItemKind(symbol.getKind()))
                    .build());
        });
        return builder.build();
    }

    @SuppressWarnings("checkstyle:cyclomaticcomplexity") // this is not complex behaviour
    private static CompletionItemKind symbolKindToCompletionItemKind(SymbolKind kind) {
        switch (kind) {
            case Class:
                return CompletionItemKind.Class;
            case Constructor:
                return CompletionItemKind.Constructor;
            case Enum:
                return CompletionItemKind.Enum;
            case Field:
                return CompletionItemKind.Field;
            case File:
                return CompletionItemKind.File;
            case Function:
                return CompletionItemKind.Function;
            case Interface:
                return CompletionItemKind.Interface;
            case Method:
                return CompletionItemKind.Method;
            case Property:
                return CompletionItemKind.Property;
            case String:
                return CompletionItemKind.Text;
            case Variable:
                return CompletionItemKind.Variable;
            case Array:
            case Boolean:
            case Constant:
            case Number:
                return CompletionItemKind.Value;
            case Module:
            case Namespace:
            case Package:
                return CompletionItemKind.Module;
            default:
                throw new IllegalArgumentException(String.format("Unsupported SymbolKind: %s", kind));
        }
    }

}
