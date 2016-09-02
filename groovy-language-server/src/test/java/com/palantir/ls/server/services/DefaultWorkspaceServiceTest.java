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

package com.palantir.ls.server.services;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import com.palantir.ls.server.api.CompilerWrapper;
import com.palantir.ls.server.api.LanguageServerState;
import com.palantir.ls.server.groovy.util.DefaultDiagnosticBuilder;
import com.palantir.ls.server.util.Ranges;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.FileChangeType;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.builders.DidChangeWatchedFilesParamsBuilder;
import io.typefox.lsapi.builders.LocationBuilder;
import io.typefox.lsapi.builders.PublishDiagnosticsParamsBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import io.typefox.lsapi.builders.WorkspaceSymbolParamsBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public final class DefaultWorkspaceServiceTest {

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();

    private DefaultWorkspaceService service;
    private Set<PublishDiagnosticsParams> expectedDiagnostics = Sets.newHashSet();
    private Set<SymbolInformation> expectedReferences = Sets.newHashSet();

    @Mock
    private CompilerWrapper compilerWrapper;
    @Mock
    private LanguageServerState state;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        expectedDiagnostics =
                Sets.newHashSet(new PublishDiagnosticsParamsBuilder().uri("foo")
                        .diagnostic(new DefaultDiagnosticBuilder("Some message", DiagnosticSeverity.Error).build())
                        .diagnostic(
                                new DefaultDiagnosticBuilder("Some other message", DiagnosticSeverity.Warning).build())
                        .build());

        expectedReferences.add(new SymbolInformationBuilder()
                .containerName("Something")
                .kind(SymbolKind.Class)
                .name("MyClassName")
                .location(new LocationBuilder()
                        .uri("uri")
                        .range(Ranges.createRange(1, 1, 9, 9))
                        .build())
                .build());
        expectedReferences.add(new SymbolInformationBuilder()
                .containerName("SomethingElse")
                .kind(SymbolKind.Class)
                .name("MyClassName2")
                .location(new LocationBuilder()
                            .uri("uri")
                            .range(Ranges.createRange(1, 1, 9, 9))
                            .build())
                .build());
        Set<SymbolInformation> allReferencesReturned = Sets.newHashSet(expectedReferences);
        // The reference that will be filtered out
        allReferencesReturned.add(new SymbolInformationBuilder()
                .containerName("SomethingElse")
                .kind(SymbolKind.Class)
                .name("MyClassName3")
                .location(new LocationBuilder()
                            .uri("uri")
                            .range(Ranges.UNDEFINED_RANGE)
                            .build())
                .build());

        when(compilerWrapper.getWorkspaceRoot()).thenReturn(workspace.getRoot().toPath().toUri());
        when(compilerWrapper.compile()).thenReturn(expectedDiagnostics);
        when(compilerWrapper.getFilteredSymbols(Mockito.any())).thenReturn(allReferencesReturned);

        when(state.getCompilerWrapper()).thenReturn(compilerWrapper);

        service = new DefaultWorkspaceService(state);
    }

    @Test
    public void testSymbol() throws InterruptedException, ExecutionException {
        CompletableFuture<List<? extends SymbolInformation>> response =
                service.symbol(new WorkspaceSymbolParamsBuilder().query("myQuery").build());
        assertThat(response.get().stream().collect(Collectors.toSet()), is(expectedReferences));
    }

    @Test
    public void testDidChangeWatchedFiles() throws InterruptedException, ExecutionException {
        service.didChangeWatchedFiles(new DidChangeWatchedFilesParamsBuilder().change("uri", FileChangeType.Deleted)
                .change("uri", FileChangeType.Created).change("uri", FileChangeType.Changed).build());
        // assert diagnostics were published
        Mockito.verify(state, Mockito.times(1)).publishDiagnostics(expectedDiagnostics);
    }

}
