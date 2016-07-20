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
import io.typefox.lsapi.DiagnosticImpl;
import io.typefox.lsapi.RangeImpl;
import io.typefox.lsapi.util.LsapiFactories;

public final class DiagnosticBuilder {
    private final String message;
    private final int severity;

    private Optional<String> code = Optional.absent();
    private Optional<RangeImpl> range = Optional.absent();
    private Optional<String> source = Optional.absent();

    public DiagnosticBuilder(String message, int severity) {
        Preconditions.checkNotNull(message, "message cannot be null");
        this.message = message;
        this.severity = severity;
    }

    public DiagnosticBuilder code(String codeStr) {
        this.code = Optional.fromNullable(codeStr);
        return this;
    }

    public DiagnosticBuilder range(RangeImpl rangeImpl) {
        this.range = Optional.fromNullable(rangeImpl);
        return this;
    }

    public DiagnosticBuilder source(String sourceStr) {
        this.source = Optional.fromNullable(sourceStr);
        return this;
    }

    public DiagnosticImpl build() {
        DiagnosticImpl diagnostic = new DiagnosticImpl();
        diagnostic.setMessage(message);
        diagnostic.setSeverity(severity);
        diagnostic.setCode(code.orNull()); // this is not required by the language server protocol
        diagnostic.setRange(range
                .or(LsapiFactories.newRange(LsapiFactories.newPosition(-1, -1), LsapiFactories.newPosition(-1, -1))));
        diagnostic.setSource(source.or("groovyc"));
        return diagnostic;
    }

}
