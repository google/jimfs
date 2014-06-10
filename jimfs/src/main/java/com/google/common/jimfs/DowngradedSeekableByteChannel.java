/*
 * Copyright 2014 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * A thin wrapper around a {@link FileChannel} that exists only to implement
 * {@link SeekableByteChannel} but NOT extend {@link FileChannel}.
 *
 * @author Colin Decker
 */
final class DowngradedSeekableByteChannel implements SeekableByteChannel {

  private final FileChannel channel;

  DowngradedSeekableByteChannel(FileChannel channel) {
    this.channel = checkNotNull(channel);
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    return channel.read(dst);
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    return channel.write(src);
  }

  @Override
  public long position() throws IOException {
    return channel.position();
  }

  @Override
  public SeekableByteChannel position(long newPosition) throws IOException {
    channel.position(newPosition);
    return this;
  }

  @Override
  public long size() throws IOException {
    return channel.size();
  }

  @Override
  public SeekableByteChannel truncate(long size) throws IOException {
    channel.truncate(size);
    return this;
  }

  @Override
  public boolean isOpen() {
    return channel.isOpen();
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
