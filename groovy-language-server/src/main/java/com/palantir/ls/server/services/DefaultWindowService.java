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

package com.palantir.ls.server.services;

import com.palantir.ls.server.api.LanguageServerState;
import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.ShowMessageRequestParams;
import io.typefox.lsapi.services.WindowService;
import java.util.function.Consumer;

public final class DefaultWindowService implements WindowService {

    private final LanguageServerState state;

    public DefaultWindowService(LanguageServerState state) {
        this.state = state;
    }

    @Override
    public void onShowMessage(Consumer<MessageParams> callback) {
        state.setShowMessage(callback);
    }

    @Override
    public void onShowMessageRequest(Consumer<ShowMessageRequestParams> callback) {
        state.setShowMessageRequest(callback);
    }

    @Override
    public void onLogMessage(Consumer<MessageParams> callback) {
        state.setLogMessage(callback);
    }

}
