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

import com.google.common.io.Files;
import com.palantir.ls.server.DefaultCompilerWrapper;
import com.palantir.ls.server.DefaultLanguageServerState;
import com.palantir.ls.server.StreamLanguageServerLauncher;
import com.palantir.ls.server.api.LanguageServerState;
import com.palantir.ls.server.api.TreeParser;
import com.palantir.ls.server.groovy.util.GroovyConstants;
import com.palantir.ls.server.services.DefaultTextDocumentService;
import com.palantir.ls.server.services.DefaultWindowService;
import com.palantir.ls.server.services.DefaultWorkspaceService;
import com.palantir.ls.server.util.Uris;
import com.palantir.ls.server.util.WorkspaceUriSupplier;
import io.typefox.lsapi.InitializeParams;
import io.typefox.lsapi.InitializeResult;
import io.typefox.lsapi.LanguageDescription;
import io.typefox.lsapi.ServerCapabilities;
import io.typefox.lsapi.TextDocumentSyncKind;
import io.typefox.lsapi.builders.CompletionOptionsBuilder;
import io.typefox.lsapi.builders.InitializeResultBuilder;
import io.typefox.lsapi.builders.LanguageDescriptionBuilder;
import io.typefox.lsapi.builders.ServerCapabilitiesBuilder;
import io.typefox.lsapi.services.LanguageServer;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GroovyLanguageServer implements LanguageServer {

    private static final Logger logger = LoggerFactory.getLogger(GroovyLanguageServer.class);

    private final LanguageServerState state;
    private final TextDocumentService textDocumentService;
    private final WorkspaceService workspaceService;
    private final WindowService windowService;

    private Path workspaceRoot;

    public GroovyLanguageServer(LanguageServerState state, TextDocumentService textDocumentService,
            WorkspaceService workspaceService, WindowService windowService) {
        this.state = state;
        this.textDocumentService = textDocumentService;
        this.workspaceService = workspaceService;
        this.windowService = windowService;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        logger.debug("Initializing Groovy Language Server");
        workspaceRoot = Uris.getAbsolutePath(params.getRootPath());
        logger.debug("Resolve workspace root from '{}' to '{}'", params.getRootPath(), workspaceRoot);

        ServerCapabilities capabilities = new ServerCapabilitiesBuilder()
                .textDocumentSync(TextDocumentSyncKind.Incremental)
                .documentSymbolProvider(true)
                .workspaceSymbolProvider(true)
                .referencesProvider(true)
                .completionProvider(new CompletionOptionsBuilder()
                        .resolveProvider(false)
                        .triggerCharacter(".")
                        .build())
                .build();
        LanguageDescription languageDescription = new LanguageDescriptionBuilder()
                .languageId(GroovyConstants.GROOVY_LANGUAGE_NAME)
                .fileExtension(GroovyConstants.GROOVY_LANGUAGE_EXTENSION)
                .build();
        InitializeResult result = new InitializeResultBuilder()
                .capabilities(capabilities)
                .supportedLanguage(languageDescription)
                .build();

        Path changedFilesDirectory = Files.createTempDir().toPath();

        GroovyWorkspaceCompiler compiler =
                GroovyWorkspaceCompiler.of(Files.createTempDir().toPath(), workspaceRoot, changedFilesDirectory);
        TreeParser parser =
                GroovyTreeParser.of(compiler, workspaceRoot,
                        new WorkspaceUriSupplier(workspaceRoot, changedFilesDirectory));
        DefaultCompilerWrapper groovycWrapper = new DefaultCompilerWrapper(compiler, parser);
        state.setCompilerWrapper(groovycWrapper);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void shutdown() {}

    @Override
    public void exit() {}

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public WindowService getWindowService() {
        return windowService;
    }

    @Override
    public void onTelemetryEvent(Consumer<Object> callback) {
        state.setTelemetryEvent(callback);
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public static void main(String[] args) {
        LanguageServerState state = new DefaultLanguageServerState();
        LanguageServer server =
                new GroovyLanguageServer(state, new DefaultTextDocumentService(state),
                        new DefaultWorkspaceService(state), new DefaultWindowService(state));

        StreamLanguageServerLauncher launcher = new StreamLanguageServerLauncher(server, System.in, System.out);
        launcher.setLogger(logger);
        launcher.launch();
    }

}
