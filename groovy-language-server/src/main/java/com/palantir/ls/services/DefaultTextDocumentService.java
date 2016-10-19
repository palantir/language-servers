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

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.palantir.ls.api.LanguageServerState;
import com.palantir.ls.util.CompletionUtils;
import com.palantir.ls.util.Ranges;
import com.palantir.ls.util.Uris;
import io.typefox.lsapi.CompletionList;
import io.typefox.lsapi.DocumentSymbolParams;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.TextDocumentPositionParams;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class DefaultTextDocumentService extends AbstractTextDocumentService {
    private final LanguageServerState state;

    public DefaultTextDocumentService(LanguageServerState state) {
        this.state = state;
    }

    @Override
    public LanguageServerState getState() {
        return state;
    }

    @Override
    public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position) {
        return CompletableFuture.completedFuture(
                CompletionUtils.createCompletionListFromSymbols(state.getCompilerWrapper().getFileSymbols()
                        .get(Uris.resolveToRoot(getWorkspacePath(), position.getTextDocument().getUri()))));
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        URI uri = Uris.resolveToRoot(getWorkspacePath(), position.getTextDocument().getUri());
        return CompletableFuture.completedFuture(state.getCompilerWrapper().gotoDefinition(uri, position.getPosition())
                .transform(Lists::newArrayList).or(Lists.newArrayList()));
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFuture.completedFuture(
                state.getCompilerWrapper().findReferences(params).stream()
                        .filter(location -> Ranges.isValid(location.getRange()))
                        .collect(Collectors.toList()));
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
}
