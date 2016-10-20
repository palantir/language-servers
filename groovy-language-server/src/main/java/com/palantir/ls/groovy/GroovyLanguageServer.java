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

import com.google.common.io.Files;
import com.palantir.ls.DefaultCompilerWrapper;
import com.palantir.ls.DefaultLanguageServerState;
import com.palantir.ls.StreamLanguageServerLauncher;
import com.palantir.ls.api.LanguageServerState;
import com.palantir.ls.api.TreeParser;
import com.palantir.ls.services.DefaultTextDocumentService;
import com.palantir.ls.services.DefaultWindowService;
import com.palantir.ls.services.DefaultWorkspaceService;
import com.palantir.ls.util.Uris;
import com.palantir.ls.util.WorkspaceUriSupplier;
import io.typefox.lsapi.InitializeParams;
import io.typefox.lsapi.InitializeResult;
import io.typefox.lsapi.ServerCapabilities;
import io.typefox.lsapi.TextDocumentSyncKind;
import io.typefox.lsapi.builders.CompletionOptionsBuilder;
import io.typefox.lsapi.builders.InitializeResultBuilder;
import io.typefox.lsapi.builders.ServerCapabilitiesBuilder;
import io.typefox.lsapi.services.LanguageServer;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroovyLanguageServer implements LanguageServer {

    private static final Logger logger = LoggerFactory.getLogger(GroovyLanguageServer.class);

    private final LanguageServerState state;
    private final TextDocumentService textDocumentService;
    private final WorkspaceService workspaceService;
    private final WindowService windowService;

    private Path workspaceRoot;
    private Path targetDirectory;
    private Path changedFilesDirectory;

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
                .definitionProvider(true)
                .build();
        InitializeResult result = new InitializeResultBuilder()
                .capabilities(capabilities)
                .build();

        targetDirectory = Files.createTempDir().toPath();
        changedFilesDirectory = Files.createTempDir().toPath();

        GroovyWorkspaceCompiler compiler =
                GroovyWorkspaceCompiler.of(targetDirectory, workspaceRoot, changedFilesDirectory);
        TreeParser parser =
                GroovyTreeParser.of(compiler, workspaceRoot,
                        new WorkspaceUriSupplier(workspaceRoot, changedFilesDirectory));
        DefaultCompilerWrapper groovycWrapper = new DefaultCompilerWrapper(compiler, parser);
        state.setCompilerWrapper(groovycWrapper);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void shutdown() {
        deleteDirectory(targetDirectory.toFile());
        deleteDirectory(changedFilesDirectory.toFile());
    }

    private static void deleteDirectory(File directory) {
        try {
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            logger.error("Could not delete directory '" + directory.toString() + "'", e);
        }
    }

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
