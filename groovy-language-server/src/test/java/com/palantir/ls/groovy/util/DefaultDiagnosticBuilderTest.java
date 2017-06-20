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

package com.palantir.ls.groovy.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.ls.util.Ranges;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DefaultDiagnosticBuilderTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @SuppressFBWarnings("NP_NULL_PARAM_DEREF")
    @Test
    public void testExceptionOnNullMessage() {
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("message cannot be null");
        DefaultDiagnosticBuilder.of(null, DiagnosticSeverity.Error);
    }

    @Test
    public void testDefaults() {
        Diagnostic diagnostic = DefaultDiagnosticBuilder.of("message", DiagnosticSeverity.Error);
        assertThat(diagnostic.getMessage(), is("message"));
        assertThat(diagnostic.getSeverity(), is(DiagnosticSeverity.Error));
        assertThat(diagnostic.getRange(), is(Ranges.UNDEFINED_RANGE));
        assertThat(diagnostic.getSource(), is("groovyc"));
    }
}
