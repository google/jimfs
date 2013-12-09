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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.primitives.UnsignedBytes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A mutable, resizable store for the bytes of a regular file. Bytes are stored in fixed-sized
 * byte arrays (blocks) allocated by a {@link HeapDisk}.
 *
 * @author Colin Decker
 */
final class ByteStore implements FileContent {

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private final HeapDisk disk;
  private final BlockList blocks;
  private long size;

  public ByteStore(HeapDisk disk) {
    this(disk, new BlockList(32), 0);
  }

  private ByteStore(HeapDisk disk, BlockList blocks, long size) {
    this.disk = checkNotNull(disk);
    this.blocks = checkNotNull(blocks);

    checkArgument(size >= 0);
    this.size = size;
  }

  private int openCount = 0;
  private boolean deleted = false;

  /**
   * Returns the read lock for this store.
   */
  public Lock readLock() {
    return lock.readLock();
  }

  /**
   * Returns the write lock for this store.
   */
  public Lock writeLock() {
    return lock.writeLock();
  }

  /**
   * Gets the current size of this store in bytes. Does not do locking, so should only be called
   * when holding a lock.
   */
  public long sizeWithoutLocking() {
    return size;
  }

  // need to lock in these methods since they're defined by an interface

  @Override
  public long size() {
    readLock().lock();
    try {
      return size;
    } finally {
      readLock().unlock();
    }
  }

  @Override
  public ByteStore copy() throws IOException {
    readLock().lock();
    try {
      BlockList copyBlocks = new BlockList(Math.max(blocks.size() * 2, 32));
      disk.allocate(copyBlocks, blocks.size());

      for (int i = 0; i < blocks.size(); i++) {
        byte[] block = blocks.get(i);
        byte[] copy = copyBlocks.get(i);
        System.arraycopy(block, 0, copy, 0, block.length);
      }
      return new ByteStore(disk, copyBlocks, size);
    } finally {
      readLock().unlock();
    }
  }

  // opened/closed/delete don't use the read/write lock... they only need to ensure that they are
  // synchronized among themselves

  /**
   * Called when a stream or channel to this store is opened.
   */
  public synchronized void opened() {
    openCount++;
  }

  /**
   * Called when a stream or channel to this store is closed. If there are no more streams or
   * channels open to the store and it has been deleted, its contents may be deleted.
   */
  public synchronized void closed() {
    if (--openCount == 0 && deleted) {
      deleteContents();
    }
  }

  @Override
  public void linked(DirectoryEntry entry) {
    checkNotNull(entry); // for NullPointerTester
  }

  @Override
  public void unlinked() {
  }

  /**
   * Marks this store as deleted. If there are no streams or channels open to the store, its
   * contents are deleted if necessary.
   */
  @Override
  public synchronized void deleted() {
    deleted = true;
    if (openCount == 0) {
      deleteContents();
    }
  }

  /**
   * Deletes the contents of this store. Called when the file that contains this store has been
   * deleted and all open streams and channels to the file have been closed.
   */
  private void deleteContents() {
    disk.free(blocks);
    size = 0;
  }

  /**
   * Truncates this store to the given {@code size}. If the given size is less than the current size
   * of this store, the size of the store is reduced to the given size and any bytes beyond that
   * size are lost. If the given size is greater than the current size of the store, this method
   * does nothing. Returns {@code true} if this store was modified by the call (its size changed)
   * and {@code false} otherwise.
   */
  public boolean truncate(long size) {
    if (size >= this.size) {
      return false;
    }

    long lastPosition = size - 1;
    this.size = size;

    int newBlockCount = blockIndex(lastPosition) + 1;
    int blocksToRemove = blocks.size() - newBlockCount;
    if (blocksToRemove > 0) {
      disk.free(blocks, blocksToRemove);
    }

    return true;
  }

  /**
   * Prepares for a write of len bytes starting at position pos.
   */
  private void prepareForWrite(long pos, long len) throws IOException {
    long end = pos + len;

    // allocate any additional blocks needed
    int lastBlockIndex = blocks.size() - 1;
    int endBlockIndex = blockIndex(end - 1);

    if (endBlockIndex > lastBlockIndex) {
      int additionalBlocksNeeded = endBlockIndex - lastBlockIndex;
      disk.allocate(blocks, additionalBlocksNeeded);
    }

    // zero bytes between current size and pos
    if (pos > size) {
      long remaining = pos - size;

      int blockIndex = blockIndex(size);
      byte[] block = blocks.get(blockIndex);
      int off = offsetInBlock(size);

      remaining -= zero(block, off, length(off, remaining));

      while (remaining > 0) {
        block = blocks.get(++blockIndex);

        remaining -= zero(block, 0, length(remaining));
      }

      size = pos;
    }
  }

  /**
   * Writes the given byte to this store at position {@code pos}. {@code pos} may be greater than
   * the current size of this store, in which case this store is resized and all bytes between the
   * current size and {@code pos} are set to 0. Returns the number of bytes written.
   *
   * @throws IOException if the store needs more blocks but the disk is full
   */
  public int write(long pos, byte b) throws IOException {
    prepareForWrite(pos, 1);

    byte[] block = blocks.get(blockIndex(pos));
    int off = offsetInBlock(pos);
    block[off] = b;

    if (pos >= size) {
      size = pos + 1;
    }

    return 1;
  }

  /**
   * Writes {@code len} bytes starting at offset {@code off} in the given byte array to this store
   * starting at position {@code pos}. {@code pos} may be greater than the current size of this
   * store, in which case this store is resized and all bytes between the current size and {@code
   * pos} are set to 0. Returns the number of bytes written.
   *
   * @throws IOException if the store needs more blocks but the disk is full
   */
  public int write(long pos, byte[] b, int off, int len) throws IOException {
    prepareForWrite(pos, len);

    if (len == 0) {
      return 0;
    }

    int remaining = len;

    int blockIndex = blockIndex(pos);
    byte[] block = blocks.get(blockIndex);
    int offInBlock = offsetInBlock(pos);

    int written = put(block, offInBlock, b, off, length(offInBlock, remaining));
    remaining -= written;
    off += written;

    while (remaining > 0) {
      block = blocks.get(++blockIndex);

      written = put(block, 0, b, off, length(remaining));
      remaining -= written;
      off += written;
    }

    long endPos = pos + len;
    if (endPos > size) {
      size = endPos;
    }

    return len;
  }

  /**
   * Writes all available bytes from buffer {@code buf} to this store starting at position {@code
   * pos}. {@code pos} may be greater than the current size of this store, in which case this store
   * is resized and all bytes between the current size and {@code pos} are set to 0. Returns the
   * number of bytes written.
   *
   * @throws IOException if the store needs more blocks but the disk is full
   */
  public int write(long pos, ByteBuffer buf) throws IOException {
    int len = buf.remaining();

    prepareForWrite(pos, len);

    if (len == 0) {
      return 0;
    }

    int blockIndex = blockIndex(pos);
    byte[] block = blocks.get(blockIndex);
    int off = offsetInBlock(pos);

    put(block, off, buf);

    while (buf.hasRemaining()) {
      block = blocks.get(++blockIndex);

      put(block, 0, buf);
    }

    long endPos = pos + len;
    if (endPos > size) {
      size = endPos;
    }

    return len;
  }

  /**
   * Writes all available bytes from each buffer in {@code bufs}, in order, to this store starting
   * at position {@code pos}. {@code pos} may be greater than the current size of this store, in
   * which case this store is resized and all bytes between the current size and {@code pos} are set
   * to 0. Returns the number of bytes written.
   *
   * @throws IOException if the store needs more blocks but the disk is full
   */
  public long write(long pos, Iterable<ByteBuffer> bufs) throws IOException {
    long start = pos;
    for (ByteBuffer buf : bufs) {
      pos += write(pos, buf);
    }
    return pos - start;
  }

  /**
   * Transfers up to {@code count} bytes from the given channel to this store starting at position
   * {@code pos}. Returns the number of bytes transferred. If {@code pos} is greater than the
   * current size of this store, the store is truncated up to size {@code pos} before writing.
   *
   * @throws IOException if the store needs more blocks but the disk is full or if reading from src
   *     throws an exception
   */
  public long transferFrom(
      ReadableByteChannel src, long pos, long count) throws IOException {
    prepareForWrite(pos, 0); // don't assume the full count bytes will be written

    if (count == 0) {
      return 0;
    }

    long remaining = count;

    int blockIndex = blockIndex(pos);
    byte[] block = blockForWrite(blockIndex);
    int off = offsetInBlock(pos);

    ByteBuffer buf = ByteBuffer.wrap(block, off, length(off, remaining));

    long currentPos = pos;
    int read = 0;
    while (buf.hasRemaining()) {
      read = src.read(buf);
      if (read == -1) {
        break;
      }

      currentPos += read;
      remaining -= read;
    }

    // update size before trying to get next block in case the disk is out of space
    if (currentPos > size) {
      size = currentPos;
    }

    if (read != -1) {
      outer: while (remaining > 0) {
        block = blockForWrite(++blockIndex);

        buf = ByteBuffer.wrap(block, 0, length(remaining));
        while (buf.hasRemaining()) {
          read = src.read(buf);
          if (read == -1) {
            break outer;
          }

          currentPos += read;
          remaining -= read;
        }

        if (currentPos > size) {
          size = currentPos;
        }
      }
    }

    if (currentPos > size) {
      size = currentPos;
    }

    return currentPos - pos;
  }

  /**
   * Reads the byte at position {@code pos} in this store as an unsigned integer in the range 0-255.
   * If {@code pos} is greater than or equal to the size of this store, returns -1 instead.
   */
  public int read(long pos) {
    if (pos >= size) {
      return -1;
    }

    byte[] block = blocks.get(blockIndex(pos));
    int off = offsetInBlock(pos);
    return UnsignedBytes.toInt(block[off]);
  }

  /**
   * Reads up to {@code len} bytes starting at position {@code pos} in this store to the given byte
   * array starting at offset {@code off}. Returns the number of bytes actually read or -1 if {@code
   * pos} is greater than or equal to the size of this store.
   */
  public int read(long pos, byte[] b, int off, int len) {
    // since max is len (an int), result is guaranteed to be an int
    int bytesToRead = (int) bytesToRead(pos, len);

    if (bytesToRead > 0) {
      int remaining = bytesToRead;

      int blockIndex = blockIndex(pos);
      byte[] block = blocks.get(blockIndex);
      int offsetInBlock = offsetInBlock(pos);

      int read = get(block, offsetInBlock, b, off, length(offsetInBlock, remaining));
      remaining -= read;
      off += read;

      while (remaining > 0) {
        int index = ++blockIndex;
        block = blocks.get(index);

        read = get(block, 0, b, off, length(remaining));
        remaining -= read;
        off += read;
      }
    }

    return bytesToRead;
  }

  /**
   * Reads up to {@code buf.remaining()} bytes starting at position {@code pos} in this store to the
   * given buffer. Returns the number of bytes read or -1 if {@code pos} is greater than or equal to
   * the size of this store.
   */
  public int read(long pos, ByteBuffer buf) {
    // since max is buf.remaining() (an int), result is guaranteed to be an int
    int bytesToRead = (int) bytesToRead(pos, buf.remaining());

    if (bytesToRead > 0) {
      int remaining = bytesToRead;

      int blockIndex = blockIndex(pos);
      byte[] block = blocks.get(blockIndex);
      int off = offsetInBlock(pos);

      remaining -= get(block, off, buf, length(off, remaining));

      while (remaining > 0) {
        int index = ++blockIndex;
        block = blocks.get(index);
        remaining -= get(block, 0, buf, length(remaining));
      }
    }

    return bytesToRead;
  }

  /**
   * Reads up to the total {@code remaining()} number of bytes in each of {@code bufs} starting at
   * position {@code pos} in this store to the given buffers, in order. Returns the number of bytes
   * read or -1 if {@code pos} is greater than or equal to the size of this store.
   */
  public long read(long pos, Iterable<ByteBuffer> bufs) {
    if (pos >= size()) {
      return -1;
    }

    long start = pos;
    for (ByteBuffer buf : bufs) {
      int read = read(pos, buf);
      if (read == -1) {
        break;
      } else {
        pos += read;
      }
    }

    return pos - start;
  }

  /**
   * Transfers up to {@code count} bytes to the given channel starting at position {@code pos} in
   * this store. Returns the number of bytes transferred, possibly 0. Note that unlike all other
   * read methods in this class, this method does not return -1 if {@code pos} is greater than or
   * equal to the current size. This for consistency with {@link FileChannel#transferTo}, which
   * this method is primarily intended as an implementation of.
   */
  public long transferTo(
      long pos, long count, WritableByteChannel dest) throws IOException {
    long bytesToRead = bytesToRead(pos, count);

    if (bytesToRead > 0) {
      long remaining = bytesToRead;

      int blockIndex = blockIndex(pos);
      byte[] block = blocks.get(blockIndex);
      int off = offsetInBlock(pos);

      ByteBuffer buf = ByteBuffer.wrap(block, off, length(off, remaining));
      while (buf.hasRemaining()) {
        remaining -= dest.write(buf);
      }
      buf.clear();

      while (remaining > 0) {
        int index = ++blockIndex;
        block = blocks.get(index);

        buf = ByteBuffer.wrap(block, 0, length(remaining));
        while (buf.hasRemaining()) {
          remaining -= dest.write(buf);
        }
        buf.clear();
      }
    }

    return Math.max(bytesToRead, 0); // don't return -1 for this method
  }

  /**
   * Gets the block at the given index, expanding to create the block if necessary.
   */
  private byte[] blockForWrite(int index) throws IOException {
    int blockCount = blocks.size();
    if (index >= blockCount) {
      int additionalBlocksNeeded = index - blockCount + 1;
      disk.allocate(blocks, additionalBlocksNeeded);
    }

    return blocks.get(index);
  }

  private int blockIndex(long position) {
    return (int) (position / disk.blockSize());
  }

  private int offsetInBlock(long position) {
    return (int) (position % disk.blockSize());
  }

  private int length(long max) {
    return (int) Math.min(disk.blockSize(), max);
  }

  private int length(int off, long max) {
    return (int) Math.min(disk.blockSize() - off, max);
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
   * Zeroes len bytes in the given block starting at the given offset. Returns len.
   */
  private static int zero(byte[] block, int offset, int len) {
    Util.zero(block, offset, len);
    return len;
  }

  /**
   * Puts the given slice of the given array at the given offset in the given block.
   */
  private static int put(byte[] block, int offset, byte[] b, int off, int len) {
    System.arraycopy(b, off, block, offset, len);
    return len;
  }

  /**
   * Puts the contents of the given byte buffer at the given offset in the given block.
   */
  private static int put(byte[] block, int offset, ByteBuffer buf) {
    int len = Math.min(block.length - offset, buf.remaining());
    buf.get(block, offset, len);
    return len;
  }

  /**
   * Reads len bytes starting at the given offset in the given block into the given slice of the
   * given byte array.
   */
  private static int get(byte[] block, int offset, byte[] b, int off, int len) {
    System.arraycopy(block, offset, b, off, len);
    return len;
  }

  /**
   * Reads len bytes starting at the given offset in the given block into the given byte buffer.
   */
  private static int get(byte[] block, int offset, ByteBuffer buf, int len) {
    buf.put(block, offset, len);
    return len;
  }
}
