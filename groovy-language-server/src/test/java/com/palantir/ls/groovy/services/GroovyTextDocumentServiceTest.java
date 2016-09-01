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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.palantir.ls.groovy.GroovyLanguageServerState;
import com.palantir.ls.groovy.LanguageServerState;
import com.palantir.ls.groovy.api.CompilerWrapper;
import com.palantir.ls.groovy.util.DefaultDiagnosticBuilder;
import com.palantir.ls.groovy.util.Ranges;
import io.typefox.lsapi.CompletionItemKind;
import io.typefox.lsapi.CompletionList;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.TextDocumentIdentifier;
import io.typefox.lsapi.TextDocumentItem;
import io.typefox.lsapi.TextDocumentPositionParams;
import io.typefox.lsapi.VersionedTextDocumentIdentifier;
import io.typefox.lsapi.builders.CompletionItemBuilder;
import io.typefox.lsapi.builders.CompletionListBuilder;
import io.typefox.lsapi.builders.DidChangeTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidCloseTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidOpenTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DidSaveTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DocumentSymbolParamsBuilder;
import io.typefox.lsapi.builders.LocationBuilder;
import io.typefox.lsapi.builders.PublishDiagnosticsParamsBuilder;
import io.typefox.lsapi.builders.ReferenceParamsBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import io.typefox.lsapi.builders.TextDocumentIdentifierBuilder;
import io.typefox.lsapi.builders.TextDocumentItemBuilder;
import io.typefox.lsapi.builders.TextDocumentPositionParamsBuilder;
import io.typefox.lsapi.builders.VersionedTextDocumentIdentifierBuilder;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public final class GroovyTextDocumentServiceTest {

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private GroovyTextDocumentService service;
    private Path filePath;
    private List<PublishDiagnosticsParams> publishedDiagnostics = Lists.newArrayList();
    private Set<PublishDiagnosticsParams> expectedDiagnostics;
    private Map<URI, Set<SymbolInformation>> symbolsMap = Maps.newHashMap();
    private Set<SymbolInformation> expectedReferences = Sets.newHashSet();

    @Mock
    private CompilerWrapper compilerWrapper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        filePath = workspace.newFile("something.groovy").toPath();
        expectedDiagnostics =
                Sets.newHashSet(new PublishDiagnosticsParamsBuilder().uri("foo")
                        .diagnostic(new DefaultDiagnosticBuilder("Some message", DiagnosticSeverity.Error)
                                .source(filePath.toString()).build())
                        .diagnostic(new DefaultDiagnosticBuilder("Some other message", DiagnosticSeverity.Warning)
                                .source(filePath.toString()).build())
                        .build());

        SymbolInformation symbol1 = new SymbolInformationBuilder().name("ThisIsASymbol").kind(SymbolKind.Field).build();
        SymbolInformation symbol2 = new SymbolInformationBuilder().name("methodA").kind(SymbolKind.Method).build();
        symbolsMap.put(filePath.toUri(), Sets.newHashSet(symbol1, symbol2));

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
        when(compilerWrapper.getWorkspaceRoot()).thenReturn(workspace.getRoot().toPath());
        when(compilerWrapper.compile()).thenReturn(expectedDiagnostics);
        when(compilerWrapper.getFileSymbols()).thenReturn(symbolsMap);
        when(compilerWrapper.findReferences(Mockito.any())).thenReturn(allReferencesReturned);

        LanguageServerState state = new GroovyLanguageServerState();
        state.setCompilerWrapper(compilerWrapper);

        service = new GroovyTextDocumentService(state);

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
                .uri(filePath.toAbsolutePath().toString())
                .languageId("groovy")
                .version(1)
                .text("something")
                .build();
        service.didOpen(new DidOpenTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0)));
    }

    @Test
    public void testDidChange() {
        service.didChange(new DidChangeTextDocumentParamsBuilder()
                .contentChange(Ranges.createRange(0, 0, 1, 1), 3, "Hello")
                .textDocument((VersionedTextDocumentIdentifier) new VersionedTextDocumentIdentifierBuilder()
                        .version(0)
                        .uri(filePath.toAbsolutePath().toString())
                        .build())
                .build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0)));
    }

    @Test
    public void testDidChange_noChanges() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
                String.format("Calling didChange with no changes on uri '%s'", filePath.toUri()));
        service.didChange(new DidChangeTextDocumentParamsBuilder()
                .textDocument((VersionedTextDocumentIdentifier) new VersionedTextDocumentIdentifierBuilder()
                        .version(0)
                        .uri(filePath.toAbsolutePath().toString())
                        .build())
                .build());
    }

    @Test
    public void testDidChange_nonExistantUri() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException
                .expectMessage(String.format("Uri '%s' does not exist", filePath.toUri() + "boo"));
        service.didChange(new DidChangeTextDocumentParamsBuilder()
                .textDocument((VersionedTextDocumentIdentifier) new VersionedTextDocumentIdentifierBuilder()
                        .version(0)
                        .uri(filePath.toAbsolutePath().toString() + "boo")
                        .build())
                .build());
    }

    @Test
    public void testDidClose() {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(filePath.toAbsolutePath().toString())
                .build();
        service.didClose(new DidCloseTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0)));
    }

    @Test
    public void testDidClose_nonExistantUri() {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(filePath.toAbsolutePath().toString() + "boo")
                .build();
        expectedException.expect(IllegalArgumentException.class);
        expectedException
                .expectMessage(String.format("Uri '%s' does not exist", filePath.toUri() + "boo"));
        service.didClose(new DidCloseTextDocumentParamsBuilder().textDocument(textDocument).build());
    }

    @Test
    public void testDidSave() {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(filePath.toAbsolutePath().toString())
                .build();
        service.didSave(new DidSaveTextDocumentParamsBuilder().textDocument(textDocument).build());
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0)));
    }

    @Test
    public void testDidSave_nonExistantUri() {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(filePath.toAbsolutePath().toString() + "boo")
                .build();
        expectedException.expect(IllegalArgumentException.class);
        expectedException
                .expectMessage(String.format("Uri '%s' does not exist", filePath.toUri() + "boo"));
        service.didSave(new DidSaveTextDocumentParamsBuilder().textDocument(textDocument).build());
    }

    @Test
    public void testDocumentSymbols_absolutePath() throws InterruptedException, ExecutionException {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(filePath.toAbsolutePath().toString())
                .build();
        CompletableFuture<List<? extends SymbolInformation>> response =
                service.documentSymbol(new DocumentSymbolParamsBuilder().textDocument(textDocument).build());
        assertThat(response.get().stream().collect(Collectors.toSet()),
                is(symbolsMap.get(filePath.toUri())));
    }

    @Test
    public void testDocumentSymbols_relativePath() throws InterruptedException, ExecutionException {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder().uri("something.groovy").build();
        CompletableFuture<List<? extends SymbolInformation>> response =
                service.documentSymbol(new DocumentSymbolParamsBuilder().textDocument(textDocument).build());
        assertThat(response.get().stream().collect(Collectors.toSet()),
                is(symbolsMap.get(filePath.toUri())));
    }

    @Test
    public void testDocumentSymbols_nonExistantUri() throws InterruptedException, ExecutionException {
        TextDocumentIdentifier textDocument = new TextDocumentIdentifierBuilder()
                .uri(filePath.toAbsolutePath().toString() + "boo")
                .build();
        expectedException.expect(IllegalArgumentException.class);
        expectedException
                .expectMessage(String.format("Uri '%s' does not exist", filePath.toUri() + "boo"));
        service.documentSymbol(new DocumentSymbolParamsBuilder().textDocument(textDocument).build());
    }

    @Test
    public void testReferences() throws InterruptedException, ExecutionException {
        // HACK, blocked on https://github.com/TypeFox/ls-api/issues/39
        ReferenceParams params = (ReferenceParams) new ReferenceParamsBuilder()
                .context(false)
                .position(5, 5)
                .textDocument("uri")
                .uri("uri")
                .build();
        CompletableFuture<List<? extends Location>> response = service.references(params);
        assertThat(response.get().stream().collect(Collectors.toSet()),
                is(expectedReferences.stream().map(symbol -> symbol.getLocation()).collect(Collectors.toSet())));
    }

    @Test
    public void testCompletion() throws InterruptedException, ExecutionException {
        String uri = filePath.toAbsolutePath().toString();
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
        String uri = workspace.getRoot().toPath().resolve("somethingthatdoesntexist.groovy").toString();
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
