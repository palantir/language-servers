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

import io.typefox.lsapi.services.json.LanguageServerToJsonAdapter;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GroovyLanguageMain {

    private static final Logger log = LoggerFactory.getLogger(GroovyLanguageMain.class);

    private GroovyLanguageMain() {}

    public static void main(String[] args) {
        GroovyLanguageServer server = new GroovyLanguageServer(new GroovyTextDocumentService(),
                                                               new GroovyWorkspaceService(),
                                                               new GroovyWindowService());
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
