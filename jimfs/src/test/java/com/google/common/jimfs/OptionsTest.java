/*
 * Copyright 2017 Google Inc.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.junit.Test;

import java.nio.file.*;

import static org.junit.Assert.*;

/**
 * Unit tests for class {@link Options}.
 *
 * @date 24.06.2017
 * @see Options
 **/
public class OptionsTest {


    @Test
    public void testGetOptionsForInputStream() {

        LinkOption linkOption = LinkOption.NOFOLLOW_LINKS;
        OpenOption[] openOptionArray = new OpenOption[5];
        openOptionArray[0] = linkOption;
        openOptionArray[1] = linkOption;
        openOptionArray[2] = linkOption;
        openOptionArray[3] = linkOption;
        openOptionArray[4] = linkOption;
        ImmutableSet<OpenOption> immutableSet = Options.getOptionsForInputStream(openOptionArray);

        assertEquals(1, immutableSet.size());

    }

    @Test
    public void testGetOptionsForInputStreamThrowsUnsupportedOperationException() {

        StandardOpenOption[] standardOpenOptionArray = StandardOpenOption.values();

        try {
            Options.getOptionsForInputStream(standardOpenOptionArray);
            fail("Expecting exception: UnsupportedOperationException");
        } catch(UnsupportedOperationException e) {
            assertEquals("'WRITE' not allowed",e.getMessage());
            assertEquals("com.google.common.jimfs.Options", e.getStackTrace()[0].getClassName());
        }

    }

    @Test
    public void testGetOptionsForChannelThrowsUnsupportedOperationException() {

        StandardOpenOption[] standardOpenOptionArray = new StandardOpenOption[5];
        StandardOpenOption standardOpenOption = StandardOpenOption.READ;
        standardOpenOptionArray[0] = standardOpenOption;
        StandardOpenOption standardOpenOptionTwo = StandardOpenOption.APPEND;
        standardOpenOptionArray[1] = standardOpenOptionTwo;
        standardOpenOptionArray[2] = standardOpenOption;
        standardOpenOptionArray[3] = standardOpenOptionTwo;
        standardOpenOptionArray[4] = standardOpenOptionArray[0];
        ImmutableSet<StandardOpenOption> immutableSet = ImmutableSet.copyOf(standardOpenOptionArray);

        try {
            Options.getOptionsForChannel(immutableSet);
            fail("Expecting exception: UnsupportedOperationException");
        } catch(UnsupportedOperationException e) {
            assertEquals("'READ' + 'APPEND' not allowed",e.getMessage());
            assertEquals("com.google.common.jimfs.Options", e.getStackTrace()[0].getClassName());
        }

    }

    @Test
    public void testGetOptionsForChannel() {

        StandardOpenOption standardOpenOption = StandardOpenOption.DSYNC;
        StandardOpenOption standardOpenOptionTwo = StandardOpenOption.APPEND;
        ImmutableSortedSet<StandardOpenOption> immutableSortedSet = ImmutableSortedSet.of(standardOpenOption, standardOpenOptionTwo, standardOpenOption, standardOpenOption);
        ImmutableSet<OpenOption> immutableSet = Options.getOptionsForChannel(immutableSortedSet);

        assertEquals(2, immutableSortedSet.size());
        assertEquals(3, immutableSet.size());

    }


}