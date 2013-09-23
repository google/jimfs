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
 * {@link Disk} using {@linkplain ByteBuffer#allocateDirect(int) direct} byte buffers for blocks.
 *
 * @author Colin Decker
 */
final class DirectDisk extends Disk {

  private ByteBuffer[] blocks = new ByteBuffer[256];
  private int blockCount;

  /**
   * Creates a disk with the default block size.
   */
  public DirectDisk() {
    this(DEFAULT_BLOCK_SIZE);
  }

  /**
   * Creates a disk with the given block size.
   */
  public DirectDisk(int blockSize) {
    super(blockSize);
  }

  @Override
  protected void allocateMoreBlocks() {
    int newBlockIndex = blockCount;
    blockCount++;
    if (blockCount > blocks.length) {
      blocks = Arrays.copyOf(blocks, blocks.length * 2);
    }

    blocks[newBlockIndex] = ByteBuffer.allocateDirect(blockSize);
    freeBlocks.add(newBlockIndex);
  }

  @Override
  protected int blockCount() {
    return blockCount;
  }

  @Override
  public int zero(int block, int offset, int len) {
    ByteBuffer buffer = blockForRead(block);
    int limit = offset + len;
    for (int i = offset; i < limit; i++) {
      buffer.put(i, (byte) 0);
    }
    return len;
  }

  @Override
  public int copy(int block) {
    ByteBuffer blockBuf = blockForRead(block);
    int copyBlock = alloc();
    ByteBuffer copyBuf = blocks[copyBlock];
    copyBuf.put(blockBuf);
    copyBuf.clear();
    return copyBlock;
  }

  @Override
  public void put(int block, int offset, byte b) {
    blocks[block].put(offset, b);
  }

  @Override
  public int put(int block, int offset, byte[] b, int off, int len) {
    ByteBuffer blockBuf = blocks[block];
    blockBuf.position(offset);
    blockBuf.put(b, off, len);
    blockBuf.clear();
    return len;
  }

  @Override
  public int put(int block, int offset, ByteBuffer buf) {
    ByteBuffer blockBuf = blocks[block];
    blockBuf.position(offset);
    int len = Math.min(blockSize - offset, buf.remaining());
    blockBuf.put(buf);
    blockBuf.clear();
    return len;
  }

  @Override
  public byte get(int block, int offset) {
    return blocks[block].get(offset);
  }

  @Override
  public int get(int block, int offset, byte[] b, int off, int len) {
    ByteBuffer blockBuf = blockForRead(block);
    blockBuf.position(offset);
    blockBuf.get(b, off, len);
    return len;
  }

  @Override
  public int get(int block, int offset, ByteBuffer buf, int len) {
    ByteBuffer blockBuf = blockForRead(block);
    blockBuf.position(offset);
    blockBuf.limit(offset + len);
    buf.put(blockBuf);
    return len;
  }

  @Override
  public ByteBuffer asByteBuffer(int block, int offset, int len) {
    ByteBuffer result = blockForRead(block);
    result.position(offset);
    result.limit(offset + len);
    return result;
  }

  /**
   * Must duplicate the block because we can't have multiple reading threads manipulating the state
   * of the same buffer at the same time. Why oh why couldn't some kind of public buffer type
   * without positional state have been created? ...and a better buffer API than ByteBuffer created
   * on top of it, while I'm wishing.
   */
  private ByteBuffer blockForRead(int block) {
    return blocks[block].duplicate();
  }

}
