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
import static com.google.jimfs.internal.Util.nextPowerOf2;

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
final class DirectByteStore extends ByteStore {

  private static final int INITIAL_BUFFER_SIZE = 4096;
  private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 10;

  private ByteBuffer buffer;

  public DirectByteStore() {
    this((ByteBuffer) ByteBuffer.allocateDirect(INITIAL_BUFFER_SIZE).flip());
  }

  private DirectByteStore(ByteBuffer buffer) {
    this.buffer = buffer;
    this.buffer.mark();
  }

  @Override
  public long size() {
    return buffer.limit();
  }

  @Override
  public ByteStore createCopy() {
    return new DirectByteStore(copyBuffer(buffer.limit()));
  }

  @Override
  public void delete() {
  }

  /**
   * Returns a buffer sized to hold at least {@code minSize} bytes.
   */
  private static ByteBuffer createBuffer(int minSize) {
    int newSize = nextPowerOf2(minSize);

    // if the new size overflows or would be too large to create an array
    if (newSize < 0 || newSize > MAX_BUFFER_SIZE) {
      // if the minimum size overflowed or is too large, have to throw
      if (minSize < 0 || minSize > MAX_BUFFER_SIZE) {
        throw new OutOfMemoryError();
      }

      // otherwise, just use the maximum array size
      newSize = MAX_BUFFER_SIZE;
    }

    // don't create an array smaller than the default initial array size
    return ByteBuffer.allocateDirect(Math.max(newSize, INITIAL_BUFFER_SIZE));
  }

  /**
   * Returns a buffer containing the current content of this store.
   */
  private ByteBuffer copyBuffer(int minSize) {
    ByteBuffer copy = createBuffer(minSize);
    copy.put(buffer);
    buffer.flip();
    copy.flip();
    return copy;
  }

  @Override
  public boolean truncate(long size) {
    checkNotNegative(size, "size");

    if (size >= size()) {
      return false;
    }

    // the new size isn't larger than the current size, so it must be an int
    buffer.limit((int) size);
    return true;
  }

  private void resizeBuffer(int minSize) {
    buffer = copyBuffer(minSize);
  }

  private void resizeForWrite(long minSize) {
    int intMinSize = checkNotTooLarge(minSize);
    if (intMinSize > size()) {
      if (intMinSize > buffer.capacity()) {
        resizeBuffer(intMinSize);
      }
      buffer.limit(intMinSize);
    }
  }

  @Override
  public int write(long pos, byte b) {
    checkNotNegative(pos, "pos");

    resizeForWrite(pos + 1);

    buffer.put((int) pos, b);
    return 1;
  }

  @Override
  public int write(long pos, byte[] b, int off, int len) {
    checkNotNegative(pos, "pos");
    checkPositionIndexes(off, off + len, b.length);

    int originalLimit = buffer.limit();
    int limit = (int) pos + len;
    resizeForWrite(limit);

    buffer.position((int) pos);
    buffer.limit(limit);
    try {
      buffer.put(b, off, len);
    } finally {
      buffer.position(0);
      if (originalLimit > buffer.limit()) {
        buffer.limit(originalLimit);
      }
    }
    return len;
  }

  @Override
  public int write(long pos, ByteBuffer buf) {
    checkNotNegative(pos, "pos");

    int len = buf.remaining();
    resizeForWrite(pos + len);

    buffer.position((int) pos);
    try {
      buffer.put(buf);
    } finally {
      buffer.position(0);
    }
    return len;
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long pos, long count) throws IOException {
    checkNotNegative(pos, "pos");
    checkNotNegative(count, "count");

    if (count == 0) {
      return 0;
    }

    int originalSize = buffer.limit();
    long limit = pos + count;
    resizeForWrite(pos + count);
    buffer.position((int) pos);
    buffer.limit((int) limit);
    try {

      int read = 0;
      while (read >= 0 && buffer.hasRemaining()) {
        read = src.read(buffer);
      }

      int bytesTransferred = buffer.position() - (int) pos;

      buffer.position(0);

      // reset size to the correct size, since fewer than count bytes may have been transferred
      buffer.limit(Math.max(originalSize, (int) pos + bytesTransferred));

      return bytesTransferred;
    } catch (Throwable e) {
      // if there was an exception copying from src into the buffer, set the size back to the
      // original size... the array may be corrupted in the area that was being copied to
      buffer.position(0);
      truncate(originalSize);
      throw e;
    }
  }

  @Override
  public int read(long pos) {
    if (pos >= buffer.limit()) {
      return -1;
    }

    return UnsignedBytes.toInt(buffer.get((int) pos));
  }

  @Override
  public int read(long pos, byte[] b, int off, int len) {
    checkNotNegative(pos, "pos");
    checkPositionIndexes(off, off + len, b.length);

    int bytesToRead = bytesToRead(pos, len);
    if (bytesToRead > 0) {
      ByteBuffer duplicate = buffer.duplicate();
      duplicate.position((int) pos);
      duplicate.get(b, off, bytesToRead);
    }
    return bytesToRead;
  }

  @Override
  public int read(long pos, ByteBuffer buf) {
    checkNotNegative(pos, "pos");

    int bytesToRead = bytesToRead(pos, buf.remaining());
    if (bytesToRead > 0) {
      ByteBuffer duplicate = buffer.duplicate();
      duplicate.position((int) pos);
      duplicate.limit((int) pos + bytesToRead);
      buf.put(duplicate);
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

    int bytesToRead = bytesToRead(pos, count);
    if (bytesToRead > 0) {
      ByteBuffer duplicate = buffer.duplicate();
      duplicate.position((int) pos);
      duplicate.limit((int) pos + bytesToRead);
      while (duplicate.hasRemaining()) {
        dest.write(duplicate);
      }
    }
    return Math.max(bytesToRead, 0); // don't return -1 for this method
  }

  /**
   * Returns the number of bytes that can be read starting at position {@code pos} (up to a maximum
   * of {@code max}) or -1 if {@code pos} is greater than or equal to the current size.
   */
  private int bytesToRead(long pos, long max) {
    int available = buffer.limit() - (int) pos;
    if (available <= 0) {
      return -1;
    }
    return (int) Math.min(available, max);
  }

  private static int checkNotTooLarge(long size) {
    if (size > MAX_BUFFER_SIZE) {
      throw new IllegalArgumentException("size " + size + " is too large to store in an array");
    }

    return (int) size;
  }
}
