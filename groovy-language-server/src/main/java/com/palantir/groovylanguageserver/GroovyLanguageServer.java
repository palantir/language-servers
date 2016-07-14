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

import io.typefox.lsapi.InitializeParams;
import io.typefox.lsapi.InitializeResult;
import io.typefox.lsapi.InitializeResultImpl;
import io.typefox.lsapi.ServerCapabilities;
import io.typefox.lsapi.ServerCapabilitiesImpl;
import io.typefox.lsapi.services.LanguageServer;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;
import io.typefox.lsapi.services.json.LanguageServerToJsonAdapter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GroovyLanguageServer implements LanguageServer {

    private static final Logger log = LoggerFactory.getLogger(GroovyLanguageServer.class);

    private final CompilerWrapperProvider provider;
    private final LanguageServerConfig config;
    private final TextDocumentService textDocumentService;
    private final WorkspaceService workspaceService;
    private final WindowService windowService;

    private Path workspaceRoot;

    public GroovyLanguageServer(CompilerWrapperProvider provider, LanguageServerConfig config,
            TextDocumentService textDocumentService, WorkspaceService workspaceService, WindowService windowService) {
        this.provider = provider;
        this.config = config;
        this.textDocumentService = textDocumentService;
        this.workspaceService = workspaceService;
        this.windowService = windowService;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workspaceRoot = Paths.get(params.getRootPath()).toAbsolutePath().normalize();

        ServerCapabilitiesImpl capabilities = new ServerCapabilitiesImpl();

        capabilities.setTextDocumentSync(ServerCapabilities.SYNC_INCREMENTAL);

        InitializeResultImpl result = new InitializeResultImpl();
        result.setCapabilities(capabilities);

        GroovycWrapper groovycWrapper = new GroovycWrapper(workspaceRoot);
        provider.set(groovycWrapper);

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

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public static void main(String[] args) {
        CompilerWrapperProvider provider = new SingleCompilerWrapperProvider();
        LanguageServerConfig config = new GroovyLanguageServerConfig();
        GroovyLanguageServer server =
                new GroovyLanguageServer(provider, config, new GroovyTextDocumentService(provider, config),
                        new GroovyWorkspaceService(provider), new GroovyWindowService(config));

        LanguageServerToJsonAdapter adapter = new LanguageServerToJsonAdapter(server);
        adapter.connect(System.in, System.out);
        adapter.getProtocol().addErrorListener((message, err) -> log.error(message, err));

        try {
            adapter.join();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
