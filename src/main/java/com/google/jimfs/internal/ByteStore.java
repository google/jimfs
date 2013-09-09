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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A mutable, resizable store for bytes.
 *
 * @author Colin Decker
 */
abstract class ByteStore implements FileContent {

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

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
   * Gets the current size of this store in bytes. The size is the total number of bytes that
   * can be read from the store.
   */
  @Override
  public abstract int sizeInBytes();

  /**
   * Creates a copy of this byte store.
   */
  @Override
  public abstract ByteStore copy();

  /**
   * Truncates this store to the given {@code size}. If the given size is less than the current
   * size of this store, the size of the store is reduced to the given size and any bytes beyond
   * that size are lost. If the given size is greater than the current size of the store, this
   * method does nothing. Returns {@code true} if this store was modified by the call (its size
   * changed) and {@code false} otherwise.
   *
   * @throws IllegalArgumentException if {@code size} is negative.
   */
  public abstract boolean truncate(int size);

  /**
   * Writes the given byte to this store at position {@code pos}. {@code pos} may be greater than
   * the current size of this store, in which case this store is resized and all bytes between the
   * current size and {@code pos} are set to 0. Returns the number of bytes written.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   */
  public abstract int write(int pos, byte b);

  /**
   * Writes all bytes in the given byte array to this store starting at position {@code pos}.
   * {@code pos} may be greater than the current size of this store, in which case this store is
   * resized and all bytes between the current size and {@code pos} are set to 0. Returns the number
   * of bytes written.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   */
  public int write(int pos, byte[] b) {
    return write(pos, b, 0, b.length);
  }

  /**
   * Writes {@code len} bytes starting at offset {@code off} in the given byte array to this store
   * starting at position {@code pos}. {@code pos} may be greater than the current size of this
   * store, in which case this store is resized and all bytes between the current size and
   * {@code pos} are set to 0. Returns the number of bytes written.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   * @throws IndexOutOfBoundsException if {@code off} or {@code len} is negative, or if
   *     {@code off + len} is greater than {@code b.length}.
   */
  public abstract int write(int pos, byte[] b, int off, int len);

  /**
   * Writes all available bytes from buffer {@code buf} to this store starting at position
   * {@code pos}. {@code pos} may be greater than the current size of this store, in which case
   * this store is resized and all bytes between the current size and {@code pos} are set to 0.
   * Returns the number of bytes written.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   */
  public abstract int write(int pos, ByteBuffer buf);

  /**
   * Writes all available bytes from each buffer in {@code bufs}, in order, to this store starting
   * at position {@code pos}. {@code pos} may be greater than the current size of this store, in
   * which case this store is resized and all bytes between the current size and {@code pos} are
   * set to 0. Returns the number of bytes written.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   * @throws NullPointerException if any element of {@code bufs} is {@code null}.
   */
  public int write(int pos, Iterable<ByteBuffer> bufs) {
    checkNotNegative(pos, "pos");
    for (ByteBuffer buf : bufs) {
      checkNotNull(buf);
    }

    writeLock().lock();
    try {
      int start = pos;
      for (ByteBuffer buf : bufs) {
        pos += write(pos, buf);
      }
      return pos - start;
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Transfers up to {@code count} bytes from the given channel to this store starting at position
   * {@code pos}. Returns the number of bytes transferred. If {@code pos} is greater than the
   * current size of this store, the store is truncated up to size {@code pos} before writing.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   */
  public abstract int transferFrom(ReadableByteChannel src, int pos, int count) throws IOException;

  /**
   * Appends the given byte to this store. Returns the number of bytes written.
   */
  public int append(byte b) {
    writeLock().lock();
    try {
      return write(sizeInBytes(), b);
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Appends all bytes in the given byte array to this store. Returns the number of bytes written.
   */
  public int append(byte[] b) {
    writeLock().lock();
    try {
      return write(sizeInBytes(), b, 0, b.length);
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Appends {@code len} bytes starting at offset {@code off} in the given byte array to this store.
   * Returns the number of bytes written.
   *
   * @throws IndexOutOfBoundsException if {@code off} or {@code len} is negative, or if
   *     {@code off + len} is greater than {@code b.length}.
   */
  public int append(byte[] b, int off, int len) {
    writeLock().lock();
    try {
      return write(sizeInBytes(), b, off, len);
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Appends all available bytes from buffer {@code buf} to this store. Returns the number of bytes
   * written.
   */
  public int append(ByteBuffer buf) {
    writeLock().lock();
    try {
      return write(sizeInBytes(), buf);
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Appends all available bytes from each buffer in {@code bufs}, in order, to this store. Returns
   * the number of bytes written.
   *
   * @throws NullPointerException if any element of {@code bufs} is {@code null}.
   */
  public int append(Iterable<ByteBuffer> bufs) {
    writeLock().lock();
    try {
      return write(sizeInBytes(), bufs);
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Appends up to {@code count} bytes from the given channel to this store. Returns the number of
   * bytes transferred.
   */
  public int appendFrom(ReadableByteChannel src, int count) throws IOException {
    writeLock().lock();
    try {
      return transferFrom(src, sizeInBytes(), count);
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Reads the byte at position {@code pos} in this store as an unsigned integer in the range 0-255.
   * If {@code pos} is greater than or equal to the size of this store, returns -1 instead.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   */
  public abstract int read(int pos);

  /**
   * Reads up to {@code b.length} bytes starting at position {@code pos} in this store to the given
   * byte array. Returns the number of bytes actually read or -1 if {@code pos} is greater than or
   * equal to the size of this store.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   */
  public int read(int pos, byte[] b) {
    return read(pos, b, 0, b.length);
  }

  /**
   * Reads up to {@code len} bytes starting at position {@code pos} in this store to the given byte
   * array starting at offset {@code off}. Returns the number of bytes actually read or -1 if
   * {@code pos} is greater than or equal to the size of this store.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   * @throws IndexOutOfBoundsException if {@code off} or {@code len} is negative or if
   *     {@code off + len} is greater than {@code b.length}.
   */
  public abstract int read(int pos, byte[] b, int off, int len);

  /**
   * Reads up to {@code buf.remaining()} bytes starting at position {@code pos} in this store to the
   * given buffer. Returns the number of bytes read or -1 if {@code pos} is greater than or equal to
   * the size of this store.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   */
  public abstract int read(int pos, ByteBuffer buf);

  /**
   * Reads up to the total {@code remaining()} number of bytes in each of {@code bufs} starting at
   * position {@code pos} in this store to the given buffers, in order. Returns the number of bytes
   * read or -1 if {@code pos} is greater than or equal to the size of this store.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   * @throws NullPointerException if any element of {@code bufs} is {@code null}.
   */
  public int read(int pos, Iterable<ByteBuffer> bufs) {
    checkNotNegative(pos, "pos");
    for (ByteBuffer buf : bufs) {
      checkNotNull(buf);
    }

    readLock().lock();
    try {
      if (pos >= sizeInBytes()) {
        return -1;
      }

      int start = pos;
      for (ByteBuffer buf : bufs) {
        int read = read(pos, buf);
        if (read == -1) {
          break;
        } else {
          pos += read;
        }
      }

      return pos - start;
    } finally {
      readLock().unlock();
    }
  }

  /**
   * Transfers up to {@code count} bytes to the given channel starting at position {@code pos} in
   * this store. If {@code count} is negative, no bytes are transferred. Returns the number of
   * bytes transferred, possibly 0. Note that unlike all other read methods in this class, this
   * method does not return -1 if {@code pos} is greater than or equal to the current size. This
   * for consistency with {@link FileChannel#transferTo}, which this method is primarily intended
   * as an implementation of.
   *
   * @throws IllegalArgumentException if {@code pos} is negative.
   */
  public abstract int transferTo(int pos, int count, WritableByteChannel dest) throws IOException;

  /**
   * Check that the given value is not negative, throwing an {@code IllegalArgumentException} if it
   * is.
   */
  protected static void checkNotNegative(int n, String description) {
    if (n < 0) {
      throw new IllegalArgumentException(description + " (" + n + ") may not be negative");
    }
  }
}
