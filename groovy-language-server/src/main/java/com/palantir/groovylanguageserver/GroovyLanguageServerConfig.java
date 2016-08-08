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

import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.ShowMessageRequestParams;
import java.util.function.Consumer;

public final class GroovyLanguageServerConfig implements LanguageServerConfig {

    private Consumer<MessageParams> showMessage = m -> { };
    private Consumer<ShowMessageRequestParams> showMessageRequest = m -> { };
    private Consumer<MessageParams> logMessage = m -> { };
    private Consumer<Object> telemetryEvent = e -> { };

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

}
