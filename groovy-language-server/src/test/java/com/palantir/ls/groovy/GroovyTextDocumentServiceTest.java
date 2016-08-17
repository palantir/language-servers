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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.palantir.ls.util.DefaultDiagnosticBuilder;
import com.palantir.ls.util.Ranges;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.typefox.lsapi.CompletionItemKind;
import io.typefox.lsapi.CompletionList;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.TextDocumentIdentifier;
import io.typefox.lsapi.TextDocumentItem;
import io.typefox.lsapi.TextDocumentPositionParams;
import io.typefox.lsapi.builders.CompletionItemBuilder;
import io.typefox.lsapi.builders.CompletionListBuilder;
import io.typefox.lsapi.builders.DidChangeTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidCloseTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidOpenTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidSaveTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DocumentSymbolParamsBuilder;
import io.typefox.lsapi.builders.LocationBuilder;
import io.typefox.lsapi.builders.ReferenceParamsBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import io.typefox.lsapi.builders.TextDocumentIdentifierBuilder;
import io.typefox.lsapi.builders.TextDocumentItemBuilder;
import io.typefox.lsapi.builders.TextDocumentPositionParamsBuilder;
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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
public final class GroovyTextDocumentServiceTest {

    private static final Path WORKSPACE_PATH = Paths.get("/some/path/that/may/exist/");

    private GroovyTextDocumentService service;
    private List<PublishDiagnosticsParams> publishedDiagnostics = Lists.newArrayList();
    private Set<Diagnostic> expectedDiagnostics = Sets.newHashSet();
    private Map<String, Set<SymbolInformation>> symbolsMap = Maps.newHashMap();
    private Set<SymbolInformation> expectedReferences = Sets.newHashSet();

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

        SymbolInformation symbol1 = new SymbolInformationBuilder().name("ThisIsASymbol").kind(SymbolKind.Field).build();
        SymbolInformation symbol2 = new SymbolInformationBuilder().name("methodA").kind(SymbolKind.Method).build();
        symbolsMap.put(WORKSPACE_PATH.resolve("something.groovy").toString(), Sets.newHashSet(symbol1, symbol2));

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
        when(compilerWrapper.getWorkspaceRoot()).thenReturn(WORKSPACE_PATH);
        when(compilerWrapper.compile()).thenReturn(diagnostics);
        when(compilerWrapper.getFileSymbols()).thenReturn(symbolsMap);
        when(compilerWrapper.findReferences(Mockito.any())).thenReturn(allReferencesReturned);

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
        TextDocumentItem textDocument = new TextDocumentItemBuilder()
                .uri(WORKSPACE_PATH.resolve("something.groovy").toString())
                .languageId("groovy")
                .version(1)
                .text("something")
                .build();
        service.didOpen(new DidOpenTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidChange() {
        service.didChange(new DidChangeTextDocumentParamsBuilder()
                .uri(WORKSPACE_PATH.resolve("something.groovy").toString())
                .build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidClose() {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(WORKSPACE_PATH.resolve("something.groovy").toString())
                .build();
        service.didClose(new DidCloseTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidSave() {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(WORKSPACE_PATH.resolve("something.groovy").toString())
                .build();
        service.didSave(new DidSaveTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(WORKSPACE_PATH.toString(), publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDocumentSymbols_absolutePath() throws InterruptedException, ExecutionException {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(WORKSPACE_PATH.resolve("something.groovy").toString())
                .build();
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

    @Test
    public void testReferences() throws InterruptedException, ExecutionException {
        // HACK, blocked on https://github.com/TypeFox/ls-api/issues/39
        ReferenceParams params = (ReferenceParams) new ReferenceParamsBuilder()
                .context(false).position(5, 5)
                .textDocument("uri")
                .uri("uri")
                .build();
        CompletableFuture<List<? extends Location>> response = service.references(params);
        assertThat(response.get().stream().collect(Collectors.toSet()),
                is(expectedReferences.stream().map(symbol -> symbol.getLocation()).collect(Collectors.toSet())));
    }

    @Test
    public void testCompletion() throws InterruptedException, ExecutionException {
        String uri = WORKSPACE_PATH.resolve("something.groovy").toString();
        TextDocumentPositionParams params = new TextDocumentPositionParamsBuilder()
                .position(5, 5)
                .textDocument(uri)
                .uri(uri)
                .build();
        CompletableFuture<CompletionList> response = service.completion(params);
        CompletionList expectedResult = new CompletionListBuilder()
                .incomplete(false)
                .item(new CompletionItemBuilder()
                        .label("ThisIsASymbol")
                        .kind(CompletionItemKind.Field)
                        .build())
                .item(new CompletionItemBuilder()
                        .label("methodA")
                        .kind(CompletionItemKind.Method).build())
                .build();
        assertThat(response.get().isIncomplete(), is(expectedResult.isIncomplete()));
        assertThat(Sets.newHashSet(response.get().getItems()), is(Sets.newHashSet(expectedResult.getItems())));
    }

    @Test
    public void testCompletion_noSymbols() throws InterruptedException, ExecutionException {
        String uri = WORKSPACE_PATH.resolve("somethingthatdoesntexist.groovy").toString();
        TextDocumentPositionParams params = new TextDocumentPositionParamsBuilder()
                .position(5, 5)
                .textDocument(uri)
                .uri(uri)
                .build();
        CompletableFuture<CompletionList> response = service.completion(params);
        assertThat(response.get().isIncomplete(), is(false));
        assertThat(response.get().getItems(), is(Lists.newArrayList()));
    }

}
