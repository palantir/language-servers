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

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.palantir.ls.util.Ranges;
import io.typefox.lsapi.CodeActionParams;
import io.typefox.lsapi.CodeLens;
import io.typefox.lsapi.CodeLensParams;
import io.typefox.lsapi.Command;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionItemKind;
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
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.TextDocumentPositionParams;
import io.typefox.lsapi.TextEdit;
import io.typefox.lsapi.WorkspaceEdit;
import io.typefox.lsapi.builders.CompletionItemBuilder;
import io.typefox.lsapi.builders.CompletionListBuilder;
import io.typefox.lsapi.builders.PublishDiagnosticsParamsBuilder;
import io.typefox.lsapi.impl.CompletionListImpl;
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
    private final LanguageServerConfig config;

    public GroovyTextDocumentService(CompilerWrapperProvider provider, LanguageServerConfig config) {
        this.provider = provider;
        this.config = config;
    }

    @Override
    public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position) {
        return CompletableFuture.completedFuture(createCompletionListFromSymbols(
                provider.get().getFileSymbols().get(position.getTextDocument().getUri())));
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
                provider.get().findReferences(params).stream()
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
        Path absoluteUri = resolveUriToWorkspaceRoot(params.getTextDocument().getUri());
        assertFileExists(absoluteUri);
        List<SymbolInformation> symbols =
                Optional.fromNullable(provider.get().getFileSymbols().get(absoluteUri.toString()).stream()
                        .collect(Collectors.toList())).or(Lists.newArrayList());
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
        Path absoluteUri = resolveUriToWorkspaceRoot(params.getTextDocument().getUri());
        assertFileExists(absoluteUri);
        publishDiagnostics(provider.get().compile());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        Path absoluteUri = resolveUriToWorkspaceRoot(params.getTextDocument().getUri());
        assertFileExists(absoluteUri);
        if (params.getContentChanges() == null || params.getContentChanges().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Calling didChange with no changes on uri '%s'", absoluteUri.toString()));
        }
        provider.get().handleFileChanged(absoluteUri, Lists.newArrayList(params.getContentChanges()));
        publishDiagnostics(provider.get().compile());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        Path absoluteUri = resolveUriToWorkspaceRoot(params.getTextDocument().getUri());
        assertFileExists(absoluteUri);
        provider.get().handleFileClosed(absoluteUri);
        publishDiagnostics(provider.get().compile());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        Path absoluteUri = resolveUriToWorkspaceRoot(params.getTextDocument().getUri());
        assertFileExists(absoluteUri);
        provider.get().handleFileSaved(absoluteUri);
        publishDiagnostics(provider.get().compile());
    }

    @Override
    public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback) {
        config.setPublishDiagnostics(callback);
    }

    private void publishDiagnostics(Set<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return;
        }
        PublishDiagnosticsParamsBuilder paramsBuilder =
                new PublishDiagnosticsParamsBuilder()
                    .uri(provider.get().getWorkspaceRoot().toAbsolutePath().toString());
        diagnostics.stream().forEach(d -> paramsBuilder.diagnostic(d));
        config.getPublishDiagnostics().accept(paramsBuilder.build());
    }

    private Path resolveUriToWorkspaceRoot(String uri) {
        Path filePath = Paths.get(uri);
        return filePath.isAbsolute() ? filePath : provider.get().getWorkspaceRoot().resolve(uri).toAbsolutePath();
    }

    private void assertFileExists(Path uri) {
        checkArgument(uri.toFile().exists(), String.format("Uri '%s' does not exist", uri));
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
