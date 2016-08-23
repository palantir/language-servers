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

import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.ShowMessageRequestParams;
import java.util.function.Consumer;

/**
 * Used to share message callbacks between Language Server services.
 */
public interface LanguageServerConfig {

    Consumer<MessageParams> getShowMessage();

    void setShowMessage(Consumer<MessageParams> callback);

    Consumer<ShowMessageRequestParams> getShowMessageRequest();

    void setShowMessageRequest(Consumer<ShowMessageRequestParams> callback);

    Consumer<MessageParams> getLogMessage();

    void setLogMessage(Consumer<MessageParams> callback);

    Consumer<Object> getTelemetryEvent();

    void setTelemetryEvent(Consumer<Object> callback);

    Consumer<PublishDiagnosticsParams> getPublishDiagnostics();

    void setPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback);

}
