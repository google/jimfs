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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.math.LongMath;
import com.google.jimfs.Configuration;

import java.io.IOException;
import java.math.RoundingMode;

/**
 * A resizable pseudo-disk acting as a shared space for storing file data. A disk allocates fixed
 * size blocks of bytes to files as needed and may cache blocks that have been freed for reuse. A
 * memory disk has a fixed maximum number of blocks it will allocate at a time (which sets the
 * total "size" of the disk) and a maximum number of unused blocks it will cache for reuse at a
 * time (which sets the minimum amount of space the disk will use once
 *
 * @author Colin Decker
 */
final class HeapDisk {

  /** 8 KB blocks. */
  public static final int DEFAULT_BLOCK_SIZE = 8192;

  /** 4 GB of space with 8 KB blocks. */
  public static final int DEFAULT_MAX_BLOCK_COUNT =
      (int) ((4L * 1024 * 1024 * 1024) / DEFAULT_BLOCK_SIZE);

  /** Fixed size of each block for this disk. */
  private final int blockSize;

  /** Maximum total number of blocks that the disk may contain at any time. */
  private final int maxBlockCount;

  /** Maximum total number of unused blocks that may be cached for reuse at any time. */
  private final int maxCachedBlockCount;

  /** Cache of free blocks to be allocated to files. */
  @VisibleForTesting final BlockList blockCache;

  /** The current total number of blocks that are currently allocated to files. */
  private int allocatedBlockCount;

  /**
   * Creates a new heap disk with 8 KB blocks that can store up to 4 GB of data and caches all
   * blocks that are freed.
   */
  public HeapDisk() {
    this(DEFAULT_BLOCK_SIZE, DEFAULT_MAX_BLOCK_COUNT, DEFAULT_MAX_BLOCK_COUNT);
  }

  public HeapDisk(Configuration config) {
    this.blockSize = config.blockSize();
    this.maxBlockCount = toBlockCount(config.maxSize(), blockSize);
    this.maxCachedBlockCount = config.maxCacheSize() == -1
        ? maxBlockCount
        : toBlockCount(config.maxCacheSize(), blockSize);
    this.blockCache = new BlockList(Math.min(maxCachedBlockCount, 8192));
  }

  /**  Returns the nearest multiple of {@code blockSize} that is <= {@code size}. */
  private static int toBlockCount(long size, int blockSize) {
    return (int) LongMath.divide(size, blockSize, RoundingMode.FLOOR);
  }

  /**
   * Creates a new disk with the given {@code blockSize}, {@code maxBlockCount} and
   * {@code maxCachedBlockCount}.
   */
  public HeapDisk(int blockSize, int maxBlockCount, int maxCachedBlockCount) {
    checkArgument(blockSize > 0, "blockSize (%s) must be positive", blockSize);
    checkArgument(maxBlockCount > 0, "maxBlockCount (%s) must be positive", maxBlockCount);
    checkArgument(maxCachedBlockCount >= 0,
        "maxCachedBlockCount must be non-negative", maxCachedBlockCount);
    this.blockSize = blockSize;
    this.maxBlockCount = maxBlockCount;
    this.maxCachedBlockCount = maxCachedBlockCount;
    this.blockCache = new BlockList(Math.min(maxCachedBlockCount, 8192));
  }

  /**
   * Returns the size of blocks created by this disk.
   */
  public int blockSize() {
    return blockSize;
  }

  /**
   * Returns the total size of this disk. This is the maximum size of the disk and does not reflect
   * the amount of data currently allocated or cached.
   */
  public synchronized long getTotalSpace() {
    return maxBlockCount * (long) blockSize;
  }

  /**
   * Returns the current number of unallocated bytes on this disk. This is the maximum number of
   * additional bytes that could be allocated and does not reflect the number of bytes currently
   * actually cached in the disk.
   */
  public synchronized long getUnallocatedSpace() {
    return (maxBlockCount - allocatedBlockCount) * (long) blockSize;
  }

  /**
   * Allocates the given number of blocks and adds their identifiers to the given list.
   */
  public synchronized void allocate(BlockList blocks, int count) throws IOException {
    int newAllocatedBlockCount = allocatedBlockCount + count;
    if (newAllocatedBlockCount > maxBlockCount) {
      throw new IOException("out of disk space");
    }

    int newBlocksNeeded = Math.max(count - blockCache.size(), 0);

    for (int i = 0; i < newBlocksNeeded; i++) {
      blocks.add(new byte[blockSize]);
    }

    if (newBlocksNeeded != count) {
      blockCache.transferTo(blocks, count - newBlocksNeeded);
    }

    allocatedBlockCount = newAllocatedBlockCount;
  }

  /**
   * Frees all blocks in the given list.
   */
  public void free(BlockList blocks) {
    free(blocks, blocks.size());
  }

  /**
   * Frees the last count blocks from the given list.
   */
  public synchronized void free(BlockList blocks, int count) {
    int remainingCacheSpace = maxCachedBlockCount - blocks.size();
    if (remainingCacheSpace > 0) {
      blocks.copyTo(blockCache, Math.min(count, remainingCacheSpace));
    }
    blocks.truncate(blocks.size() - count);

    allocatedBlockCount -= count;
  }
}
