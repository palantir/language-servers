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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.palantir.groovylanguageserver.util.DiagnosticBuilder;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticImpl;
import io.typefox.lsapi.DidChangeTextDocumentParamsImpl;
import io.typefox.lsapi.DidCloseTextDocumentParamsImpl;
import io.typefox.lsapi.DidOpenTextDocumentParamsImpl;
import io.typefox.lsapi.DidSaveTextDocumentParamsImpl;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.TextDocumentIdentifierImpl;
import io.typefox.lsapi.TextDocumentItemImpl;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class GroovyTextDocumentServiceTest {

    private GroovyTextDocumentService service;
    private List<PublishDiagnosticsParams> publishedDiagnostics = Lists.newArrayList();
    private Set<DiagnosticImpl> expectedDiagnostics = Sets.newHashSet();

    @Mock
    private CompilerWrapper compilerWrapper;
    @Mock
    private CompilerWrapperProvider provider;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        expectedDiagnostics.add(new DiagnosticBuilder("Some message", Diagnostic.SEVERITY_ERROR).build());
        List<DiagnosticImpl> diagnostics = Lists.newArrayList(expectedDiagnostics);

        when(compilerWrapper.getWorkspaceRoot()).thenReturn(Paths.get("some/path/that/may/exist"));
        when(compilerWrapper.compile()).thenReturn(diagnostics);

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
        textDocument.setUri("some/path/that/may/exists/something.groovy");
        textDocument.setLanguageId("groovy");
        textDocument.setVersion(1);
        textDocument.setText("something");
        params.setTextDocument(textDocument);
        service.didOpen(params);
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(Paths.get("some/path/that/may/exist").toAbsolutePath().toString(),
                publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidChange() {
        DidChangeTextDocumentParamsImpl params = new DidChangeTextDocumentParamsImpl();
        params.setUri("some/path/that/may/exists/something.groovy");
        service.didChange(params);
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(Paths.get("some/path/that/may/exist").toAbsolutePath().toString(),
                publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidClose() {
        DidCloseTextDocumentParamsImpl params = new DidCloseTextDocumentParamsImpl();
        TextDocumentIdentifierImpl textDocument = new TextDocumentIdentifierImpl();
        textDocument.setUri("some/path/that/may/exists/something.groovy");
        params.setTextDocument(textDocument);
        service.didClose(params);
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(Paths.get("some/path/that/may/exist").toAbsolutePath().toString(),
                publishedDiagnostics.get(0).getUri());
    }

    @Test
    public void testDidSave() {
        DidSaveTextDocumentParamsImpl params = new DidSaveTextDocumentParamsImpl();
        TextDocumentIdentifierImpl textDocument = new TextDocumentIdentifierImpl();
        textDocument.setUri("some/path/that/may/exists/something.groovy");
        params.setTextDocument(textDocument);
        service.didSave(params);
        // assert diagnostics were published
        assertEquals(1, publishedDiagnostics.size());
        assertEquals(expectedDiagnostics, Sets.newHashSet(publishedDiagnostics.get(0).getDiagnostics()));
        assertEquals(Paths.get("some/path/that/may/exist").toAbsolutePath().toString(),
                publishedDiagnostics.get(0).getUri());
    }

}
