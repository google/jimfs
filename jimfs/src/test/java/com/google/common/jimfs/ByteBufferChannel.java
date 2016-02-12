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

package com.google.common.jimfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * @author Colin Decker
 */
public class ByteBufferChannel implements SeekableByteChannel {

  private final ByteBuffer buffer;

  public ByteBufferChannel(byte[] bytes) {
    this.buffer = ByteBuffer.wrap(bytes);
  }

  public ByteBufferChannel(byte[] bytes, int offset, int length) {
    this.buffer = ByteBuffer.wrap(bytes, offset, length);
  }

  public ByteBufferChannel(int capacity) {
    this.buffer = ByteBuffer.allocate(capacity);
  }

  public ByteBufferChannel(ByteBuffer buffer) {
    this.buffer = buffer;
  }

  public ByteBuffer buffer() {
    return buffer;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    if (buffer.remaining() == 0) {
      return -1;
    }
    int length = Math.min(dst.remaining(), buffer.remaining());
    for (int i = 0; i < length; i++) {
      dst.put(buffer.get());
    }
    return length;
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    int length = Math.min(src.remaining(), buffer.remaining());
    for (int i = 0; i < length; i++) {
      buffer.put(src.get());
    }
    return length;
  }

  @Override
  public long position() throws IOException {
    return buffer.position();
  }

  @Override
  public SeekableByteChannel position(long newPosition) throws IOException {
    buffer.position((int) newPosition);
    return this;
  }

  @Override
  public long size() throws IOException {
    return buffer.limit();
  }

  @Override
  public SeekableByteChannel truncate(long size) throws IOException {
    buffer.limit((int) size);
    return this;
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public void close() throws IOException {}
}
