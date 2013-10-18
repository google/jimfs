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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A mutable, resizable store for bytes.
 *
 * @author Colin Decker
 */
abstract class ByteStore implements FileContent {

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * Returns the read lock for this store.
   */
  protected final Lock readLock() {
    return lock.readLock();
  }

  /**
   * Returns the write lock for this store.
   */
  protected final Lock writeLock() {
    return lock.writeLock();
  }

  /**
   * Gets the current size of this store in bytes. Does not do locking, so should only be called
   * when holding a lock.
   */
  public abstract long currentSize();

  /**
   * Creates a copy of this byte store.
   */
  protected abstract ByteStore createCopy();

  // need to lock in these methods since they're defined by an interface

  @Override
  public final long size() {
    readLock().lock();
    try {
      return currentSize();
    } finally {
      readLock().unlock();
    }
  }

  @Override
  public final ByteStore copy() {
    readLock().lock();
    try {
      return createCopy();
    } finally {
      readLock().unlock();
    }
  }

  /**
   * Truncates this store to the given {@code size}. If the given size is less than the current size
   * of this store, the size of the store is reduced to the given size and any bytes beyond that
   * size are lost. If the given size is greater than the current size of the store, this method
   * does nothing. Returns {@code true} if this store was modified by the call (its size changed)
   * and {@code false} otherwise.
   */
  public abstract boolean truncate(long size);

  /**
   * Writes the given byte to this store at position {@code pos}. {@code pos} may be greater than
   * the current size of this store, in which case this store is resized and all bytes between the
   * current size and {@code pos} are set to 0. Returns the number of bytes written.
   */
  public abstract int write(long pos, byte b);

  /**
   * Writes {@code len} bytes starting at offset {@code off} in the given byte array to this store
   * starting at position {@code pos}. {@code pos} may be greater than the current size of this
   * store, in which case this store is resized and all bytes between the current size and {@code
   * pos} are set to 0. Returns the number of bytes written.
   */
  public abstract int write(long pos, byte[] b, int off, int len);

  /**
   * Writes all available bytes from buffer {@code buf} to this store starting at position {@code
   * pos}. {@code pos} may be greater than the current size of this store, in which case this store
   * is resized and all bytes between the current size and {@code pos} are set to 0. Returns the
   * number of bytes written.
   */
  public abstract int write(long pos, ByteBuffer buf);

  /**
   * Writes all available bytes from each buffer in {@code bufs}, in order, to this store starting
   * at position {@code pos}. {@code pos} may be greater than the current size of this store, in
   * which case this store is resized and all bytes between the current size and {@code pos} are set
   * to 0. Returns the number of bytes written.
   */
  public long write(long pos, Iterable<ByteBuffer> bufs) {
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
   */
  public abstract long transferFrom(
      ReadableByteChannel src, long pos, long count) throws IOException;

  /**
   * Reads the byte at position {@code pos} in this store as an unsigned integer in the range 0-255.
   * If {@code pos} is greater than or equal to the size of this store, returns -1 instead.
   */
  public abstract int read(long pos);

  /**
   * Reads up to {@code b.length} bytes starting at position {@code pos} in this store to the given
   * byte array. Returns the number of bytes actually read or -1 if {@code pos} is greater than or
   * equal to the size of this store.
   */
  public int read(long pos, byte[] b) {
    return read(pos, b, 0, b.length);
  }

  /**
   * Reads up to {@code len} bytes starting at position {@code pos} in this store to the given byte
   * array starting at offset {@code off}. Returns the number of bytes actually read or -1 if {@code
   * pos} is greater than or equal to the size of this store.
   */
  public abstract int read(long pos, byte[] b, int off, int len);

  /**
   * Reads up to {@code buf.remaining()} bytes starting at position {@code pos} in this store to the
   * given buffer. Returns the number of bytes read or -1 if {@code pos} is greater than or equal to
   * the size of this store.
   */
  public abstract int read(long pos, ByteBuffer buf);

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
  public abstract long transferTo(
      long pos, long count, WritableByteChannel dest) throws IOException;
}
