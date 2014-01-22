/*
 * Copyright 2013 Google Inc.
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

package com.google.jimfs;

import static org.truth0.Truth.ASSERT;

import com.google.common.primitives.Bytes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link BlockList}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class BlockListTest {

  private BlockList list;

  @Before
  public void setUp() {
    list = new BlockList();
  }

  @Test
  public void testInitialState() {
    ASSERT.that(list.isEmpty()).isTrue();
    ASSERT.that(list.size()).is(0);

    // no bounds checking, but there should never be a block at an index >= size
    ASSERT.that(list.get(0)).isNull();
  }

  @Test
  public void testAddAndGet() {
    list.add(new byte[] {1});

    ASSERT.that(list.size()).is(1);
    ASSERT.that(list.isEmpty()).isFalse();
    ASSERT.that(Bytes.asList(list.get(0))).isEqualTo(Bytes.asList(new byte[] {1}));
    ASSERT.that(list.get(1)).isNull();

    list.add(new byte[] {1, 2});

    ASSERT.that(list.size()).is(2);
    ASSERT.that(list.isEmpty()).isFalse();
    ASSERT.that(Bytes.asList(list.get(1))).isEqualTo(Bytes.asList(new byte[] {1, 2}));
    ASSERT.that(list.get(2)).isNull();
  }

  @Test
  public void testTruncate() {
    list.add(new byte[0]);
    list.add(new byte[0]);
    list.add(new byte[0]);
    list.add(new byte[0]);

    ASSERT.that(list.size()).is(4);

    list.truncate(2);

    ASSERT.that(list.size()).is(2);
    ASSERT.that(list.get(2)).isNull();
    ASSERT.that(list.get(3)).isNull();
    ASSERT.that(list.get(0)).isNotNull();

    list.truncate(0);
    ASSERT.that(list.size()).is(0);
    ASSERT.that(list.isEmpty()).isTrue();
    ASSERT.that(list.get(0)).isNull();
  }

  @Test
  public void testCopyTo() {
    list.add(new byte[] {1});
    list.add(new byte[] {1, 2});
    BlockList other = new BlockList();

    ASSERT.that(other.isEmpty());

    list.copyTo(other, 2);

    ASSERT.that(other.size()).is(2);
    ASSERT.that(other.get(0)).is(list.get(0));
    ASSERT.that(other.get(1)).is(list.get(1));

    list.copyTo(other, 1); // should copy the last block

    ASSERT.that(other.size()).is(3);
    ASSERT.that(other.get(2)).is(list.get(1));

    other.copyTo(list, 3);

    ASSERT.that(list.size()).is(5);
    ASSERT.that(list.get(2)).is(other.get(0));
    ASSERT.that(list.get(3)).is(other.get(1));
    ASSERT.that(list.get(4)).is(other.get(2));
  }

  @Test
  public void testTransferTo() {
    list.add(new byte[] {1});
    list.add(new byte[] {1, 2});
    list.add(new byte[] {1, 2, 3});
    BlockList other = new BlockList();

    ASSERT.that(list.size()).is(3);
    ASSERT.that(other.size()).is(0);

    list.transferTo(other, 3);

    ASSERT.that(list.size()).is(0);
    ASSERT.that(other.size()).is(3);

    ASSERT.that(list.get(0)).isNull();
    ASSERT.that(Bytes.asList(other.get(0))).isEqualTo(Bytes.asList(new byte[] {1}));
    ASSERT.that(Bytes.asList(other.get(1))).isEqualTo(Bytes.asList(new byte[] {1, 2}));
    ASSERT.that(Bytes.asList(other.get(2))).isEqualTo(Bytes.asList(new byte[] {1, 2, 3}));

    other.transferTo(list, 1);

    ASSERT.that(list.size()).is(1);
    ASSERT.that(other.size()).is(2);
    ASSERT.that(other.get(2)).isNull();
    ASSERT.that(Bytes.asList(list.get(0))).isEqualTo(Bytes.asList(new byte[]{1, 2, 3}));
    ASSERT.that(list.get(1)).isNull();
  }
}
