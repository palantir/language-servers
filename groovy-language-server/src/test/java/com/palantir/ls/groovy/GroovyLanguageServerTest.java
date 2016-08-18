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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import io.typefox.lsapi.InitializeParams;
import io.typefox.lsapi.InitializeResult;
import io.typefox.lsapi.TextDocumentSyncKind;
import io.typefox.lsapi.builders.InitializeParamsBuilder;
import io.typefox.lsapi.impl.ClientCapabilitiesImpl;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public final class GroovyLanguageServerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private GroovyLanguageServer server;

    @Before
    public void before() {
        server = new GroovyLanguageServer(new CompilerWrapperProvider() {
            @Override
            public void set(CompilerWrapper compilerWrapper) {}

            @Override
            public CompilerWrapper get() {
                return Mockito.mock(CompilerWrapper.class);
            }
        }, Mockito.mock(LanguageServerConfig.class), Mockito.mock(TextDocumentService.class),
                Mockito.mock(WorkspaceService.class), Mockito.mock(WindowService.class));
    }

    @Test
    public void testInitialize_absoluteWorkspacePath() throws InterruptedException, ExecutionException {
        InitializeParams params =
                new InitializeParamsBuilder().capabilities(new ClientCapabilitiesImpl()).processId(1)
                        .rootPath(folder.getRoot().toPath().toAbsolutePath().toString()).build();
        InitializeResult result = server.initialize(params).get();
        assertInitializeResultIsCorrect(folder.getRoot().toPath().toAbsolutePath().normalize(), result);

        // Test normalization
        params = new InitializeParamsBuilder().capabilities(new ClientCapabilitiesImpl()).processId(1)
                        .rootPath(folder.getRoot().toPath().toAbsolutePath().toString() + "/somethingelse/..").build();
        result = server.initialize(params).get();
        assertInitializeResultIsCorrect(folder.getRoot().toPath().toAbsolutePath().normalize(), result);
    }

    @Test
    public void testInitialize_relativeWorkspacePath() throws InterruptedException, ExecutionException, IOException {
        File workspaceRoot = Paths.get("").toAbsolutePath().resolve("something").toFile();
        // Create a directory in our working directory
        assertTrue(workspaceRoot.mkdir());

        InitializeParams params =
                new InitializeParamsBuilder().capabilities(new ClientCapabilitiesImpl()).processId(1)
                        .rootPath("something").build();
        InitializeResult result = server.initialize(params).get();
        assertInitializeResultIsCorrect(workspaceRoot.toPath(), result);

        // Test normalization
        params = new InitializeParamsBuilder().capabilities(new ClientCapabilitiesImpl()).processId(1)
                        .rootPath("./something").build();
        result = server.initialize(params).get();
        assertInitializeResultIsCorrect(workspaceRoot.toPath(), result);

        params = new InitializeParamsBuilder().capabilities(new ClientCapabilitiesImpl()).processId(1)
                        .rootPath("somethingelse/../something/../something").build();
        result = server.initialize(params).get();
        assertInitializeResultIsCorrect(workspaceRoot.toPath(), result);

        // Delete the directory we created in our working directory
        assertTrue(workspaceRoot.delete());
    }

    private void assertInitializeResultIsCorrect(Path expectedWorkspaceRoot, InitializeResult result) {
        assertThat(server.getWorkspaceRoot(), is(expectedWorkspaceRoot));
        assertThat(result.getCapabilities().getTextDocumentSync(), is(TextDocumentSyncKind.Incremental));
        assertTrue(result.getCapabilities().isDocumentSymbolProvider());
        assertTrue(result.getCapabilities().isWorkspaceSymbolProvider());
    }

}
