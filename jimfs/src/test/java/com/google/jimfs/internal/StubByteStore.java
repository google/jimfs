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

import com.google.common.primitives.UnsignedBytes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Fake byte store implementation.
 *
 * @author Colin Decker
 */
public class StubByteStore extends ByteStore {

  private long size;
  private boolean throwException;

  StubByteStore(int initialSize) {
    setSize(initialSize);
  }

  StubByteStore(StubByteStore other) {
    setSize(other.size);
  }

  @Override
  public long size() {
    return size;
  }

  @Override
  public ByteStore createCopy() {
    return new StubByteStore(this);
  }

  @Override
  public void delete() {
  }

  public void setSize(long size) {
    this.size = size;
  }

  public void setThrowException(boolean throwException) {
    this.throwException = throwException;
  }

  @Override
  public boolean truncate(long size) {
    checkThrowException();
    if (size < this.size) {
      setSize(size);
      return true;
    }
    return false;
  }

  @Override
  public int write(long pos, byte b) {
    return write(pos, new byte[]{b}, 0, 1);
  }

  @Override
  public int write(long pos, byte[] b, int off, int len) {
    return write(pos, ByteBuffer.wrap(b, off, len));
  }

  @Override
  public int write(long pos, ByteBuffer buf) {
    checkThrowException();
    int written = buf.remaining();
    setSize(Math.max(size, pos + written));
    buf.position(buf.position() + written);
    return written;
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count)
      throws IOException {
    checkThrowException();
    ByteBuffer buffer = ByteBuffer.allocate((int) count);
    int read = src.read(buffer);
    setSize(Math.max(size, position + read));
    return read;
  }

  @Override
  public int read(long pos) {
    byte[] b = new byte[1];
    if (read(pos, b) != -1) {
      return UnsignedBytes.toInt(b[0]);
    }
    return -1;
  }

  @Override
  public int read(long pos, byte[] b, int off, int len) {
    return read(pos, ByteBuffer.wrap(b, off, len));
  }

  @Override
  public int read(long pos, ByteBuffer buf) {
    checkThrowException();
    int toRead = (int) Math.min(buf.remaining(), size - pos);
    if (toRead <= 0) {
      return -1;
    }
    buf.position(buf.position() + toRead);
    return toRead;
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target)
      throws IOException {
    int toTransfer = (int) (size - position);
    if (toTransfer > 0) {
      target.write(ByteBuffer.allocate(toTransfer));
    }
    return toTransfer;
  }

  @Override
  public long read(long position, Iterable<ByteBuffer> buffers) {
    checkThrowException();
    long toRead = size - position;
    if (toRead == 0) {
      return -1;
    }

    int read = 0;
    for (ByteBuffer buffer : buffers) {
      while (buffer.hasRemaining() && read < toRead) {
        buffer.put((byte) 0);
        read++;
      }
      if (read >= toRead) {
        break;
      }
    }
    return read;
  }

  private void checkThrowException() {
    if (throwException) {
      throw new RuntimeException("error");
    }
  }
}