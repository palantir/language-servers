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

import com.google.common.collect.Maps;
import com.palantir.ls.api.CompilerWrapper;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.ShowMessageRequestParams;
import io.typefox.lsapi.builders.PublishDiagnosticsParamsBuilder;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class GroovyLanguageServerConfig implements LanguageServerConfig {

    private CompilerWrapper compilerWrapper = null;
    private Consumer<MessageParams> showMessage = m -> { };
    private Consumer<ShowMessageRequestParams> showMessageRequest = m -> { };
    private Consumer<MessageParams> logMessage = m -> { };
    private Consumer<Object> telemetryEvent = e -> { };
    private Consumer<PublishDiagnosticsParams> publishDiagnostics = p -> { };

    @Override
    public CompilerWrapper getCompilerWrapper() {
        return compilerWrapper;
    }

    @Override
    public void setCompilerWrapper(CompilerWrapper compilerWrapper) {
        this.compilerWrapper = compilerWrapper;
    }

    @Override
    public Consumer<MessageParams> getShowMessage() {
        return showMessage;
    }

    @Override
    public void setShowMessage(Consumer<MessageParams> callback) {
        this.showMessage = callback;
    }

    @Override
    public Consumer<ShowMessageRequestParams> getShowMessageRequest() {
        return showMessageRequest;
    }

    @Override
    public void setShowMessageRequest(Consumer<ShowMessageRequestParams> callback) {
        this.showMessageRequest = callback;
    }

    @Override
    public Consumer<MessageParams> getLogMessage() {
        return logMessage;
    }

    @Override
    public void setLogMessage(Consumer<MessageParams> callback) {
        this.logMessage = callback;
    }

    @Override
    public Consumer<Object> getTelemetryEvent() {
        return telemetryEvent;
    }

    @Override
    public void setTelemetryEvent(Consumer<Object> telemetryEvent) {
        this.telemetryEvent = telemetryEvent;
    }

    @Override
    public void setPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback) {
        this.publishDiagnostics = callback;
    }

    @Override
    public void publishDiagnostics(Path workspaceRoot, Set<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return;
        }
        Map<URI, PublishDiagnosticsParamsBuilder> diagnosticsByFile = Maps.newHashMap();

        diagnostics.forEach(diagnostic -> {
            try {
                URI uri = Paths.get(diagnostic.getSource()).toUri();
                diagnosticsByFile
                        .computeIfAbsent(uri, (value) -> new PublishDiagnosticsParamsBuilder().uri(uri.toString()))
                        .diagnostic(diagnostic);
            } catch (IllegalArgumentException e) {
                // The compiler can give errors not associated with a particular source file, in which case we put it
                // under the workspace uri.
                diagnosticsByFile.computeIfAbsent(workspaceRoot.toUri(),
                        (value) -> new PublishDiagnosticsParamsBuilder().uri(workspaceRoot.toUri().toString()))
                        .diagnostic(diagnostic);
            }
        });

        diagnosticsByFile.values().stream().map(paramsBuilder -> paramsBuilder.build())
                .forEach(publishDiagnostics::accept);
    }

}
