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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.palantir.groovylanguageserver.util.DefaultDiagnosticBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.TextDocumentIdentifier;
import io.typefox.lsapi.TextDocumentItem;
import io.typefox.lsapi.builders.DidChangeTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidCloseTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidOpenTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidSaveTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DocumentSymbolParamsBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import io.typefox.lsapi.builders.TextDocumentIdentifierBuilder;
import io.typefox.lsapi.builders.TextDocumentItemBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
public final class GroovyTextDocumentServiceTest {

    private static final Path WORKSPACE_PATH = Paths.get("/some/path/that/may/exist/");

    private GroovyTextDocumentService service;
    private List<PublishDiagnosticsParams> publishedDiagnostics = Lists.newArrayList();
    private Set<Diagnostic> expectedDiagnostics = Sets.newHashSet();
    private Map<String, Set<SymbolInformation>> symbolsMap = Maps.newHashMap();

    @Mock
    private CompilerWrapper compilerWrapper;
    @Mock
    private CompilerWrapperProvider provider;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        expectedDiagnostics.add(new DefaultDiagnosticBuilder("Some message", DiagnosticSeverity.Error).build());
        expectedDiagnostics.add(new DefaultDiagnosticBuilder("Some other message", DiagnosticSeverity.Warning).build());
        Set<Diagnostic> diagnostics = Sets.newHashSet(expectedDiagnostics);

        SymbolInformation symbol = new SymbolInformationBuilder().name("ThisIsASymbol").build();
        symbolsMap.put(WORKSPACE_PATH.resolve("something.groovy").toString(), Sets.newHashSet(symbol));

        when(compilerWrapper.getWorkspaceRoot()).thenReturn(WORKSPACE_PATH);
        when(compilerWrapper.compile()).thenReturn(diagnostics);
        when(compilerWrapper.getFileSymbols()).thenReturn(symbolsMap);

        when(provider.get()).thenReturn(compilerWrapper);

        service = new GroovyTextDocumentService(provider);

        Consumer<PublishDiagnosticsParams> callback = p -> {
            publishDiagnostics(p);
        };

        service.onPublishDiagnostics(callback);
    }

    private void publishDiagnostics(PublishDiagnosticsParams params) {
        publishedDiagnostics.add(params);
    }

    @Test
    public void testDidOpen() {
        TextDocumentItem textDocument =
                new TextDocumentItemBuilder().uri(WORKSPACE_PATH.resolve("something.groovy").toString())
                        .languageId("groovy").version(1).text("something").build();
        service.didOpen(new DidOpenTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidChange() {
        service.didChange(new DidChangeTextDocumentParamsBuilder()
                .uri(WORKSPACE_PATH.resolve("something.groovy").toString()).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidClose() {
        TextDocumentIdentifier textDocument =
                new TextDocumentIdentifierBuilder().uri(WORKSPACE_PATH.resolve("something.groovy").toString()).build();
        service.didClose(new DidCloseTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidSave() {
        TextDocumentIdentifier textDocument =
                new TextDocumentIdentifierBuilder().uri(WORKSPACE_PATH.resolve("something.groovy").toString()).build();
        service.didSave(new DidSaveTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDocumentSymbols_absolutePath() throws InterruptedException, ExecutionException {
        TextDocumentIdentifier textDocument =
                new TextDocumentIdentifierBuilder().uri(WORKSPACE_PATH.resolve("something.groovy").toString()).build();
        CompletableFuture<List<? extends SymbolInformation>> response =
                service.documentSymbol(new DocumentSymbolParamsBuilder().textDocument(textDocument).build());
        assertThat(response.get().stream().collect(Collectors.toSet()),
                is(symbolsMap.get(WORKSPACE_PATH.resolve("something.groovy").toString())));
    }

    @Test
    public void testDocumentSymbols_relativePath() throws InterruptedException, ExecutionException {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder().uri("something.groovy").build();
        CompletableFuture<List<? extends SymbolInformation>> response =
                service.documentSymbol(new DocumentSymbolParamsBuilder().textDocument(textDocument).build());
        assertThat(response.get().stream().collect(Collectors.toSet()),
                is(symbolsMap.get(WORKSPACE_PATH.resolve("something.groovy").toString())));
    }

}
