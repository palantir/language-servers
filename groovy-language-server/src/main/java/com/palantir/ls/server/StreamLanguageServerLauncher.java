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

package com.palantir.ls.server;

import com.palantir.ls.util.LoggerMessageTracer;
import io.typefox.lsapi.services.LanguageServer;
import io.typefox.lsapi.services.json.MessageJsonHandler;
import io.typefox.lsapi.services.json.StreamMessageReader;
import io.typefox.lsapi.services.json.StreamMessageWriter;
import io.typefox.lsapi.services.transport.io.ConcurrentMessageReader;
import io.typefox.lsapi.services.transport.io.MessageReader;
import io.typefox.lsapi.services.transport.io.MessageWriter;
import io.typefox.lsapi.services.transport.server.LanguageServerEndpoint;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;

public final class StreamLanguageServerLauncher {

    private final LanguageServerEndpoint languageServerEndpoint;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    private final ExecutorService executorService;
    private final MessageJsonHandler jsonHandler;

    public StreamLanguageServerLauncher(LanguageServer languageServer, InputStream in, OutputStream out) {
        this.languageServerEndpoint = new LanguageServerEndpoint(languageServer);
        this.inputStream = in;
        this.outputStream = out;
        this.executorService = Executors.newCachedThreadPool();
        this.jsonHandler = new MessageJsonHandler();
    }

    public void setLogger(Logger logger) {
        languageServerEndpoint.setMessageTracer(new LoggerMessageTracer(logger));
    }

    public void launch() {
        MessageReader reader = new StreamMessageReader(inputStream, jsonHandler);
        ConcurrentMessageReader concurrentReader = new ConcurrentMessageReader(reader, executorService);
        MessageWriter writer = new StreamMessageWriter(outputStream, jsonHandler);

        languageServerEndpoint.connect(concurrentReader, writer);

        concurrentReader.join();
    }
}
