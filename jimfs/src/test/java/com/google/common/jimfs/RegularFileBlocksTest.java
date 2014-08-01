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

package com.google.common.jimfs;

import static com.google.common.truth.Truth.ASSERT;

import com.google.common.primitives.Bytes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the lower-level operations dealing with the blocks of a {@link RegularFile}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class RegularFileBlocksTest {

  private RegularFile file;

  @Before
  public void setUp() {
    file = createFile();
  }

  private static RegularFile createFile() {
    return RegularFile.create(-1, new HeapDisk(2, 2, 2));
  }

  @Test
  public void testInitialState() {
    ASSERT.that(file.blockCount()).is(0);

    // no bounds checking, but there should never be a block at an index >= size
    ASSERT.that(file.getBlock(0)).isNull();
  }

  @Test
  public void testAddAndGet() {
    file.addBlock(new byte[] {1});

    ASSERT.that(file.blockCount()).is(1);
    ASSERT.that(Bytes.asList(file.getBlock(0))).isEqualTo(Bytes.asList(new byte[] {1}));
    ASSERT.that(file.getBlock(1)).isNull();

    file.addBlock(new byte[] {1, 2});

    ASSERT.that(file.blockCount()).is(2);
    ASSERT.that(Bytes.asList(file.getBlock(1))).isEqualTo(Bytes.asList(new byte[] {1, 2}));
    ASSERT.that(file.getBlock(2)).isNull();
  }

  @Test
  public void testTruncate() {
    file.addBlock(new byte[0]);
    file.addBlock(new byte[0]);
    file.addBlock(new byte[0]);
    file.addBlock(new byte[0]);

    ASSERT.that(file.blockCount()).is(4);

    file.truncateBlocks(2);

    ASSERT.that(file.blockCount()).is(2);
    ASSERT.that(file.getBlock(2)).isNull();
    ASSERT.that(file.getBlock(3)).isNull();
    ASSERT.that(file.getBlock(0)).isNotNull();

    file.truncateBlocks(0);
    ASSERT.that(file.blockCount()).is(0);
    ASSERT.that(file.getBlock(0)).isNull();
  }

  @Test
  public void testCopyTo() {
    file.addBlock(new byte[] {1});
    file.addBlock(new byte[] {1, 2});
    RegularFile other = createFile();

    ASSERT.that(other.blockCount()).is(0);

    file.copyBlocksTo(other, 2);

    ASSERT.that(other.blockCount()).is(2);
    ASSERT.that(other.getBlock(0)).isEqualTo(file.getBlock(0));
    ASSERT.that(other.getBlock(1)).isEqualTo(file.getBlock(1));

    file.copyBlocksTo(other, 1); // should copy the last block

    ASSERT.that(other.blockCount()).is(3);
    ASSERT.that(other.getBlock(2)).isEqualTo(file.getBlock(1));

    other.copyBlocksTo(file, 3);

    ASSERT.that(file.blockCount()).is(5);
    ASSERT.that(file.getBlock(2)).isEqualTo(other.getBlock(0));
    ASSERT.that(file.getBlock(3)).isEqualTo(other.getBlock(1));
    ASSERT.that(file.getBlock(4)).isEqualTo(other.getBlock(2));
  }

  @Test
  public void testTransferTo() {
    file.addBlock(new byte[] {1});
    file.addBlock(new byte[] {1, 2});
    file.addBlock(new byte[] {1, 2, 3});
    RegularFile other = createFile();

    ASSERT.that(file.blockCount()).is(3);
    ASSERT.that(other.blockCount()).is(0);

    file.transferBlocksTo(other, 3);

    ASSERT.that(file.blockCount()).is(0);
    ASSERT.that(other.blockCount()).is(3);

    ASSERT.that(file.getBlock(0)).isNull();
    ASSERT.that(Bytes.asList(other.getBlock(0))).isEqualTo(Bytes.asList(new byte[] {1}));
    ASSERT.that(Bytes.asList(other.getBlock(1))).isEqualTo(Bytes.asList(new byte[] {1, 2}));
    ASSERT.that(Bytes.asList(other.getBlock(2))).isEqualTo(Bytes.asList(new byte[] {1, 2, 3}));

    other.transferBlocksTo(file, 1);

    ASSERT.that(file.blockCount()).is(1);
    ASSERT.that(other.blockCount()).is(2);
    ASSERT.that(other.getBlock(2)).isNull();
    ASSERT.that(Bytes.asList(file.getBlock(0))).isEqualTo(Bytes.asList(new byte[]{1, 2, 3}));
    ASSERT.that(file.getBlock(1)).isNull();
  }
}
