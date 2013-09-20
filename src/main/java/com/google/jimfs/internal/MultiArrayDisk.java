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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link Disk} made up of multiple byte arrays. Each time more blocks are needed, a new byte
 * array is allocated and used to back multiple arrays.
 *
 * @author Colin Decker
 */
final class MultiArrayDisk extends Disk {

  /** 8 KB blocks */
  private static final int DEFAULT_BLOCK_SIZE = 8 * 1024;

  /** Allocated in 4 MB arrays. */
  private static final int DEFAULT_BLOCKS_PER_ARRAY = 512;

  private final int blocksPerArray;
  private final int arraySize;
  private final List<byte[]> arrays;
  private long size;

  /**
   * Create a disk with default settings.
   */
  public MultiArrayDisk() {
    this(DEFAULT_BLOCK_SIZE, DEFAULT_BLOCKS_PER_ARRAY);
  }

  /**
   * Creates a disk using the given block size and number of blocks per allocated array.
   */
  public MultiArrayDisk(int blockSize, int blocksPerArray) {
    super(blockSize);
    checkArgument(blocksPerArray > 0, "blocksPerArray (%s) must be positive", blocksPerArray);

    this.blocksPerArray = blocksPerArray;
    this.arraySize = blockSize * blocksPerArray;
    this.arrays = new ArrayList<>();

    initialize();
  }

  @Override
  protected int allocateMoreBlocks() {
    arrays.add(new byte[arraySize]);
    long firstBlockPosition = size;
    size += arraySize;
    for (long i = firstBlockPosition; i < size; i += blockSize) {
      blocks.add(i);
    }
    return blocksPerArray;
  }

  @Override
  public void zero(long block, int off, int len) {
    byte[] bytes = bytes(block);
    int blockOff = offset(block);
    int start = blockOff + off;
    int end = start + len;
    Arrays.fill(bytes, start, end, (byte) 0);
  }

  @Override
  public void copy(long from, long to) {
    byte[] fromBytes = bytes(from);
    int fromOff = offset(from);

    byte[] toBytes = bytes(to);
    int toOff = offset(to);

    System.arraycopy(fromBytes, fromOff, toBytes, toOff, blockSize);
  }

  @Override
  public void put(long block, int index, byte b) {
    checkArgument(index < blockSize);
    byte[] bytes = bytes(block);
    int blockOff = offset(block);
    bytes[blockOff + index] = b;
  }

  @Override
  public int put(long block, int index, byte[] b, int off, int len) {
    int bytesToWrite = Math.min(len, blockSize - index);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    System.arraycopy(b, off, bytes, blockOff + index, bytesToWrite);
    return bytesToWrite;
  }

  @Override
  public int put(long block, int index, ByteBuffer buf) {
    int bytesToWrite = Math.min(buf.remaining(), blockSize - index);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    buf.get(bytes, blockOff + index, bytesToWrite);
    return bytesToWrite;
  }

  @Override
  public int get(long block, int index) {
    checkArgument(index < blockSize);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    return UnsignedBytes.toInt(bytes[blockOff + index]);
  }

  @Override
  public int get(long block, int index, byte[] b, int off, int len) {
    int bytesToRead = Math.min(len, blockSize - index);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    System.arraycopy(bytes, blockOff + index, b, off, bytesToRead);
    return bytesToRead;
  }

  @Override
  public int get(long block, int index, ByteBuffer buf, int len) {
    int bytesToRead = Math.min(len, blockSize - index);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    buf.put(bytes, blockOff + index, bytesToRead);
    return bytesToRead;
  }

  @Override
  public ByteBuffer asByteBuffer(long block) {
    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    return ByteBuffer.wrap(bytes, blockOff, blockSize).slice();
  }

  private byte[] bytes(long block) {
    return arrays.get((int) (block / arraySize));
  }

  private int offset(long block) {
    return (int) (block % arraySize);
  }
}
