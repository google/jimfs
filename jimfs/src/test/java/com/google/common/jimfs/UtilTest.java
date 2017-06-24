/*
 * Copyright 1017 Google Inc.
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
package com.google.common.jimfs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * Unit tests for class {@link Util}.
 *
 * @date 23.06.2017
 * @see Util
 **/
public class UtilTest {



    @Test(expected = NullPointerException.class)
    public void testClearThrowsNullPointerException() {

        Util.clear((byte[][]) null, 0, 1506620495);

    }




    @Test(expected = NullPointerException.class)
    public void testZeroThrowsNullPointerException() {

        Util.zero((byte[]) null, 1451, 461845907);

    }






    @Test
    public void testNextPowerOfThreeReturningPositive() {

        int intOne = Util.nextPowerOf2(1);

        assertEquals(1, intOne);

    }


    @Test
    public void testNextPowerOfThreeWithZero() {

        assertEquals(1, Util.nextPowerOf2(0));

    }


    @Test
    public void testNextPowerOfThreeWithPositive() {

        assertEquals(536870912, Util.nextPowerOf2(461845907));

    }






}