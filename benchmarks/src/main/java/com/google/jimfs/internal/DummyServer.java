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

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @author Colin Decker
 */
public final class DummyServer extends AbstractExecutionThreadService {

  public static DummyServer newReadingServer(int size) {
    return new DummyServer(size);
  }

  public static DummyServer newWritingServer(byte[] bytesToWrite) {
    return new DummyServer(bytesToWrite);
  }

  private final ByteBuffer readBuffer;
  private final ByteBuffer writeBuffer;
  private ServerSocketChannel serverChannel;

  private DummyServer(int size) {
    this.readBuffer = ByteBuffer.allocateDirect(size);
    this.writeBuffer = null;
  }

  private DummyServer(byte[] bytesToWrite) {
    this.readBuffer = null;
    writeBuffer = ByteBuffer.allocateDirect(bytesToWrite.length);
    writeBuffer.put(bytesToWrite);
    writeBuffer.clear();
  }

  @Override
  protected void startUp() throws IOException {
    serverChannel = ServerSocketChannel.open();
    serverChannel.bind(null);
  }

  /**
   * Gets the port for this server.
   */
  public int port() throws IOException {
    return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
  }

  @Override
  protected void run() throws Exception {
    while (isRunning()) {
      try (SocketChannel channel = serverChannel.accept()) {
        if (writeBuffer != null) {
          while (writeBuffer.hasRemaining()) {
            channel.write(writeBuffer);
          }
          writeBuffer.clear();
        }

        if (readBuffer != null) {
          while (channel.read(readBuffer) != -1) {
            readBuffer.clear();
          }
        }
      } catch (AsynchronousCloseException ignore) {
      }
    }
  }

  @Override
  protected void triggerShutdown() {
    try {
      serverChannel.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void shutDown() throws IOException {
    serverChannel.close();
  }
}
