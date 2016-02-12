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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

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

  private RegularFile blocks;

  @Before
  public void setUp() {
    // the HeapDisk of this file is unused; it's passed to other HeapDisks to test operations
    blocks = RegularFile.create(-1, new HeapDisk(2, 2, 2));
  }

  @Test
  public void testInitialSettings_basic() {
    HeapDisk disk = new HeapDisk(8192, 100, 100);

    assertThat(disk.blockSize()).isEqualTo(8192);
    assertThat(disk.getTotalSpace()).isEqualTo(819200);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(819200);
    assertThat(disk.blockCache.blockCount()).isEqualTo(0);
  }

  @Test
  public void testInitialSettings_fromConfiguration() {
    Configuration config =
        Configuration.unix()
            .toBuilder()
            .setBlockSize(4)
            .setMaxSize(99) // not a multiple of 4
            .setMaxCacheSize(25)
            .build();

    HeapDisk disk = new HeapDisk(config);

    assertThat(disk.blockSize()).isEqualTo(4);
    assertThat(disk.getTotalSpace()).isEqualTo(96);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(96);
    assertThat(disk.blockCache.blockCount()).isEqualTo(0);
  }

  @Test
  public void testAllocate() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 0);

    disk.allocate(blocks, 1);

    assertThat(blocks.blockCount()).isEqualTo(1);
    assertThat(blocks.getBlock(0).length).isEqualTo(4);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(36);

    disk.allocate(blocks, 5);

    assertThat(blocks.blockCount()).isEqualTo(6);
    for (int i = 0; i < blocks.blockCount(); i++) {
      assertThat(blocks.getBlock(i).length).isEqualTo(4);
    }
    assertThat(disk.getUnallocatedSpace()).isEqualTo(16);
    assertThat(disk.blockCache.blockCount()).isEqualTo(0);
  }

  @Test
  public void testFree_noCaching() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 0);
    disk.allocate(blocks, 6);

    disk.free(blocks, 2);
    assertThat(blocks.blockCount()).isEqualTo(4);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(24);
    assertThat(disk.blockCache.blockCount()).isEqualTo(0);

    disk.free(blocks);

    assertThat(blocks.blockCount()).isEqualTo(0);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(40);
    assertThat(disk.blockCache.blockCount()).isEqualTo(0);
  }

  @Test
  public void testFree_fullCaching() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 10);
    disk.allocate(blocks, 6);

    disk.free(blocks, 2);

    assertThat(blocks.blockCount()).isEqualTo(4);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(24);
    assertThat(disk.blockCache.blockCount()).isEqualTo(2);

    disk.free(blocks);

    assertThat(blocks.blockCount()).isEqualTo(0);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(40);
    assertThat(disk.blockCache.blockCount()).isEqualTo(6);
  }

  @Test
  public void testFree_partialCaching() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 4);
    disk.allocate(blocks, 6);

    disk.free(blocks, 2);

    assertThat(blocks.blockCount()).isEqualTo(4);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(24);
    assertThat(disk.blockCache.blockCount()).isEqualTo(2);

    disk.free(blocks);

    assertThat(blocks.blockCount()).isEqualTo(0);
    assertThat(disk.getUnallocatedSpace()).isEqualTo(40);
    assertThat(disk.blockCache.blockCount()).isEqualTo(4);
  }

  @Test
  public void testAllocateFromCache_fullAllocationFromCache() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 10);
    disk.allocate(blocks, 10);

    assertThat(disk.getUnallocatedSpace()).isEqualTo(0);

    disk.free(blocks);

    assertThat(blocks.blockCount()).isEqualTo(0);
    assertThat(disk.blockCache.blockCount()).isEqualTo(10);

    List<byte[]> cachedBlocks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      cachedBlocks.add(disk.blockCache.getBlock(i));
    }

    disk.allocate(blocks, 6);

    assertThat(blocks.blockCount()).isEqualTo(6);
    assertThat(disk.blockCache.blockCount()).isEqualTo(4);

    // the 6 arrays in blocks are the last 6 arrays that were cached
    for (int i = 0; i < 6; i++) {
      assertThat(blocks.getBlock(i)).isEqualTo(cachedBlocks.get(i + 4));
    }
  }

  @Test
  public void testAllocateFromCache_partialAllocationFromCache() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 4);
    disk.allocate(blocks, 10);

    assertThat(disk.getUnallocatedSpace()).isEqualTo(0);

    disk.free(blocks);

    assertThat(blocks.blockCount()).isEqualTo(0);
    assertThat(disk.blockCache.blockCount()).isEqualTo(4);

    List<byte[]> cachedBlocks = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      cachedBlocks.add(disk.blockCache.getBlock(i));
    }

    disk.allocate(blocks, 6);

    assertThat(blocks.blockCount()).isEqualTo(6);
    assertThat(disk.blockCache.blockCount()).isEqualTo(0);

    // the last 4 arrays in blocks are the 4 arrays that were cached
    for (int i = 2; i < 6; i++) {
      assertThat(blocks.getBlock(i)).isEqualTo(cachedBlocks.get(i - 2));
    }
  }

  @Test
  public void testFullDisk() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 4);
    disk.allocate(blocks, 10);

    try {
      disk.allocate(blocks, 1);
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testFullDisk_doesNotAllocatePartiallyWhenTooManyBlocksRequested() throws IOException {
    HeapDisk disk = new HeapDisk(4, 10, 4);
    disk.allocate(blocks, 6);

    RegularFile blocks2 = RegularFile.create(-2, disk);

    try {
      disk.allocate(blocks2, 5);
      fail();
    } catch (IOException expected) {
    }

    assertThat(blocks2.blockCount()).isEqualTo(0);
  }
}
