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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import com.palantir.ls.api.CompilerWrapper;
import com.palantir.ls.api.LanguageServerState;
import com.palantir.ls.util.Ranges;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.DidChangeConfigurationParams;
import io.typefox.lsapi.FileChangeType;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.builders.DiagnosticBuilder;
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

public class AbstractWorkspaceServiceTest {

    private static class TestWorkspaceService extends AbstractWorkspaceService {
        private final LanguageServerState state;

        TestWorkspaceService(LanguageServerState state) {
            this.state = state;
        }

        @Override
        protected LanguageServerState getState() {
            return state;
        }

        @Override
        public void didChangeConfiguration(DidChangeConfigurationParams didChangeConfigurationParams) {
            throw new UnsupportedOperationException();
        }
    }

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();

    private AbstractWorkspaceService service;
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
                        .diagnostic(new DiagnosticBuilder()
                                .message("Some message")
                                .severity(DiagnosticSeverity.Error)
                                .build()
                        ).diagnostic(new DiagnosticBuilder()
                                .message("Some other message")
                                .severity(DiagnosticSeverity.Warning)
                                .build()
                        ).build());

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

        service = new TestWorkspaceService(state);
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
