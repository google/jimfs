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

import com.google.common.primitives.UnsignedBytes;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A {@link Disk} using a single byte array that doubles in size when more blocks are needed.
 *
 * @author Colin Decker
 */
final class SingleArrayDisk extends Disk {

  public static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 5;

  /** 8 KB blocks */
  private static final int DEFAULT_BLOCK_SIZE = 8 * 1024;

  /** 8 MB initial array. */
  private static final int INITIAL_ARRAY_SIZE = DEFAULT_BLOCK_SIZE * 1024;

  private byte[] array;

  /**
   * Create a disk with default settings.
   */
  public SingleArrayDisk() {
    this(DEFAULT_BLOCK_SIZE, INITIAL_ARRAY_SIZE);
  }

  /**
   * Creates a disk using the given block size and number of blocks per allocated array.
   */
  public SingleArrayDisk(int blockSize, int initialArraySize) {
    super(blockSize);

    checkArgument(initialArraySize > 0, "initialArraySize (%s) must be positive", initialArraySize);
    checkArgument(initialArraySize % blockSize == 0,
        "initialArraySize (%s) must be a multiple of blockSize", initialArraySize);

    this.array = new byte[initialArraySize];
    initialize();
  }

  @Override
  protected int allocateMoreBlocks() {
    if (array.length == MAX_ARRAY_SIZE) {
      throw new OutOfMemoryError("unable to allocate a larger array");
    }

    int newSize = array.length * 2;
    if (newSize < 0 || newSize > MAX_ARRAY_SIZE) {
      newSize = MAX_ARRAY_SIZE;
    }

    int newBlockCount = (newSize - array.length) / blockSize;

    byte[] newArray = new byte[newSize];
    System.arraycopy(array, 0, newArray, 0, array.length);

    for (int i = array.length; i < newSize; i += blockSize) {
      blocks.add(i);
    }

    array = newArray;
    return newBlockCount;
  }

  @Override
  public void zero(long block, int off, int len) {
    int start = (int) block + off;
    int end = start + len;
    Arrays.fill(array, start, end, (byte) 0);
  }

  @Override
  public void copy(long from, long to) {
    System.arraycopy(array, (int) from, array, (int) to, blockSize);
  }

  @Override
  public void put(long block, int index, byte b) {
    checkArgument(index < blockSize);
    array[(int) block + index] = b;
  }

  @Override
  public int put(long block, int index, byte[] b, int off, int len) {
    int bytesToWrite = Math.min(len, blockSize - index);
    System.arraycopy(b, off, array, (int) block + index, bytesToWrite);
    return bytesToWrite;
  }

  @Override
  public int put(long block, int index, ByteBuffer buf) {
    int bytesToWrite = Math.min(buf.remaining(), blockSize - index);
    buf.get(array, (int) block + index, bytesToWrite);
    return bytesToWrite;
  }

  @Override
  public int get(long block, int index) {
    checkArgument(index < blockSize);
    return UnsignedBytes.toInt(array[(int) block + index]);
  }

  @Override
  public int get(long block, int index, byte[] b, int off, int len) {
    int bytesToRead = Math.min(len, blockSize - index);
    System.arraycopy(array, (int) block + index, b, off, bytesToRead);
    return bytesToRead;
  }

  @Override
  public int get(long block, int index, ByteBuffer buf, int len) {
    int bytesToRead = Math.min(len, blockSize - index);
    buf.put(array, (int) block + index, bytesToRead);
    return bytesToRead;
  }

  @Override
  public ByteBuffer asByteBuffer(long block) {
    return ByteBuffer.wrap(array, (int) block, blockSize).slice();
  }
}
