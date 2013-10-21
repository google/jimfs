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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for {@link HeapDisk} that reuse a disk for each store created. Stores are not deleted
 * after most tests, meaning new blocks must be allocated for each store created.
 *
 * @author Colin Decker
 */
public class HeapDiskReuseTest extends HeapDiskTest {

  private final Disk disk = new HeapDisk(8);

  @Override
  protected ByteStore createByteStore() {
    return disk.createByteStore();
  }

  @Test
  public void testDeletedStore_contentsReturnedToDisk() {
    byte[] bytes = new byte[1000];
    store.opened();

    store.write(0, bytes, 0, bytes.length);

    int freeBlocksAfterWrite = disk.freeBlocks.size();
    assertContentEquals(bytes, store);

    store.delete();

    assertEquals(freeBlocksAfterWrite, disk.freeBlocks.size());
    assertContentEquals(bytes, store);

    store.closed();

    assertContentEquals(new byte[0], store);

    int freeBlocksAfterDelete = disk.freeBlocks.size();
    assertTrue(freeBlocksAfterDelete > freeBlocksAfterWrite);
  }
}
