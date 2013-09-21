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

import com.google.common.collect.Iterables;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * A resizable, in-memory pseudo-disk acting as a shared space for storing file data.
 *
 * @author Colin Decker
 */
final class ArrayPerBlockDisk implements RegularFileStorage {

  /** 8 KB */
  private static final int DEFAULT_BLOCK_SIZE = 8 * 1024;

  private final int blockSize;
  private final Deque<byte[]> blocks = new ArrayDeque<>();

  private long totalSize;

  public ArrayPerBlockDisk() {
    this(DEFAULT_BLOCK_SIZE);
  }

  public ArrayPerBlockDisk(int blockSize) {
    checkArgument(blockSize > 0, "blockSize (%s) must be positive", blockSize);
    checkArgument(blockSize % 2 == 0, "blockSize (%s) must be a multiple of 2", blockSize);
    this.blockSize = blockSize;
  }

  @Override
  public final ByteStore createByteStore() {
    return new ArrayPerBlockDiskByteStore(this);
  }

  /**
   * Returns the size of blocks created by this disk.
   */
  public final int blockSize() {
    return blockSize;
  }

  @Override
  public synchronized long getTotalSpace() {
    return totalSize;
  }

  @Override
  public synchronized long getUnallocatedSpace() {
    return blocks.size() * blockSize;
  }

  /**
   * Returns an available block, allocating more blocks if needed.
   */
  public synchronized byte[] alloc() {
    if (blocks.isEmpty()) {
      totalSize += blockSize;
      return new byte[blockSize];
    }

    return blocks.removeLast();
  }

  /**
   * Adds numBlocks blocks to dest, allocating more blocks if needed.
   */
  public synchronized void alloc(Collection<byte[]> dest, int numBlocks) {
    int existingBlocks = Math.min(blocks.size(), numBlocks);
    for (int i = 0; i < existingBlocks; i++) {
      dest.add(blocks.removeLast());
    }

    int remainingBlocks = numBlocks - existingBlocks;
    for (int i = 0; i < remainingBlocks; i++) {
      dest.add(new byte[blockSize]);
    }
    totalSize += remainingBlocks * blockSize;
  }

  /**
   * Frees all given blocks.
   */
  public synchronized void free(Collection<byte[]> blocks) {
    this.blocks.addAll(blocks);
  }
}
