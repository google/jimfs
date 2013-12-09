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

package com.google.jimfs.internal;

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.jimfs.Configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link HeapDisk}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class HeapDiskTest {

  private BlockList blocks;

  @Before
  public void setUp() {
    blocks = new BlockList();
  }

  @Test
  public void testInitialSettings_basic() {
    HeapDisk disk = new HeapDisk(8192, 100, 100);

    ASSERT.that(disk.blockSize()).is(8192);
    ASSERT.that(disk.getTotalSpace()).is(819200);
    ASSERT.that(disk.getUnallocatedSpace()).is(819200);
    ASSERT.that(disk.blockCache.isEmpty()).isTrue();
  }

  @Test
  public void testInitialSettings_fromConfiguration() {
    Configuration config = Configuration.unix().toBuilder()
        .setBlockSize(4)
        .setMaxSize(99) // not a multiple of 4
        .setMaxCacheSize(25)
        .build();

    HeapDisk disk = new HeapDisk(config);

    ASSERT.that(disk.blockSize()).is(4);
    ASSERT.that(disk.getTotalSpace()).is(96);
    ASSERT.that(disk.getUnallocatedSpace()).is(96);
    ASSERT.that(disk.blockCache.isEmpty()).isTrue();
  }

  @Test
  public void testAllocate() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 0);

    disk.allocate(blocks, 1);

    ASSERT.that(blocks.size()).is(1);
    ASSERT.that(blocks.get(0).length).is(4);
    ASSERT.that(disk.getUnallocatedSpace()).is(36);

    disk.allocate(blocks, 5);

    ASSERT.that(blocks.size()).is(6);
    for (int i = 0; i < blocks.size(); i++) {
      ASSERT.that(blocks.get(i).length).is(4);
    }
    ASSERT.that(disk.getUnallocatedSpace()).is(16);
    ASSERT.that(disk.blockCache.isEmpty()).isTrue();
  }

  @Test
  public void testFree_noCaching() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 0);
    disk.allocate(blocks, 6);

    disk.free(blocks, 2);

    ASSERT.that(blocks.size()).is(4);
    ASSERT.that(disk.getUnallocatedSpace()).is(24);
    ASSERT.that(disk.blockCache.isEmpty()).isTrue();

    disk.free(blocks);

    ASSERT.that(blocks.isEmpty()).isTrue();
    ASSERT.that(disk.getUnallocatedSpace()).is(40);
    ASSERT.that(disk.blockCache.isEmpty()).isTrue();
  }

  @Test
  public void testFree_fullCaching() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 10);
    disk.allocate(blocks, 6);

    disk.free(blocks, 2);

    ASSERT.that(blocks.size()).is(4);
    ASSERT.that(disk.getUnallocatedSpace()).is(24);
    ASSERT.that(disk.blockCache.size()).is(2);

    disk.free(blocks);

    ASSERT.that(blocks.isEmpty()).isTrue();
    ASSERT.that(disk.getUnallocatedSpace()).is(40);
    ASSERT.that(disk.blockCache.size()).is(6);
  }

  @Test
  public void testFree_partialCaching() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 4);
    disk.allocate(blocks, 6);

    disk.free(blocks, 2);

    ASSERT.that(blocks.size()).is(4);
    ASSERT.that(disk.getUnallocatedSpace()).is(24);
    ASSERT.that(disk.blockCache.size()).is(2);

    disk.free(blocks);

    ASSERT.that(blocks.isEmpty()).isTrue();
    ASSERT.that(disk.getUnallocatedSpace()).is(40);
    ASSERT.that(disk.blockCache.size()).is(4);
  }

  @Test
  public void testAllocateFromCache_fullAllocationFromCache() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 10);
    disk.allocate(blocks, 10);

    ASSERT.that(disk.getUnallocatedSpace()).is(0);

    disk.free(blocks);

    ASSERT.that(blocks.size()).is(0);
    ASSERT.that(disk.blockCache.size()).is(10);

    List<byte[]> cachedBlocks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      cachedBlocks.add(disk.blockCache.get(i));
    }

    disk.allocate(blocks, 6);

    ASSERT.that(blocks.size()).is(6);
    ASSERT.that(disk.blockCache.size()).is(4);

    // the 6 arrays in blocks are the last 6 arrays that were cached
    for (int i = 0; i < 6; i++) {
      ASSERT.that(blocks.get(i)).is(cachedBlocks.get(i + 4));
    }
  }

  @Test
  public void testAllocateFromCache_partialAllocationFromCache() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 4);
    disk.allocate(blocks, 10);

    ASSERT.that(disk.getUnallocatedSpace()).is(0);

    disk.free(blocks);

    ASSERT.that(blocks.size()).is(0);
    ASSERT.that(disk.blockCache.size()).is(4);

    List<byte[]> cachedBlocks = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      cachedBlocks.add(disk.blockCache.get(i));
    }

    disk.allocate(blocks, 6);

    ASSERT.that(blocks.size()).is(6);
    ASSERT.that(disk.blockCache.size()).is(0);

    // the last 4 arrays in blocks are the 4 arrays that were cached
    for (int i = 2; i < 6; i++) {
      ASSERT.that(blocks.get(i)).is(cachedBlocks.get(i - 2));
    }
  }

  @Test
  public void testFullDisk() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 4);
    disk.allocate(blocks, 10);

    try {
      disk.allocate(blocks, 1);
      fail();
    } catch (IOException expected) {}
  }

  @Test
  public void testFullDisk_doesNotAllocatePartiallyWhenTooManyBlocksRequested() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 4);
    disk.allocate(blocks, 6);

    BlockList blocks2 = new BlockList();

    try {
      disk.allocate(blocks2, 5);
      fail();
    } catch (IOException expected) {}

    ASSERT.that(blocks2.size()).is(0);
  }
}
