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

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteBuffer;

/**
 * A resizable pseudo-disk acting as a shared space for storing file data. A disk allocates fixed
 * size blocks of bytes to files as needed and caches blocks that have been freed for reuse. Each
 * block is represented by an integer which is used to locate the block for read and write
 * operations implemented by the disk.
 *
 * <p>Currently, once a memory disk has allocated a block it will cache that block indefinitely
 * once freed. This means that the total size of the disk is always the maximum number of bytes
 * that have been allocated to files at one time so far.
 *
 * @author Colin Decker
 */
abstract class MemoryDisk {

  /**
   * 8K blocks.
   */
  protected static final int DEFAULT_BLOCK_SIZE = 8192;

  /**
   * Fixed size of each block for this disk.
   */
  protected final int blockSize;

  /**
   * The current total number of blocks this disk contains, including both free blocks and blocks
   * that are allocated to files.
   */
  private int blockCount;

  /**
   * Queue of free blocks to be allocated to files.
   */
  protected final IntList free = new IntList(1024);

  protected MemoryDisk(int blockSize) {
    checkArgument(blockSize > 0, "blockSize (%s) must be positive", blockSize);
    checkArgument(blockSize % 2 == 0, "blockSize (%s) must be a multiple of 2", blockSize);
    this.blockSize = blockSize;
  }

  /**
   * Creates a new, empty byte store.
   */
  public final ByteStore createByteStore() {
    return new MemoryDiskByteStore(this);
  }

  /**
   * Allocates at least {@code minBlocks} more blocks if possible. The {@code free} list
   * should have blocks in it when this returns if an exception is not thrown. Returns the number
   * of new blocks that were allocated.
   */
  protected abstract int allocateMoreBlocks(int count);

  /**
   * Returns the size of blocks created by this disk.
   */
  public final int blockSize() {
    return blockSize;
  }

  /**
   * Returns the current total number of blocks this disk contains.
   */
  protected final int blockCount() {
    return blockCount;
  }

  /**
   * Returns the current total size of this disk.
   */
  public final synchronized long getTotalSpace() {
    return blockCount * blockSize;
  }

  /**
   * Returns the current number of unallocated bytes on this disk.
   */
  public final synchronized long getUnallocatedSpace() {
    return free.size() * blockSize;
  }

  /**
   * Allocates the given number of blocks and adds their identifiers to the given list.
   */
  public final synchronized void allocate(IntList blocks, int count) {
    int additionalBlocksNeeded = count - free.size();
    if (additionalBlocksNeeded > 0) {
      blockCount += allocateMoreBlocks(additionalBlocksNeeded);
    }

    free.transferTo(blocks, count);
  }

  /**
   * Frees all blocks in the given list.
   */
  public final void free(IntList blocks) {
    free(blocks, blocks.size());
  }

  /**
   * Frees the last count blocks from the given list.
   */
  public final synchronized void free(IntList blocks, int count) {
    blocks.transferTo(free, count);
  }

  /**
   * Zeroes len bytes in the given block starting at the given offset. Returns len.
   */
  public abstract int zero(int block, int offset, int len);

  /**
   * Copies the given block and returns the copy.
   */
  public abstract void copy(int block, int copy);

  /**
   * Puts the given byte at the given offset in the given block.
   */
  public abstract void put(int block, int offset, byte b);

  /**
   * Puts the given slice of the given array at the given offset in the given block.
   */
  public abstract int put(int block, int offset, byte[] b, int off, int len);

  /**
   * Puts the contents of the given byte buffer at the given offset in the given block.
   */
  public abstract int put(int block, int offset, ByteBuffer buf);

  /**
   * Returns the byte at the given offset in the given block.
   */
  public abstract byte get(int block, int offset);

  /**
   * Reads len bytes starting at the given offset in the given block into the given slice of the
   * given byte array.
   */
  public abstract int get(int block, int offset, byte[] b, int off, int len);

  /**
   * Reads len bytes starting at the given offset in the given block into the given byte buffer.
   */
  public abstract int get(int block, int offset, ByteBuffer buf, int len);

  /**
   * Returns a ByteBuffer view of the slice of the given block starting at the given offset and
   * having the given length.
   */
  public abstract ByteBuffer asByteBuffer(int block, int offset, int len);
}
