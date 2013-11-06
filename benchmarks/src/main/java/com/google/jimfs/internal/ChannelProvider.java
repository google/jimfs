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

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Colin Decker
 */
public abstract class ChannelProvider {

  public static ChannelProvider newSocketChannelProvider(int size) {
    return new SocketChannelProvider(size, null);
  }

  public static ChannelProvider newSocketChannelProvider(byte[] bytes) {
    return new SocketChannelProvider(bytes.length, bytes);
  }

  public static ChannelProvider newFileChannelProvider() {
    return new FileChannelProvider(null);
  }

  public static ChannelProvider newFileChannelProvider(byte[] bytes) {
    return new FileChannelProvider(bytes);
  }

  public abstract void setUp() throws IOException;

  public void beforeRep() throws IOException {
  }

  public abstract ReadableByteChannel getReadableChannel();

  public abstract WritableByteChannel getWritableChannel();

  public void afterRep() throws IOException {
  }

  public abstract void tearDown() throws IOException;

  private static final class SocketChannelProvider extends ChannelProvider {

    private final int size;
    private final byte[] bytes;

    private DummyServer server;
    private SocketChannel channel;

    private SocketChannelProvider(int size, byte[] bytes) {
      this.size = size;
      this.bytes = bytes;
    }

    @Override
    public void setUp() throws IOException {
      if (bytes == null) {
        server = DummyServer.newReadingServer(size);
      } else {
        server = DummyServer.newWritingServer(bytes);
      }

      server.startAsync()
          .awaitRunning();

      channel = SocketChannel.open();
      channel.connect(new InetSocketAddress("127.0.0.1", server.port()));
    }

    @Override
    public SocketChannel getReadableChannel() {
      return channel;
    }

    @Override
    public SocketChannel getWritableChannel() {
      return channel;
    }

    @Override
    public void tearDown() throws IOException {
      try {
        channel.close();
      } finally {
        server.stopAsync()
            .awaitTerminated();
      }
    }
  }

  private static final class FileChannelProvider extends ChannelProvider {

    private final byte[] bytes;

    private Path file;
    private FileChannel channel;

    private FileChannelProvider(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public void setUp() throws IOException {
      file = Files.createTempFile("FileChannelProvider", ".tmp");
      channel = FileChannel.open(file, READ, WRITE, TRUNCATE_EXISTING);
      if (bytes != null) {
        channel.write(ByteBuffer.wrap(bytes));
      }
    }

    @Override
    public void beforeRep() throws IOException {
      channel.position(0);
    }

    @Override
    public FileChannel getReadableChannel() {
      return channel;
    }

    @Override
    public FileChannel getWritableChannel() {
      return channel;
    }

    @Override
    public void afterRep() throws IOException {
      if (bytes == null) {
        channel.truncate(0);
      }
    }

    @Override
    public void tearDown() throws IOException {
      try {
        channel.close();
      } finally {
        Files.deleteIfExists(file);
      }
    }
  }
}
