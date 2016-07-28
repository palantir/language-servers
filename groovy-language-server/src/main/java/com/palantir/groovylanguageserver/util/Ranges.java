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

import com.google.common.base.Preconditions;
import io.typefox.lsapi.Position;
import io.typefox.lsapi.PositionImpl;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.RangeImpl;
import io.typefox.lsapi.util.LsapiFactories;

public final class Ranges {

    private Ranges() {}

    /**
     * Returns a newly created range.
     */
    public static RangeImpl createRange(int startLine, int startColumn, int endLine, int endColumn) {
        PositionImpl start = LsapiFactories.newPosition(startLine, startColumn);
        PositionImpl end = LsapiFactories.newPosition(endLine, endColumn);
        return LsapiFactories.newRange(start, end);
    }

    /**
     * Checks whether the given range is valid, i.e its start is before or equal to its end.
     */
    public static boolean isValid(Range range) {
        return isValid(range.getStart()) && isValid(range.getEnd()) && compareTo(range.getStart(), range.getEnd()) <= 0;
    }

    /**
     * Checks whether the given position is valid, i.e. it has non-negative line and character values.
     */
    public static boolean isValid(Position position) {
        return position.getLine() >= 0 && position.getCharacter() >= 0;
    }

    /**
     * Compares position1 to position2. If they are the same, returns 0. If position1 is before position2, returns a
     * negative number. If position1 is after position2, returns a positive number.
     */
    public static int compareTo(Position position1, Position position2) {
        if (position1.getLine() < position2.getLine()) {
            return position1.getLine() - position2.getLine();
        }
        if (position1.getLine() > position2.getLine()) {
            return position1.getLine() - position2.getLine();
        }
        // The two positions are on the same line
        if (position1.getCharacter() < position2.getCharacter()) {
            return position1.getCharacter() - position2.getCharacter();
        }
        if (position1.getCharacter() > position2.getCharacter()) {
            return position1.getCharacter() - position2.getCharacter();
        }
        return 0;
    }

    /**
     * Returns whether the given range contains the given position.
     */
    public static boolean contains(Range range, Position position) {
        Preconditions.checkArgument(isValid(range), String.format("range is not valid: %s", range.toString()));
        Preconditions.checkArgument(isValid(position), String.format("position is not valid: %s", position.toString()));

        return compareTo(range.getStart(), position) <= 0 && compareTo(range.getEnd(), position) >= 0;
    }

}
