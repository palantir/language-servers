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

package com.palantir.ls.server.groovy;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.palantir.ls.server.DefaultLanguageServerState;
import com.palantir.ls.server.StreamLanguageServerLauncher;
import com.palantir.ls.server.api.LanguageServerState;
import com.palantir.ls.server.groovy.util.DefaultDiagnosticBuilder;
import com.palantir.ls.server.services.DefaultTextDocumentService;
import com.palantir.ls.server.services.DefaultWindowService;
import com.palantir.ls.server.services.DefaultWorkspaceService;
import com.palantir.ls.server.util.Ranges;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.InitializeResult;
import io.typefox.lsapi.Message;
import io.typefox.lsapi.ServerCapabilities;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.TextDocumentSyncKind;
import io.typefox.lsapi.builders.CompletionOptionsBuilder;
import io.typefox.lsapi.builders.DidOpenTextDocumentParamsBuilder;
import io.typefox.lsapi.builders.DocumentSymbolParamsBuilder;
import io.typefox.lsapi.builders.InitializeParamsBuilder;
import io.typefox.lsapi.builders.ServerCapabilitiesBuilder;
import io.typefox.lsapi.builders.SymbolInformationBuilder;
import io.typefox.lsapi.builders.TextDocumentItemBuilder;
import io.typefox.lsapi.services.LanguageServer;
import io.typefox.lsapi.services.json.MessageJsonHandler;
import io.typefox.lsapi.services.json.StreamMessageReader;
import io.typefox.lsapi.services.json.StreamMessageWriter;
import io.typefox.lsapi.services.transport.client.LanguageClientEndpoint;
import io.typefox.lsapi.services.transport.io.ConcurrentMessageReader;
import io.typefox.lsapi.services.transport.io.MessageReader;
import io.typefox.lsapi.services.transport.io.MessageWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure2;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GroovyLanguageServerIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(GroovyLanguageServerIntegrationTest.class);

    @Rule
    public TemporaryFolder workspaceRoot = new TemporaryFolder();

    private LanguageClientEndpoint client;
    private List<MyPublishDiagnosticParams> publishedDiagnostics = Lists.newArrayList();

    @Before
    public void before() throws IOException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        client = new LanguageClientEndpoint(executorService);

        MessageJsonHandler jsonHandler = new MessageJsonHandler();
        jsonHandler.setMethodResolver(client);

        PipedOutputStream clientOutputStream = new PipedOutputStream();
        PipedOutputStream serverOutputStream = new PipedOutputStream();
        PipedInputStream clientInputStream = new PipedInputStream(serverOutputStream);
        PipedInputStream serverInputStream = new PipedInputStream(clientOutputStream);

        MessageReader reader =
                new ConcurrentMessageReader(new StreamMessageReader(clientInputStream, jsonHandler), executorService);
        MessageWriter writer = new StreamMessageWriter(clientOutputStream, jsonHandler);

        reader.setOnError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                System.err.println("ERROR:\n" + error);
            }
        });
        reader.setOnRead(new Procedure2<Message, String>() {
            @Override
            public void apply(Message message, String data) {
                System.err.println("READ:\n" + message.getJsonrpc() + "\n" + data);
            }
        });
        writer.setOnWrite(new Procedure2<Message, String>() {
            @Override
            public void apply(Message message, String data) {
                System.err.println("WRITE:\n" + message.getJsonrpc() + "\n" + data);
            }
        });

        client.connect(reader, writer);

        // Start Groovy language server
        createAndLaunchLanguageServer(serverInputStream, serverOutputStream);

        client.getTextDocumentService().onPublishDiagnostics((diagnosticsParams) -> {
            publishedDiagnostics.add(new MyPublishDiagnosticParams(diagnosticsParams.getUri(),
                    diagnosticsParams.getDiagnostics().stream().collect(Collectors.toSet())));
        });
    }

    private static void createAndLaunchLanguageServer(final InputStream in, final OutputStream out) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LanguageServerState state = new DefaultLanguageServerState();
                LanguageServer server =
                        new GroovyLanguageServer(state, new DefaultTextDocumentService(state),
                                new DefaultWorkspaceService(state), new DefaultWindowService(state));

                StreamLanguageServerLauncher launcher = new StreamLanguageServerLauncher(server, in, out);
                launcher.setLogger(logger);
                launcher.launch();
            }
        }).start();
    }

    @Test
    public void testInitialize() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<InitializeResult> completableResult =
                client.initialize(new InitializeParamsBuilder().clientName("natacha").processId(0)
                        .rootPath(workspaceRoot.getRoot().toPath().toUri().toString()).build());
        InitializeResult result = completableResult.get(60, TimeUnit.SECONDS);
        assertCorrectInitializeResult(result);
    }

    @Test
    public void testSymbols() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        File newFolder1 = workspaceRoot.newFolder();
        File file = addFileToFolder(newFolder1, "Coordinates.groovy",
                "class Coordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   def name = \"Natacha\"\n"
                        + "   double getAt(int idx1, int idx2) {\n"
                        + "      def someString = \"Also in symbols\"\n"
                        + "      println someString\n"
                        + "      if (idx1 == 0) latitude\n"
                        + "      else if (idx1 == 1) longitude\n"
                        + "      else throw new Exception(\"Wrong coordinate index, use 0 or 1 \")\n"
                        + "   }\n"
                        + "}\n");

        CompletableFuture<InitializeResult> completableResult =
                client.initialize(new InitializeParamsBuilder().clientName("natacha").processId(0)
                        .rootPath(workspaceRoot.getRoot().toPath().toUri().toString()).build());
        InitializeResult result = completableResult.get(60, TimeUnit.SECONDS);
        assertCorrectInitializeResult(result);

        // Send a didOpen request to trigger a compilation
        sendDidOpen(file);

        // Give it some time to compile
        Thread.sleep(2000);

        // Assert no diagnostics were published because compilation was successful
        assertEquals(Sets.newHashSet(), publishedDiagnostics.stream().collect(Collectors.toSet()));

        CompletableFuture<List<? extends SymbolInformation>> documentSymbolResult = client.getTextDocumentService()
                .documentSymbol(new DocumentSymbolParamsBuilder().textDocument(file.getAbsolutePath()).build());
        Set<SymbolInformation> actualSymbols = Sets.newHashSet(documentSymbolResult.get(60, TimeUnit.SECONDS));
        // Remove generated symbols for a saner comparison
        actualSymbols = actualSymbols.stream()
                        .filter(symbol -> Ranges.isValid(symbol.getLocation().getRange())).collect(Collectors.toSet());

        String fileUri = file.toPath().toUri().toString();
        Set<SymbolInformation> expectedResults = Sets.newHashSet(
                new SymbolInformationBuilder()
                        .name("Coordinates")
                        .kind(SymbolKind.Class)
                        .location(fileUri, Ranges.createRange(0, 0, 1, 0))
                        .build(),
                new SymbolInformationBuilder()
                        .name("getAt")
                        .containerName("Coordinates")
                        .kind(SymbolKind.Method)
                        .location(fileUri, Ranges.createRange(4, 3, 10, 4))
                        .build(),
                new SymbolInformationBuilder()
                        .name("latitude")
                        .containerName("Coordinates")
                        .kind(SymbolKind.Field)
                        .location(fileUri, Ranges.createRange(1, 3, 1, 18))
                        .build(),
                new SymbolInformationBuilder()
                        .name("longitude")
                        .containerName("Coordinates")
                        .kind(SymbolKind.Field)
                        .location(fileUri, Ranges.createRange(2, 3, 2, 19))
                        .build(),
                new SymbolInformationBuilder()
                        .name("name")
                        .containerName("Coordinates")
                        .kind(SymbolKind.Field)
                        .location(fileUri, Ranges.createRange(3, 3, 3, 23))
                        .build(),
                new SymbolInformationBuilder()
                        .name("idx1")
                        .containerName("getAt")
                        .kind(SymbolKind.Variable)
                        .location(fileUri, Ranges.createRange(4, 16, 4, 24))
                        .build(),
                new SymbolInformationBuilder()
                        .name("idx2")
                        .containerName("getAt")
                        .kind(SymbolKind.Variable)
                        .location(fileUri, Ranges.createRange(4, 26, 4, 34))
                        .build(),
                new SymbolInformationBuilder()
                        .name("someString")
                        .containerName("getAt")
                        .kind(SymbolKind.Variable)
                        .location(fileUri, Ranges.createRange(5, 10, 5, 20))
                        .build());

        assertEquals(expectedResults, actualSymbols);
    }

    @Test
    public void testDiagnosticNotification()
            throws InterruptedException, ExecutionException, TimeoutException, IOException {
        File newFolder1 = workspaceRoot.newFolder();
        File newFolder2 = workspaceRoot.newFolder();
        File test1 = addFileToFolder(newFolder1, "test1.groovy",
                "class Coordinates {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew1(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        File test2 = addFileToFolder(newFolder2, "test2.groovy",
                "class Coordinates2 {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew222(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(newFolder2, "test3.groovy",
                "class Coordinates3 {\n"
                        + "   double latitude\n"
                        + "   double longitude\n"
                        + "   double getAt(int idx) {\n"
                        + "      if (idx == 0) latitude\n"
                        + "      else if (idx == 1) longitude\n"
                        + "      else throw new ExceptionNew(\"Wrong coordinate index, use 0 or 1\")\n"
                        + "   }\n"
                        + "}\n");
        addFileToFolder(workspaceRoot.getRoot(), "test4.groovy", "class ExceptionNew {}\n");

        CompletableFuture<InitializeResult> completableResult =
                client.initialize(new InitializeParamsBuilder().clientName("natacha").processId(0)
                        .rootPath(workspaceRoot.getRoot().toPath().toUri().toString()).build());
        InitializeResult result = completableResult.get(60, TimeUnit.SECONDS);
        assertCorrectInitializeResult(result);

        // Send a didOpen request to trigger a compilation
        sendDidOpen(test1);

        // Give it some time to publish
        Thread.sleep(2000);

        Set<MyPublishDiagnosticParams> expectedDiagnosticsResult =
                Sets.newHashSet(
                        new MyPublishDiagnosticParams(test1.toPath().toUri().toString(),
                                Sets.newHashSet(new DefaultDiagnosticBuilder(
                                        "unable to resolve class ExceptionNew1 \n @ line 7, column 18.",
                                        DiagnosticSeverity.Error)
                                                .range(Ranges.createRange(6, 17, 6, 72))
                                                .source(test1.getAbsolutePath())
                                                .build())),
                        new MyPublishDiagnosticParams(test2.toPath().toUri().toString(),
                                Sets.newHashSet(new DefaultDiagnosticBuilder(
                                        "unable to resolve class ExceptionNew222 \n @ line 7, column 18.",
                                        DiagnosticSeverity.Error)
                                                .range(Ranges.createRange(6, 17, 6, 74))
                                                .source(test2.getAbsolutePath())
                                                .build())));
        assertEquals(expectedDiagnosticsResult, publishedDiagnostics.stream().collect(Collectors.toSet()));
        assertEquals(2, publishedDiagnostics.size());
    }

    private void sendDidOpen(File file) {
        client.getTextDocumentService()
                .didOpen(new DidOpenTextDocumentParamsBuilder()
                        .textDocument(
                                new TextDocumentItemBuilder()
                                        .languageId("groovy")
                                        .uri(file.getAbsolutePath())
                                        .version(0)
                                        .text("foo")
                                        .build())
                        .build());
    }

    private void assertCorrectInitializeResult(InitializeResult result) {
        ServerCapabilities expectedCapabilities = new ServerCapabilitiesBuilder()
                .textDocumentSync(TextDocumentSyncKind.Incremental)
                .documentSymbolProvider(true)
                .workspaceSymbolProvider(true)
                .referencesProvider(true)
                .completionProvider(new CompletionOptionsBuilder()
                        .resolveProvider(false)
                        .triggerCharacter(".")
                        .build())
                .definitionProvider(true)
                .build();
        assertEquals(expectedCapabilities, result.getCapabilities());
    }

    private static File addFileToFolder(File parent, String filename, String contents) throws IOException {
        File file = Files.createFile(Paths.get(parent.getAbsolutePath(), filename)).toFile();
        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.toString());
        writer.println(contents);
        writer.close();
        return file;
    }

    // This is needed because the original PublishDiagnosticParams has a list of diagnostics, and order can't be
    // predicted so we need to compare them as sets, but neither can be assume the order of the published diagnostics.
    // So instead we compare Sets of Sets.
    private static final class MyPublishDiagnosticParams {
        private final String uri;
        private final Set<Diagnostic> diagnostics;

        MyPublishDiagnosticParams(String uri, Set<Diagnostic> diagnostics) {
            this.uri = uri;
            this.diagnostics = diagnostics;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((diagnostics == null) ? 0 : diagnostics.hashCode());
            result = prime * result + ((uri == null) ? 0 : uri.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            MyPublishDiagnosticParams other = (MyPublishDiagnosticParams) obj;
            if (diagnostics == null) {
                if (other.diagnostics != null) {
                    return false;
                }
            } else if (!diagnostics.equals(other.diagnostics)) {
                return false;
            }
            if (uri == null) {
                if (other.uri != null) {
                    return false;
                }
            } else if (!uri.equals(other.uri)) {
                return false;
            }
            return true;
        }

    }

}
