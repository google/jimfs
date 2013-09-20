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
import static com.google.jimfs.internal.Disk.BlockQueue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Byte store backed by a {@link Disk}.
 *
 * @author Colin Decker
 */
final class DiskByteStore extends ByteStore {

  private final Disk disk;
  private final BlockQueue blocks;
  private long size;

  public DiskByteStore(Disk disk) {
    this(disk, new BlockQueue(32), 0);
  }

  private DiskByteStore(Disk disk, BlockQueue blocks, long size) {
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
    BlockQueue copyBlocks = new BlockQueue(blocks.size() * 2);
    for (int i = 0; i < blocks.size(); i++) {
      long block = blocks.get(i);
      long copy = disk.alloc();
      disk.copy(block, copy);
      copyBlocks.add(copy);
    }
    return new DiskByteStore(disk, copyBlocks, size);
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
    long lastPosition = size - 1;
    if (size < this.size) {
      this.size = size;

      int newBlockCount = blockIndex(lastPosition) + 1;
      int blocksToRemove = blocks.size() - newBlockCount;
      if (blocksToRemove > 0) {
        BlockQueue blocksToFree = new BlockQueue(blocksToRemove);
        for (int i = 0; i < blocksToRemove; i++) {
          blocksToFree.add(blocks.take()); // removing blocks from the end
        }
        disk.free(blocks);
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
    if (pos > size) {
      long currentPos = size;
      long remaining = pos - size;
      while (remaining > 0) {
        long block = blockForWrite(currentPos);
        int off = indexInBlock(currentPos);
        int lenToZero = (int) Math.min(disk.blockSize() - off, remaining);
        disk.zero(block, off, lenToZero);
        currentPos += lenToZero;
        remaining -= lenToZero;
      }

      size = pos;
    }
  }

  @Override
  public int write(long pos, byte b) {
    checkNotNegative(pos, "pos");

    zeroForWrite(pos);

    disk.put(blockForWrite(pos), indexInBlock(pos), b);

    if (pos + 1 > size) {
      size = pos + 1;
    }

    return 1;
  }

  @Override
  public int write(long pos, byte[] b, int off, int len) {
    checkNotNegative(pos, "pos");
    checkPositionIndexes(off, off + len, b.length);

    zeroForWrite(pos);

    int remaining = len;
    long currentPos = pos;
    int currentOff = off;
    while (remaining > 0) {
      long block = blockForWrite(currentPos);

      int written = disk.put(block, indexInBlock(currentPos), b, currentOff, remaining);
      remaining -= written;
      currentPos += written;
      currentOff += written;
    }

    if (pos + len > size) {
      size = pos + len;
    }

    return len;
  }

  @Override
  public int write(long pos, ByteBuffer buf) {
    checkNotNegative(pos, "pos");

    zeroForWrite(pos);

    int bytesToWrite = buf.remaining();
    long currentPos = pos;
    while (buf.hasRemaining()) {
      long block = blockForWrite(currentPos);
      currentPos += disk.put(block, indexInBlock(currentPos), buf);
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


    long currentPos = pos;
    long remaining = count;
    while (remaining > 0) {
      long block = blockForWrite(currentPos);

      ByteBuffer buffer = disk.asByteBuffer(block);
      buffer.position(indexInBlock(currentPos));
      if (buffer.remaining() > remaining) {
        buffer.limit((int) (buffer.position() + remaining));
      }

      int read = src.read(buffer);
      if (read == -1) {
        break;
      }

      currentPos += read;
      remaining -= read;
    }

    if (currentPos > size) {
      size = currentPos;
    }

    return count - remaining;
  }

  @Override
  public int read(long pos) {
    checkNotNegative(pos, "pos");

    if (pos >= size) {
      return -1;
    }

    return disk.get(blockForRead(pos), indexInBlock(pos));
  }

  @Override
  public int read(long pos, byte[] b, int off, int len) {
    checkNotNegative(pos, "pos");
    checkPositionIndexes(off, off + len, b.length);

    int bytesToRead = bytesToRead(pos, len);
    if (bytesToRead > 0) {
      int currentRelativePos = 0;
      int remaining = bytesToRead - currentRelativePos;

      while (remaining > 0) {
        long currentPos = pos + currentRelativePos;
        int currentOff = off + currentRelativePos;

        long block = blockForRead(currentPos);
        int indexInBlock = indexInBlock(currentPos);
        int read = disk.get(block, indexInBlock, b, currentOff, remaining);
        currentRelativePos += read;
        remaining -= read;
      }
    }
    return bytesToRead;
  }

  @Override
  public int read(long pos, ByteBuffer buf) {
    checkNotNegative(pos, "pos");

    int bytesToRead = bytesToRead(pos, buf.remaining());
    if (bytesToRead > 0) {
      int currentRelativePos = 0;
      int remaining = bytesToRead;

      while (remaining > 0) {
        long currentPos = pos + currentRelativePos;

        long block = blockForRead(currentPos);
        int indexInBlock = indexInBlock(currentPos);
        int read = disk.get(block, indexInBlock, buf, remaining);
        currentRelativePos += read;
        remaining -= read;
      }
    }
    return bytesToRead;
  }

  @Override
  public long transferTo(long pos, long count, WritableByteChannel dest) throws IOException {
    checkNotNegative(pos, "pos");
    checkNotNegative(count, "count");

    if (count == 0) {
      return 0;
    }

    long bytesToRead = bytesToRead(pos, count);
    if (bytesToRead > 0) {
      int currentRelativePos = 0;
      long remaining = bytesToRead;

      while (remaining > 0) {
        long currentPos = pos + currentRelativePos;

        long block = blockForRead(currentPos);
        int indexInBlock = indexInBlock(currentPos);

        ByteBuffer buf = disk.asByteBuffer(block);
        buf.position(indexInBlock);

        int bytesToReadFromBlock = (int) Math.min(remaining, buf.remaining());
        buf.limit(indexInBlock + bytesToReadFromBlock);

        while (buf.hasRemaining()) {
          dest.write(buf);
        }

        currentRelativePos += bytesToReadFromBlock;
        remaining -= bytesToReadFromBlock;
      }
    }
    return Math.max(bytesToRead, 0); // don't return -1 for this method
  }

  /**
   * Gets the block containing the given position. May return a proxy for empty blocks. Should not
   * be called if pos >= size.
   */
  private long blockForRead(long pos) {
    return blocks.get(blockIndex(pos));
  }

  /**
   * Gets the block at the given position, expanding to create the block if necessary. Must return
   * an actual writable block.
   */
  private long blockForWrite(long pos) {
    int index = blockIndex(pos);

    int additionalBlocksNeeded = (index + 1) - blocks.size();
    expandBlocks(additionalBlocksNeeded);

    return blocks.get(index);
  }

  private void expandBlocks(int additionalBlocksNeeded) {
    for (int i = 0; i < additionalBlocksNeeded; i++) {
      // sparse-ish allocation... don't allocate a new block until we actually need to write to it
      blocks.add(disk.alloc());
    }
  }

  private int blockIndex(long position) {
    return (int) (position / disk.blockSize());
  }

  private int indexInBlock(long position) {
    return (int) (position % disk.blockSize());
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
