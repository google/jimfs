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

import static com.google.common.jimfs.TestUtils.buffer;
import static com.google.common.jimfs.TestUtils.regularFile;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Runnables;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Tests for {@link JimfsAsynchronousFileChannel}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class JimfsAsynchronousFileChannelTest {

  private static JimfsAsynchronousFileChannel channel(
      RegularFile file, ExecutorService executor, OpenOption... options) throws IOException {
    JimfsFileChannel channel =
        new JimfsFileChannel(
            file,
            Options.getOptionsForChannel(ImmutableSet.copyOf(options)),
            new FileSystemState(Runnables.doNothing()));
    return new JimfsAsynchronousFileChannel(channel, executor);
  }

  /**
   * Just tests the main read/write methods... the methods all delegate to the non-async channel
   * anyway.
   */
  @Test
  public void testAsyncChannel() throws Throwable {
    RegularFile file = regularFile(15);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    JimfsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);

    try {
      assertEquals(15, channel.size());

      assertSame(channel, channel.truncate(5));
      assertEquals(5, channel.size());

      file.write(5, new byte[5], 0, 5);
      checkAsyncRead(channel);
      checkAsyncWrite(channel);
      checkAsyncLock(channel);

      channel.close();
      assertFalse(channel.isOpen());
    } finally {
      executor.shutdown();
    }
  }

  @Test
  public void testClosedChannel() throws Throwable {
    RegularFile file = regularFile(15);
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try {
      JimfsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);
      channel.close();

      assertClosed(channel.read(ByteBuffer.allocate(10), 0));
      assertClosed(channel.write(ByteBuffer.allocate(10), 15));
      assertClosed(channel.lock());
      assertClosed(channel.lock(0, 10, true));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  public void testAsyncClose_write() throws Throwable {
    RegularFile file = regularFile(15);
    ExecutorService executor = Executors.newFixedThreadPool(4);

    try {
      JimfsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);

      file.writeLock().lock(); // cause another thread trying to write to block

      // future-returning write
      Future<Integer> future = channel.write(ByteBuffer.allocate(10), 0);

      // completion handler write
      SettableFuture<Integer> completionHandlerFuture = SettableFuture.create();
      channel.write(ByteBuffer.allocate(10), 0, null, setFuture(completionHandlerFuture));

      // Despite this 10ms sleep to allow plenty of time, it's possible, though very rare, for a
      // race to cause the channel to be closed before the asynchronous calls get to the initial
      // check that the channel is open, causing ClosedChannelException to be thrown rather than
      // AsynchronousCloseException. This is not a problem in practice, just a quirk of how these
      // tests work and that we don't have a way of waiting for the operations to get past that
      // check.
      Uninterruptibles.sleepUninterruptibly(10, MILLISECONDS);

      channel.close();

      assertAsynchronousClose(future);
      assertAsynchronousClose(completionHandlerFuture);
    } finally {
      executor.shutdown();
    }
  }

  @Test
  public void testAsyncClose_read() throws Throwable {
    RegularFile file = regularFile(15);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      JimfsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);

      file.writeLock().lock(); // cause another thread trying to read to block

      // future-returning read
      Future<Integer> future = channel.read(ByteBuffer.allocate(10), 0);

      // completion handler read
      SettableFuture<Integer> completionHandlerFuture = SettableFuture.create();
      channel.read(ByteBuffer.allocate(10), 0, null, setFuture(completionHandlerFuture));

      // Despite this 10ms sleep to allow plenty of time, it's possible, though very rare, for a
      // race to cause the channel to be closed before the asynchronous calls get to the initial
      // check that the channel is open, causing ClosedChannelException to be thrown rather than
      // AsynchronousCloseException. This is not a problem in practice, just a quirk of how these
      // tests work and that we don't have a way of waiting for the operations to get past that
      // check.
      Uninterruptibles.sleepUninterruptibly(10, MILLISECONDS);

      channel.close();

      assertAsynchronousClose(future);
      assertAsynchronousClose(completionHandlerFuture);
    } finally {
      executor.shutdown();
    }
  }

  private static void checkAsyncRead(AsynchronousFileChannel channel) throws Throwable {
    ByteBuffer buf = buffer("1234567890");
    assertEquals(10, (int) channel.read(buf, 0).get());

    buf.flip();

    SettableFuture<Integer> future = SettableFuture.create();
    channel.read(buf, 0, null, setFuture(future));

    assertThat(future.get(10, SECONDS)).isEqualTo(10);
  }

  private static void checkAsyncWrite(AsynchronousFileChannel asyncChannel) throws Throwable {
    ByteBuffer buf = buffer("1234567890");
    assertEquals(10, (int) asyncChannel.write(buf, 0).get());

    buf.flip();
    SettableFuture<Integer> future = SettableFuture.create();
    asyncChannel.write(buf, 0, null, setFuture(future));

    assertThat(future.get(10, SECONDS)).isEqualTo(10);
  }

  private static void checkAsyncLock(AsynchronousFileChannel channel) throws Throwable {
    assertNotNull(channel.lock().get());
    assertNotNull(channel.lock(0, 10, true).get());

    SettableFuture<FileLock> future = SettableFuture.create();
    channel.lock(0, 10, true, null, setFuture(future));

    assertNotNull(future.get(10, SECONDS));
  }

  /**
   * Returns a {@code CompletionHandler} that sets the appropriate result or exception on the given
   * {@code future} on completion.
   */
  private static <T> CompletionHandler<T, Object> setFuture(final SettableFuture<T> future) {
    return new CompletionHandler<T, Object>() {
        @Override
        public void completed(T result, Object attachment) {
          future.set(result);
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
          future.setException(exc);
        }
      };
  }

  /**
   * Assert that the future fails, with the failure caused by {@code ClosedChannelException}.
   */
  private static void assertClosed(Future<?> future) throws Throwable {
    try {
      future.get(10, SECONDS);
      fail("ChannelClosedException was not thrown");
    } catch (ExecutionException expected) {
      assertThat(expected.getCause()).isInstanceOf(ClosedChannelException.class);
    }
  }

  /**
   * Assert that the future fails, with the failure caused by either
   * {@code AsynchronousCloseException} or (rarely) {@code ClosedChannelException}.
   */
  private static void assertAsynchronousClose(Future<?> future) throws Throwable {
    try {
      future.get(10, SECONDS);
      fail("no exception was thrown");
    } catch (ExecutionException expected) {
      Throwable t = expected.getCause();
      if (!(t instanceof AsynchronousCloseException || t instanceof ClosedChannelException)) {
        fail("expected AsynchronousCloseException (or in rare cases ClosedChannelException): "
            + "got " + t);
      }
    }
  }
}
