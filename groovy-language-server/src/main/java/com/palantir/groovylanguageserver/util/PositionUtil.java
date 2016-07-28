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

import io.typefox.lsapi.Position;
import io.typefox.lsapi.PositionImpl;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.RangeImpl;
import io.typefox.lsapi.util.LsapiFactories;

public final class PositionUtil {

    private PositionUtil() {}

    public static RangeImpl createRange(int startLine, int startColumn, int endLine, int endColumn) {
        PositionImpl start = LsapiFactories.newPosition(startLine, startColumn);
        PositionImpl end = LsapiFactories.newPosition(endLine, endColumn);
        return LsapiFactories.newRange(start, end);
    }

    /**
     * Returns whether the given range contains the given point.
     */
    public static boolean contains(Range range, Position point) {
        return range.getStart().getLine() < point.getLine() || (range.getStart().getLine() == point.getLine()
                && range.getStart().getCharacter() <= point.getCharacter());
    }

}
