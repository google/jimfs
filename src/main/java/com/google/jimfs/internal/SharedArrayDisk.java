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
 * {@link Disk} made up of multiple byte arrays, each of which backs multiple blocks.
 *
 * @author Colin Decker
 */
final class SharedArrayDisk extends Disk {

  private static final int DEFAULT_BLOCKS_PER_ARRAY = 512;

  private final int blocksPerArray;
  private final int arraySize;
  private final List<byte[]> arrays;
  private int blockCount;

  /**
   * Create a disk with default settings.
   */
  public SharedArrayDisk() {
    this(DEFAULT_BLOCK_SIZE, DEFAULT_BLOCKS_PER_ARRAY);
  }

  /**
   * Creates a disk using the given block size and number of blocks per allocated array.
   */
  public SharedArrayDisk(int blockSize, int blocksPerArray) {
    super(blockSize);
    checkArgument(blocksPerArray > 0, "blocksPerArray (%s) must be positive", blocksPerArray);

    this.blocksPerArray = blocksPerArray;
    this.arraySize = blockSize * blocksPerArray;
    this.arrays = new ArrayList<>();
  }

  @Override
  protected int blockCount() {
    return blockCount;
  }

  @Override
  protected void allocateMoreBlocks() {
    arrays.add(new byte[arraySize]);

    int newBlockCount = blockCount + blocksPerArray;

    // add blocks in reverse so they come out in contiguous order... not that it really matters
    for (int i = newBlockCount - 1; i >= blockCount; i--) {
      blocks.add(i);
    }

    blockCount = newBlockCount;
  }

  @Override
  public void zero(int block, int offset, int len) {
    byte[] bytes = bytes(block);
    int blockOff = offset(block);
    int start = blockOff + offset;
    int end = start + len;
    Arrays.fill(bytes, start, end, (byte) 0);
  }

  @Override
  public void copy(int from, int to) {
    byte[] fromBytes = bytes(from);
    int fromOff = offset(from);

    byte[] toBytes = bytes(to);
    int toOff = offset(to);

    System.arraycopy(fromBytes, fromOff, toBytes, toOff, blockSize);
  }

  @Override
  public void put(int block, int offset, byte b) {
    checkArgument(offset < blockSize);
    byte[] bytes = bytes(block);
    int blockOff = offset(block);
    bytes[blockOff + offset] = b;
  }

  @Override
  public int put(int block, int offset, byte[] b, int off, int len) {
    int bytesToWrite = Math.min(len, blockSize - offset);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    System.arraycopy(b, off, bytes, blockOff + offset, bytesToWrite);
    return bytesToWrite;
  }

  @Override
  public int put(int block, int offset, ByteBuffer buf) {
    int bytesToWrite = Math.min(buf.remaining(), blockSize - offset);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    buf.get(bytes, blockOff + offset, bytesToWrite);
    return bytesToWrite;
  }

  @Override
  public int get(int block, int offset) {
    checkArgument(offset < blockSize);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    return UnsignedBytes.toInt(bytes[blockOff + offset]);
  }

  @Override
  public int get(int block, int offset, byte[] b, int off, int len) {
    int bytesToRead = Math.min(len, blockSize - offset);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    System.arraycopy(bytes, blockOff + offset, b, off, bytesToRead);
    return bytesToRead;
  }

  @Override
  public int get(int block, int offset, ByteBuffer buf, int maxLen) {
    int len = Math.min(blockSize - offset, maxLen);

    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    buf.put(bytes, blockOff + offset, len);
    return len;
  }

  @Override
  public ByteBuffer asByteBuffer(int block, int offset, long maxLen) {
    byte[] bytes = bytes(block);
    int blockOff = offset(block);

    int len = (int) Math.min(blockSize - offset, maxLen);

    return ByteBuffer.wrap(bytes, blockOff + offset, len);
  }

  private byte[] bytes(int block) {
    return arrays.get(block / blocksPerArray);
  }

  private int offset(int block) {
    return (block % blocksPerArray) * blockSize;
  }
}
