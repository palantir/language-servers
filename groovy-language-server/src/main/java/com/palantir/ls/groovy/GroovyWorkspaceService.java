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

import com.palantir.ls.util.Ranges;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DidChangeConfigurationParams;
import io.typefox.lsapi.DidChangeWatchedFilesParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.WorkspaceSymbolParams;
import io.typefox.lsapi.builders.PublishDiagnosticsParamsBuilder;
import io.typefox.lsapi.services.WorkspaceService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class GroovyWorkspaceService implements WorkspaceService {

    private final CompilerWrapperProvider provider;
    private final LanguageServerConfig config;

    public GroovyWorkspaceService(CompilerWrapperProvider provider, LanguageServerConfig config) {
        this.provider = provider;
        this.config = config;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        return CompletableFuture.completedFuture(provider.get().getFilteredSymbols(params.getQuery()).stream()
                .filter(symbol -> Ranges.isValid(symbol.getLocation().getRange())).collect(Collectors.toList()));
    }

    @Override
    public void didChangeConfiguraton(DidChangeConfigurationParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        provider.get().handleChangeWatchedFiles(params.getChanges());
        publishDiagnostics(provider.get().compile());
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

}
