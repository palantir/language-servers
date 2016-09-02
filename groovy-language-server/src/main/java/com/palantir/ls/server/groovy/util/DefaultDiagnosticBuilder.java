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

package com.palantir.ls.server.groovy.util;

import static com.google.common.base.Preconditions.checkNotNull;

import com.palantir.ls.server.util.Ranges;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.builders.DiagnosticBuilder;

public final class DefaultDiagnosticBuilder extends DiagnosticBuilder {

    public DefaultDiagnosticBuilder(String message, DiagnosticSeverity severity) {
        checkNotNull(message, "message cannot be null");
        this.message(message);
        this.severity(severity);
        this.range(Ranges.UNDEFINED_RANGE);
        this.source("groovyc");
    }

}
