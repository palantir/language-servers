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

import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import com.palantir.ls.server.api.TreeParser;
import com.palantir.ls.server.api.WorkspaceCompiler;
import com.palantir.ls.server.groovy.util.DefaultDiagnosticBuilder;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.builders.PublishDiagnosticsParamsBuilder;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public final class DefaultCompilerWrapperTest {

    @Mock
    private WorkspaceCompiler compiler;
    @Mock
    private TreeParser parser;

    private DefaultCompilerWrapper wrapper;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        wrapper = new DefaultCompilerWrapper(compiler, parser);
    }

    @Test
    public void testCompile_noDiagnostics() throws IOException {
        when(compiler.compile()).thenReturn(Sets.newHashSet());
        wrapper.compile();
        // assert parser was called
        Mockito.verify(parser, Mockito.times(1)).parseAllSymbols();
    }

    @Test
    public void testCompile_withDiagnostics() throws IOException {
        when(compiler.compile()).thenReturn(Sets.newHashSet(
                new PublishDiagnosticsParamsBuilder()
                        .uri("uri")
                        .diagnostic(new DefaultDiagnosticBuilder("name", DiagnosticSeverity.Error).build())
                        .build()));
        wrapper.compile();
        // assert parser wasn't called
        Mockito.verify(parser, Mockito.never()).parseAllSymbols();
    }

}
