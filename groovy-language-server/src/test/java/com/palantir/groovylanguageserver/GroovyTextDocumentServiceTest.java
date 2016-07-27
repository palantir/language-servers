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
import com.palantir.groovylanguageserver.util.DiagnosticBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticImpl;
import io.typefox.lsapi.DidChangeTextDocumentParamsImpl;
import io.typefox.lsapi.DidCloseTextDocumentParamsImpl;
import io.typefox.lsapi.DidOpenTextDocumentParamsImpl;
import io.typefox.lsapi.DidSaveTextDocumentParamsImpl;
import io.typefox.lsapi.DocumentSymbolParamsImpl;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolInformationImpl;
import io.typefox.lsapi.TextDocumentIdentifierImpl;
import io.typefox.lsapi.TextDocumentItemImpl;
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
    private Set<DiagnosticImpl> expectedDiagnostics = Sets.newHashSet();
    private Map<String, Set<SymbolInformation>> symbolsMap = Maps.newHashMap();

    @Mock
    private CompilerWrapper compilerWrapper;
    @Mock
    private CompilerWrapperProvider provider;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        expectedDiagnostics.add(new DiagnosticBuilder("Some message", Diagnostic.SEVERITY_ERROR).build());
        List<DiagnosticImpl> diagnostics = Lists.newArrayList(expectedDiagnostics);

        SymbolInformationImpl symbol = new SymbolInformationImpl();
        symbol.setName("ThisIsASymbol");
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
        DidOpenTextDocumentParamsImpl params = new DidOpenTextDocumentParamsImpl();
        TextDocumentItemImpl textDocument = new TextDocumentItemImpl();
        textDocument.setUri(WORKSPACE_PATH.resolve("something.groovy").toString());
        textDocument.setLanguageId("groovy");
        textDocument.setVersion(1);
        textDocument.setText("something");
        params.setTextDocument(textDocument);
        service.didOpen(params);
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidChange() {
        DidChangeTextDocumentParamsImpl params = new DidChangeTextDocumentParamsImpl();
        params.setUri(WORKSPACE_PATH.resolve("something.groovy").toString());
        service.didChange(params);
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidClose() {
        DidCloseTextDocumentParamsImpl params = new DidCloseTextDocumentParamsImpl();
        TextDocumentIdentifierImpl textDocument = new TextDocumentIdentifierImpl();
        textDocument.setUri(WORKSPACE_PATH.resolve("something.groovy").toString());
        params.setTextDocument(textDocument);
        service.didClose(params);
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidSave() {
        DidSaveTextDocumentParamsImpl params = new DidSaveTextDocumentParamsImpl();
        TextDocumentIdentifierImpl textDocument = new TextDocumentIdentifierImpl();
        textDocument.setUri(WORKSPACE_PATH.resolve("something.groovy").toString());
        params.setTextDocument(textDocument);
        service.didSave(params);
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDocumentSymbols_absolutePath() throws InterruptedException, ExecutionException {
        DocumentSymbolParamsImpl params = new DocumentSymbolParamsImpl();
        TextDocumentIdentifierImpl textDocument = new TextDocumentIdentifierImpl();
        textDocument.setUri(WORKSPACE_PATH.resolve("something.groovy").toString());
        params.setTextDocument(textDocument);
        CompletableFuture<List<? extends SymbolInformation>> response = service.documentSymbol(params);
        assertThat(response.get().stream().collect(Collectors.toSet()),
                is(symbolsMap.get(WORKSPACE_PATH.resolve("something.groovy").toString())));
    }

    @Test
    public void testDocumentSymbols_relativePath() throws InterruptedException, ExecutionException {
        DocumentSymbolParamsImpl params = new DocumentSymbolParamsImpl();
        TextDocumentIdentifierImpl textDocument = new TextDocumentIdentifierImpl();
        textDocument.setUri("something.groovy");
        params.setTextDocument(textDocument);
        CompletableFuture<List<? extends SymbolInformation>> response = service.documentSymbol(params);
        assertThat(response.get().stream().collect(Collectors.toSet()),
                is(symbolsMap.get(WORKSPACE_PATH.resolve("something.groovy").toString())));
    }

}
