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
import static com.google.jimfs.internal.Util.nextPowerOf2;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A resizable pseudo-disk acting as a shared space for storing file data. A disk allocates fixed
 * size blocks of bytes to files as needed and retains blocks that have been freed for reuse. Each
 * block is represented by an integer which is used to locate the block for read and write
 * operations implemented by the disk.
 *
 * @author Colin Decker
 */
abstract class Disk extends RegularFileStorage {

  /**
   * Fixed size of each block for this disk.
   */
  protected final int blockSize;

  /**
   * The target maximum number of blocks the disk should hold. While the number of blocks in the
   * disk can go over this number if needed for files, when the number of block in the disk is over
   * this number freed blocks will be discarded rather than being re-added to the block pool.
   */
  private final int maxCacheBlocks;

  /**
   * The current total number of blocks this disk contains, including both free blocks and blocks
   * that are allocated to files.
   */
  private int blockCount;

  /**
   * Queue of free blocks to be allocated to files.
   */
  protected final BlockQueue freeBlocks = new BlockQueue(1024);

  protected Disk(int blockSize, long maxCacheSize) {
    checkArgument(blockSize > 0, "blockSize (%s) must be positive", blockSize);
    checkArgument(blockSize % 2 == 0, "blockSize (%s) must be a multiple of 2", blockSize);
    this.blockSize = blockSize;
    this.maxCacheBlocks = (int) (maxCacheSize / blockSize);
  }

  @Override
  public final ByteStore createByteStore() {
    return new DiskByteStore(this);
  }

  /**
   * Allocates at least {@code minBlocks} more blocks if possible. The {@code blocks} queue should
   * have blocks in it when this returns if an exception is not thrown. Returns the number of new
   * blocks that were allocated.
   */
  protected abstract int allocateMoreBlocks(int minBlocks);

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

  @Override
  public final synchronized long getTotalSpace() {
    return blockCount * blockSize;
  }

  @Override
  public final synchronized long getUnallocatedSpace() {
    return freeBlocks.size() * blockSize;
  }

  /**
   * Allocates the given number of blocks and adds their identifiers to the given queue.
   */
  public final synchronized void alloc(BlockQueue queue, int numBlocks) {
    int additionalBlocksNeeded = numBlocks - freeBlocks.size();
    if (additionalBlocksNeeded > 0) {
      blockCount += allocateMoreBlocks(additionalBlocksNeeded);
    }

    for (int i = 0; i < numBlocks; i++) {
      queue.add(freeBlocks.take());
    }
  }

  /**
   * Frees all blocks in the given queue.
   */
  public final synchronized void free(BlockQueue blocks) {
    int blocksToCache = maxCacheBlocks - blockCount;
    if (blocksToCache > blocks.size()) {
      freeBlocks.addAll(blocks);
    } else {
      for (int i = 0; i < blocksToCache; i++) {
        freeBlocks.add(blocks.take());
      }
    }
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

  /**
   * Simple queue of block identifiers. Can be read like a list, but values can only be added
   * or removed at the end.
   */
  static final class BlockQueue {

    private int[] values;
    private int head;

    public BlockQueue(int initialCapacity) {
      this.values = new int[initialCapacity];
    }

    private void expandIfNecessary(int minSize) {
      if (minSize > values.length) {
        this.values = Arrays.copyOf(values, nextPowerOf2(minSize));
      }
    }

    public boolean isEmpty() {
      return head == 0;
    }

    public int size() {
      return head;
    }

    public void addAll(BlockQueue queue) {
      addAll(queue.values, queue.head);
    }

    public void addAll(int[] values, int len) {
      int end = head + len;
      expandIfNecessary(end);

      System.arraycopy(values, 0, this.values, head, len);
      head = end;
    }

    public void add(int value) {
      expandIfNecessary(head + 1);
      values[head++] = value;
    }

    public int get(int index) {
      return values[index];
    }

    public void clear() {
      head = 0;
    }

    public int take() {
      return values[--head];
    }
  }
}
