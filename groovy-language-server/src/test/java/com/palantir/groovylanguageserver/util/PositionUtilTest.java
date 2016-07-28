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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.typefox.lsapi.util.LsapiFactories;

public final class PositionUtilTest {

    public void testContains() {
        assertTrue(PositionUtil.contains(PositionUtil.createRange(4, 4, 4, 4), LsapiFactories.newPosition(4, 4)));
        assertTrue(PositionUtil.contains(PositionUtil.createRange(4, 4, 6, 6), LsapiFactories.newPosition(4, 4)));
        assertTrue(PositionUtil.contains(PositionUtil.createRange(4, 4, 6, 6), LsapiFactories.newPosition(6, 6)));
        assertTrue(PositionUtil.contains(PositionUtil.createRange(4, 4, 6, 6), LsapiFactories.newPosition(5, 5)));
        assertFalse(PositionUtil.contains(PositionUtil.createRange(4, 4, 6, 6), LsapiFactories.newPosition(4, 3)));
        assertFalse(PositionUtil.contains(PositionUtil.createRange(4, 4, 6, 6), LsapiFactories.newPosition(6, 7)));
    }

}
