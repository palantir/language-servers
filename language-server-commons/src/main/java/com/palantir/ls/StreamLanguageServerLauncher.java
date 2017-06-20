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

package com.palantir.ls;

import java.io.InputStream;
import java.io.OutputStream;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

public class StreamLanguageServerLauncher {

    private final InputStream inputStream;
    private final OutputStream outputStream;

    private final LanguageServer languageServer;

    public StreamLanguageServerLauncher(LanguageServer languageServer, InputStream in, OutputStream out) {
        this.languageServer = languageServer;
        this.inputStream = in;
        this.outputStream = out;
    }

    public void launch() {
        Launcher<LanguageClient> serverLauncher =
                LSPLauncher.createServerLauncher(languageServer, inputStream, outputStream);
        serverLauncher.startListening();
    }
}
