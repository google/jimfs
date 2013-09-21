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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndexes;

import com.google.common.primitives.UnsignedBytes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Byte store backed by a {@link ArrayPerBlockDisk}.
 *
 * @author Colin Decker
 */
final class ArrayPerBlockDiskByteStore extends ByteStore {

  private final ArrayPerBlockDisk disk;
  private final List<byte[]> blocks;
  private long size;

  public ArrayPerBlockDiskByteStore(ArrayPerBlockDisk disk) {
    this(disk, new ArrayList<byte[]>(), 0);
  }

  private ArrayPerBlockDiskByteStore(ArrayPerBlockDisk disk, List<byte[]> blocks, long size) {
    this.disk = checkNotNull(disk);
    this.blocks = blocks;
    this.size = size;
  }

  @Override
  public long size() {
    return size;
  }

  @Override
  protected ByteStore createCopy() {
    List<byte[]> copy = new ArrayList<>(blocks.size() * 2);
    for (byte[] block : blocks) {
      byte[] copyBlock = disk.alloc();
      System.arraycopy(block, 0, copyBlock, 0, block.length);
      copy.add(copyBlock);
    }
    return new ArrayPerBlockDiskByteStore(disk, copy, size);
  }

  @Override
  public void delete() {
    writeLock().lock();
    try {
      disk.free(blocks);
      blocks.clear();
      size = 0;
    } finally {
      writeLock().unlock();
    }
  }

  @Override
  public boolean truncate(long size) {
    if (size < this.size) {
      long lastPosition = size - 1;
      this.size = size;

      int newBlockCount = blockIndex(lastPosition) + 1;
      int blocksToRemove = blocks.size() - newBlockCount;
      if (blocksToRemove > 0) {
        List<byte[]> blocksToFree = blocks.subList(blocks.size() - blocksToRemove, blocks.size());
        disk.free(blocksToFree);
        blocksToFree.clear();
      }

      return true;
    }
    return false;
  }

  /**
   * If pos is greater than the current size of this store, zeroes bytes between the current size
   * and pos and sets size to pos. New blocks are added to the file if necessary.
   */
  private void zeroForWrite(long pos) {
    if (pos <= size) {
      return;
    }

    long remaining = pos - size;

    int blockIndex = blockIndex(size);
    byte[] block = blockForWrite(blockIndex);
    int off = offsetInBlock(size);
    int len = (int) Math.min(block.length - off, remaining);

    Arrays.fill(block, off, off + len, (byte) 0);

    remaining -= len;

    while (remaining > 0) {
      block = blockForWrite(++blockIndex);
      len = (int) Math.min(block.length, remaining);

      Arrays.fill(block, 0, len, (byte) 0);

      remaining -= len;
    }

    size = pos;
  }

  @Override
  public int write(long pos, byte b) {
    checkNotNegative(pos, "pos");

    zeroForWrite(pos);

    byte[] block = blockForWrite(blockIndex(pos));
    int off = offsetInBlock(pos);
    block[off] = b;

    if (pos >= size) {
      size = pos + 1;
    }

    return 1;
  }

  @Override
  public int write(long pos, byte[] b, int off, int len) {
    checkNotNegative(pos, "pos");
    checkPositionIndexes(off, off + len, b.length);

    zeroForWrite(pos);

    if (len == 0) {
      return 0;
    }

    int remaining = len;
    int currentOff = off;

    int blockIndex = blockIndex(pos);
    byte[] block = blockForWrite(blockIndex);
    int offInBlock = offsetInBlock(pos);
    int writeLen = Math.min(block.length - offInBlock, remaining);

    System.arraycopy(b, currentOff, block, offInBlock, writeLen);

    remaining -= writeLen;
    currentOff += writeLen;

    while (remaining > 0) {
      block = blockForWrite(++blockIndex);
      writeLen = Math.min(block.length, remaining);

      System.arraycopy(b, currentOff, block, 0, writeLen);

      remaining -= writeLen;
      currentOff += writeLen;
    }

    long newPos = pos + len;
    if (newPos > size) {
      size = newPos;
    }

    return len;
  }

  @Override
  public int write(long pos, ByteBuffer buf) {
    checkNotNegative(pos, "pos");

    zeroForWrite(pos);

    if (!buf.hasRemaining()) {
      return 0;
    }

    int bytesToWrite = buf.remaining();

    int blockIndex = blockIndex(pos);
    byte[] block = blockForWrite(blockIndex);
    int off = offsetInBlock(pos);

    int len = Math.min(block.length - off, buf.remaining());

    buf.get(block, off, len);

    while (buf.hasRemaining()) {
      block = blockForWrite(++blockIndex);
      len = Math.min(block.length, buf.remaining());

      buf.get(block, 0, len);
    }

    if (pos + bytesToWrite > size) {
      size = pos + bytesToWrite;
    }

    return bytesToWrite;
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long pos, long count) throws IOException {
    checkNotNegative(pos, "pos");
    checkNotNegative(count, "count");

    zeroForWrite(pos);

    if (count == 0) {
      return 0;
    }

    long remaining = count;

    int blockIndex = blockIndex(pos);
    byte[] block = blockForWrite(blockIndex);
    int off = offsetInBlock(pos);

    int len = (int) Math.min(block.length - off, remaining);

    int read = src.read(ByteBuffer.wrap(block, off, len));
    if (read == -1) {
      return 0;
    }

    remaining -= read;

    while (remaining > 0) {
      block = blockForWrite(++blockIndex);

      len = (int) Math.min(block.length, remaining);

      read = src.read(ByteBuffer.wrap(block, 0, len));
      if (read == -1) {
        break;
      }

      remaining -= read;
    }

    long written = count - remaining;
    long newPos = pos + written;
    if (newPos > size) {
      size = newPos;
    }

    return written;
  }

  @Override
  public int read(long pos) {
    checkNotNegative(pos, "pos");

    if (pos >= size) {
      return -1;
    }

    byte[] block = blockForRead(blockIndex(pos));
    int off = offsetInBlock(pos);
    return UnsignedBytes.toInt(block[off]);
  }

  @Override
  public int read(long pos, byte[] b, int off, int len) {
    checkNotNegative(pos, "pos");
    checkPositionIndexes(off, off + len, b.length);

    int bytesToRead = bytesToRead(pos, len);

    if (bytesToRead > 0) {
      int remaining = bytesToRead;

      int blockIndex = blockIndex(pos);
      byte[] block = blockForRead(blockIndex);
      int offsetInBlock = offsetInBlock(pos);

      int readLen = Math.min(block.length - offsetInBlock, remaining);

      System.arraycopy(block, offsetInBlock, b, off, readLen);

      remaining -= readLen;
      off += readLen;

      while (remaining > 0) {
        block = blockForRead(++blockIndex);

        readLen = Math.min(block.length, remaining);

        System.arraycopy(block, 0, b, off, readLen);

        remaining -= readLen;
        off += readLen;
      }
    }

    return bytesToRead;
  }

  @Override
  public int read(long pos, ByteBuffer buf) {
    checkNotNegative(pos, "pos");

    int bytesToRead = bytesToRead(pos, buf.remaining());

    if (bytesToRead > 0) {
      int remaining = bytesToRead;

      int blockIndex = blockIndex(pos);
      byte[] block = blockForRead(blockIndex);
      int off = offsetInBlock(pos);

      int len = Math.min(block.length - off, remaining);

      buf.put(block, off, len);

      remaining -= len;

      while (remaining > 0) {
        block = blockForRead(++blockIndex);

        len = Math.min(block.length, remaining);

        buf.put(block, 0, len);

        remaining -= len;
      }
    }

    return bytesToRead;
  }

  @Override
  public long transferTo(long pos, long count, WritableByteChannel dest) throws IOException {
    checkNotNegative(pos, "pos");
    checkNotNegative(count, "count");

    long bytesToRead = bytesToRead(pos, count);

    if (bytesToRead > 0) {
      long remaining = bytesToRead;

      int blockIndex = blockIndex(pos);
      byte[] block = blockForRead(blockIndex);
      int off = offsetInBlock(pos);

      int len = (int) Math.min(block.length - off, remaining);

      ByteBuffer buf = ByteBuffer.wrap(block, off, len);
      while (buf.hasRemaining()) {
        dest.write(buf);
      }

      remaining -= len;

      while (remaining > 0) {
        block = blockForRead(++blockIndex);

        len = (int) Math.min(block.length, remaining);

        buf = ByteBuffer.wrap(block, 0, len);
        while (buf.hasRemaining()) {
          dest.write(buf);
        }

        remaining -= len;
      }
    }

    return Math.max(bytesToRead, 0); // don't return -1 for this method
  }

  /**
   * Returns the index in blocks of the block containing the given position in the file.
   */
  private int blockIndex(long pos) {
    return (int) (pos / disk.blockSize());
  }

  /**
   * Returns the offset of the given position in the file within the block that contains it.
   */
  private int offsetInBlock(long pos) {
    return (int) (pos % disk.blockSize());
  }

  /**
   * Gets the block containing the given position.
   */
  private byte[] blockForRead(int index) {
    return blocks.get(index);
  }

  /**
   * Gets the block at the given index, expanding to create the block if necessary.
   */
  private byte[] blockForWrite(int index) {
    int additionalBlocksNeeded = index - blocks.size() + 1;
    expandBlocks(additionalBlocksNeeded);

    return blocks.get(index);
  }

  private void expandBlocks(int additionalBlocksNeeded) {
    disk.alloc(blocks, additionalBlocksNeeded);
  }

  /**
   * Returns the number of bytes that can be read starting at position {@code pos} (up to a maximum
   * of {@code max}) or -1 if {@code pos} is greater than or equal to the current size.
   */
  private long bytesToRead(long pos, long max) {
    long available = size - pos;
    if (available <= 0) {
      return -1;
    }
    return Math.min(available, max);
  }

  /**
   * Returns the number of bytes that can be read starting at position {@code pos} (up to a maximum
   * of {@code max}) or -1 if {@code pos} is greater than or equal to the current size.
   */
  private int bytesToRead(long pos, int max) {
    return (int) bytesToRead(pos, (long) max);
  }
}
