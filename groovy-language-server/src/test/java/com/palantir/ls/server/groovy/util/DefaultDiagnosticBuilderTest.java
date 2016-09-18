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
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.Range;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class DefaultDiagnosticBuilderTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @SuppressFBWarnings("NP_NULL_PARAM_DEREF")
    @Test
    public void testExceptionOnNullMessage() {
        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage("message cannot be null");
        new DefaultDiagnosticBuilder(null, DiagnosticSeverity.Error).build();
    }

    @Test
    public void testDefaults() {
        Diagnostic diagnostic = new DefaultDiagnosticBuilder("message", DiagnosticSeverity.Error).build();
        assertThat(diagnostic.getMessage(), is("message"));
        assertThat(diagnostic.getSeverity(), is(DiagnosticSeverity.Error));
        assertThat(diagnostic.getRange(), is(Ranges.UNDEFINED_RANGE));
        assertThat(diagnostic.getSource(), is("groovyc"));
    }

    @Test
    public void testOverrideDefaults() {
        Range expectedRange = Ranges.createRange(0, 0, 1, 1);
        Diagnostic diagnostic = new DefaultDiagnosticBuilder("message", DiagnosticSeverity.Error)
                .range(expectedRange)
                .source("foo")
                .build();
        assertThat(diagnostic.getMessage(), is("message"));
        assertThat(diagnostic.getSeverity(), is(DiagnosticSeverity.Error));
        assertThat(diagnostic.getRange(), is(expectedRange));
        assertThat(diagnostic.getSource(), is("foo"));
    }

}
