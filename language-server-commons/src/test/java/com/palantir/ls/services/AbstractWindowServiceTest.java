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

package com.palantir.ls.services;

import com.palantir.ls.api.LanguageServerState;
import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.ShowMessageRequestParams;
import java.io.IOException;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AbstractWindowServiceTest {

    public static class TestWindowService extends AbstractWindowService {

        private final LanguageServerState state;

        public TestWindowService(LanguageServerState state) {
            this.state = state;
        }

        @Override
        protected LanguageServerState getState() {
            return state;
        }
    }

    @Mock
    private LanguageServerState state;

    private AbstractWindowService service;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        service = new TestWindowService(state);
    }

    @Test
    public void testOnShowMessage() {
        Consumer<MessageParams> callback = m -> { };
        service.onShowMessage(callback);
        Mockito.verify(state, Mockito.times(1)).setShowMessage(callback);
    }

    @Test
    public void testOnShowMessageRequest() {
        Consumer<ShowMessageRequestParams> callback = m -> { };
        service.onShowMessageRequest(callback);
        Mockito.verify(state, Mockito.times(1)).setShowMessageRequest(callback);
    }

    @Test
    public void testOnLogMessage() {
        Consumer<MessageParams> callback = m -> { };
        service.onLogMessage(callback);
        Mockito.verify(state, Mockito.times(1)).setLogMessage(callback);
    }

}
