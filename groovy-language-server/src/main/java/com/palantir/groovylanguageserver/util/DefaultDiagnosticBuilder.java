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

package com.palantir.groovylanguageserver.util;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.builders.DiagnosticBuilder;

public final class DefaultDiagnosticBuilder {

    private final String message;
    private final DiagnosticSeverity severity;

    private Optional<String> code = Optional.absent();
    private Optional<Range> range = Optional.absent();
    private Optional<String> source = Optional.absent();

    public DefaultDiagnosticBuilder(String message, DiagnosticSeverity warning) {
        Preconditions.checkNotNull(message, "message cannot be null");
        this.message = message;
        this.severity = warning;
    }

    public DefaultDiagnosticBuilder code(String codeStr) {
        this.code = Optional.fromNullable(codeStr);
        return this;
    }

    public DefaultDiagnosticBuilder range(Range rangeVal) {
        this.range = Optional.fromNullable(rangeVal);
        return this;
    }

    public DefaultDiagnosticBuilder source(String sourceStr) {
        this.source = Optional.fromNullable(sourceStr);
        return this;
    }

    public Diagnostic build() {
        DiagnosticBuilder builder = new DiagnosticBuilder()
                                    .message(message)
                                    .severity(severity)
                                    .range(range.or(Ranges.createRange(-1, -1, -1, -1)))
                                    .source(source.or("groovyc"));
        if (code.isPresent()) {
            builder.code(code.get());
        }
        return builder.build();
    }

}
