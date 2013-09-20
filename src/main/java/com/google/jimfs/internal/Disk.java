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
 * A resizable, in-memory pseudo-disk acting as a shared space for storing file data.
 *
 * @author Colin Decker
 */
abstract class Disk implements RegularFileStorage {

  protected final int blockSize;
  protected final BlockQueue blocks = new BlockQueue(1024);
  private long totalSize;

  protected Disk(int blockSize) {
    checkArgument(blockSize > 0, "blockSize (%s) must be positive", blockSize);
    checkArgument(blockSize % 2 == 0, "blockSize (%s) must be a multiple of 2", blockSize);
    this.blockSize = blockSize;
  }

  @Override
  public final ByteStore createByteStore() {
    return new DiskByteStore(this);
  }

  /**
   * Initialize the disk by allocating initial blocks.
   */
  protected final void initialize() {
    totalSize = allocateMoreBlocks() * blockSize;
  }

  /**
   * Allocates more blocks if possible. The {@code blocks} queue should have blocks in it when this
   * returns if an exception is not thrown. Returns the number of blocks allocated.
   */
  protected abstract int allocateMoreBlocks();

  /**
   * Returns the size of blocks created by this disk.
   */
  public final int blockSize() {
    return blockSize;
  }

  @Override
  public synchronized final long getTotalSpace() {
    return totalSize;
  }

  @Override
  public synchronized final long getUnallocatedSpace() {
    return blocks.size() * blockSize;
  }

  /**
   * Allocates and returns an available block.
   */
  public final synchronized long alloc() {
    if (blocks.isEmpty()) {
      totalSize += allocateMoreBlocks() * blockSize;
    }

    return blocks.take();
  }

  /**
   * Frees all blocks in the given queue.
   */
  public final synchronized void free(BlockQueue blocks) {
    this.blocks.addAll(blocks);
  }

  /**
   * Zeroes len bytes in the given block starting at the given offset.
   */
  public abstract void zero(long block, int off, int len);

  /**
   * Copies the block at from to the block at to.
   */
  public abstract void copy(long from, long to);

  /**
   * Puts the given byte at the given index in the given block.
   *
   * @throws IllegalArgumentException if index >= the size of this block
   */
  public abstract void put(long block, int index, byte b);

  /**
   * Puts the given subsequence of the given array at the given index in the given block.
   */
  public abstract int put(long block, int index, byte[] b, int off, int len);

  /**
   * Puts the contents of the given byte buffer at the given index in the given block.
   */
  public abstract int put(long block, int index, ByteBuffer buf);

  /**
   * Returns the byte at the given index in the given block.
   *
   * @throws IllegalArgumentException if index >= the size of this block
   */
  public abstract int get(long block, int index);

  /**
   * Reads len bytes starting at the given index in the given block into the given byte array
   * starting at the given offset.
   */
  public abstract int get(long block, int index, byte[] b, int off, int len);

  /**
   * Reads up to len bytes starting at the given index in the given block into the given byte
   * buffer.
   */
  public abstract int get(long block, int index, ByteBuffer buf, int len);

  /**
   * Returns a view of the given block as a byte buffer.
   */
  public abstract ByteBuffer asByteBuffer(long block);

  /**
   * Simple queue of block start positions. Can be read like a list, but values can only be added
   * or removed on the end.
   */
  static final class BlockQueue {

    private long[] values;
    private int head;

    public BlockQueue(int initialCapacity) {
      this.values = new long[initialCapacity];
    }

    private void expandIfNecessary(int minSize) {
      if (minSize > values.length) {
        int newLength = values.length * 2;
        while (newLength < minSize) {
          newLength *= 2;
        }
        long[] newValues = new long[newLength];
        System.arraycopy(values, 0, newValues, 0, values.length);
        this.values = newValues;
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

    public void addAll(long[] values, int len) {
      int end = head + len;
      expandIfNecessary(end);

      System.arraycopy(values, 0, this.values, head, len);
      head = end;
    }

    public void add(long value) {
      expandIfNecessary(head + 1);
      values[head++] = value;
    }

    public long get(int index) {
      return values[index];
    }

    public void clear() {
      head = 0;
    }

    public long take() {
      return values[--head];
    }
  }
}
