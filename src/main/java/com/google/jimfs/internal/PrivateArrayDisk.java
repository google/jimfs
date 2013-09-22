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

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * {@link Disk} that gives each block its own byte array.
 *
 * @author Colin Decker
 */
final class PrivateArrayDisk extends Disk {

  private byte[][] blocks = new byte[256][];
  private int blockCount;

  /**
   * Creates a disk with the default block size.
   */
  public PrivateArrayDisk() {
    this(DEFAULT_BLOCK_SIZE);
  }

  /**
   * Creates a disk with the given block size.
   */
  public PrivateArrayDisk(int blockSize) {
    super(blockSize);
  }

  @Override
  protected void allocateMoreBlocks() {
    int newBlockIndex = blockCount;
    blockCount++;
    if (blockCount > blocks.length) {
      blocks = Arrays.copyOf(blocks, blocks.length * 2);
    }

    blocks[newBlockIndex] = new byte[blockSize];
    freeBlocks.add(newBlockIndex);
  }

  @Override
  protected int blockCount() {
    return blockCount;
  }

  @Override
  public void zero(int block, int offset, int len) {
    Arrays.fill(blocks[block], offset, offset + len, (byte) 0);
  }

  @Override
  public void copy(int from, int to) {
    System.arraycopy(blocks[from], 0, blocks[to], 0, blockSize);
  }

  @Override
  public void put(int block, int offset, byte b) {
    blocks[block][offset] = b;
  }

  @Override
  public int put(int block, int offset, byte[] b, int off, int len) {
    System.arraycopy(b, off, blocks[block], offset, len);
    return len;
  }

  @Override
  public int put(int block, int offset, ByteBuffer buf) {
    int len = Math.min(blockSize - offset, buf.remaining());
    buf.get(blocks[block], offset, len);
    return len;
  }

  @Override
  public int get(int block, int offset) {
    return blocks[block][offset];
  }

  @Override
  public int get(int block, int offset, byte[] b, int off, int len) {
    System.arraycopy(blocks[block], offset, b, off, len);
    return len;
  }

  @Override
  public int get(int block, int offset, ByteBuffer buf, int maxLen) {
    int len = Math.min(blockSize - offset, maxLen);
    buf.put(blocks[block], offset, len);
    return len;
  }

  @Override
  public ByteBuffer asByteBuffer(int block, int offset, long maxLen) {
    return ByteBuffer.wrap(blocks[block], offset, (int) Math.min(blockSize - offset, maxLen));
  }
}
