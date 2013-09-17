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

import static com.google.common.base.Preconditions.checkPositionIndexes;

import com.google.common.primitives.UnsignedBytes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * {@link ByteStore} implemented with a byte array that doubles in size when it needs to expand.
 *
 * @author Colin Decker
 */
final class ArrayByteStore extends ByteStore {

  private static final int INITIAL_ARRAY_SIZE = 128;
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 10;

  private int size;
  private byte[] bytes;

  public ArrayByteStore() {
    this(new byte[INITIAL_ARRAY_SIZE], 0);
  }

  private ArrayByteStore(byte[] bytes, int size) {
    this.bytes = bytes;
    this.size = size;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public ByteStore createCopy() {
    return new ArrayByteStore(copyArray(size), size);
  }

  /**
   * Returns a byte array sized to hold at least {@code minSize} bytes.
   */
  private static byte[] createArray(int minSize) {
    int newSize = nextPowerOf2(minSize);

    // if the new size overflows or would be too large to create an array
    if (newSize < 0 || newSize > MAX_ARRAY_SIZE) {
      // if the minimum size overflowed or is too large, have to throw
      if (minSize < 0 || minSize > MAX_ARRAY_SIZE) {
        throw new OutOfMemoryError();
      }

      // otherwise, just use the maximum array size
      newSize = MAX_ARRAY_SIZE;
    }

    // don't create an array smaller than the default initial array size
    return new byte[Math.max(newSize, INITIAL_ARRAY_SIZE)];
  }

  /**
   * Returns the next power of 2 >= n.
   */
  private static int nextPowerOf2(int n) {
    int highestOneBit = Integer.highestOneBit(n);
    return highestOneBit == n ? n : highestOneBit << 1;
  }

  /**
   * Returns a byte array containing the current content of this store.
   */
  private byte[] copyArray(int minSize) {
    byte[] copy = createArray(minSize);
    System.arraycopy(bytes, 0, copy, 0, size);
    return copy;
  }

  @Override
  public boolean truncate(int size) {
    checkNotNegative(size, "size");

    if (size >= this.size) {
      return false;
    }

    this.size = size;
    return true;
  }

  private void resizeArray(int minSize) {
    bytes = copyArray(minSize);
  }

  private void resizeForWrite(int minSize) {
    if (minSize > size) {
      if (minSize > bytes.length) {
        resizeArray(minSize);
      }

      this.size = minSize;
    }
  }

  @Override
  public int write(int pos, byte b) {
    checkNotNegative(pos, "pos");

    resizeForWrite(pos + 1);

    bytes[pos] = b;
    return 1;
  }

  @Override
  public int write(int pos, byte[] b, int off, int len) {
    checkNotNegative(pos, "pos");
    checkPositionIndexes(off, off + len, b.length);

    resizeForWrite(pos + len);

    System.arraycopy(b, off, bytes, pos, len);
    return len;
  }

  @Override
  public int write(int pos, ByteBuffer buf) {
    checkNotNegative(pos, "pos");

    int len = buf.remaining();
    resizeForWrite(pos + len);

    buf.get(bytes, pos, len);
    return len;
  }

  @Override
  public int transferFrom(ReadableByteChannel src, int pos, int count) throws IOException {
    checkNotNegative(pos, "pos");
    checkNotNegative(count, "count");

    if (count == 0) {
      return 0;
    }

    int originalSize = size;
    resizeForWrite(pos + count);
    try {
      // transfer directly into array
      ByteBuffer buffer = ByteBuffer.wrap(bytes, pos, count);

      int read = 0;
      while (read >= 0 && buffer.hasRemaining()) {
        read = src.read(buffer);
      }

      int bytesTransferred = buffer.position() - pos;

      // reset size to the correct size, since fewer than count bytes may have been transferred
      size = Math.max(originalSize, pos + bytesTransferred);

      return bytesTransferred;
    } catch (Throwable e) {
      // if there was an exception copying from src into the array, set the size back to the
      // original size... the array may be corrupted in the area that was being copied to
      truncate(originalSize);
      throw e;
    }
  }

  @Override
  public int read(int pos) {
    if (pos >= size) {
      return -1;
    }

    return UnsignedBytes.toInt(bytes[pos]);
  }

  @Override
  public int read(int pos, byte[] b, int off, int len) {
    checkNotNegative(pos, "pos");
    checkPositionIndexes(off, off + len, b.length);

    int bytesToRead = bytesToRead(pos, len);
    if (bytesToRead > 0) {
      System.arraycopy(bytes, pos, b, off, bytesToRead);
    }
    return bytesToRead;
  }

  @Override
  public int read(int pos, ByteBuffer buf) {
    checkNotNegative(pos, "pos");

    int bytesToRead = bytesToRead(pos, buf.remaining());
    if (bytesToRead > 0) {
      buf.put(bytes, pos, bytesToRead);
    }
    return bytesToRead;
  }

  @Override
  public int transferTo(int pos, int count, WritableByteChannel dest) throws IOException {
    checkNotNegative(pos, "pos");
    checkNotNegative(count, "count");

    if (count == 0) {
      return 0;
    }

    int bytesToRead = bytesToRead(pos, count);
    if (bytesToRead > 0) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes, pos, bytesToRead);
      while (buffer.hasRemaining()) {
        dest.write(buffer);
      }
    }
    return Math.max(bytesToRead, 0); // don't return -1 for this method
  }

  /**
   * Returns the number of bytes that can be read starting at position {@code pos} (up to a maximum
   * of {@code max}) or -1 if {@code pos} is greater than or equal to the current size.
   */
  private int bytesToRead(int pos, int max) {
    int available = size - pos;
    if (available <= 0) {
      return -1;
    }
    return Math.min(available, max);
  }
}
