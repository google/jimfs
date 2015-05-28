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
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Runnables;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
  public void testClosedChannel() throws IOException, InterruptedException {
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
  public void testAsyncClose_write() throws IOException, InterruptedException {
    RegularFile file = regularFile(15);
    ExecutorService executor = Executors.newFixedThreadPool(4);

    try {
      JimfsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);

      file.writeLock().lock(); // cause another thread trying to write to block

      Future<Integer> future = channel.write(ByteBuffer.allocate(10), 0);

      final CountDownLatch handlerLatch = new CountDownLatch(1);
      final AtomicBoolean gotAsyncCloseException = new AtomicBoolean(false);
      channel.write(
          ByteBuffer.allocate(10), 0, null,
          new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
              handlerLatch.countDown();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
              gotAsyncCloseException.set(exc instanceof AsynchronousCloseException);
              handlerLatch.countDown();
            }
          });

      // give enough time to ensure both writes start blocking
      Uninterruptibles.sleepUninterruptibly(10, MILLISECONDS);

      channel.close();

      try {
        future.get();
        fail();
      } catch (ExecutionException expected) {
        assertTrue(expected.getCause() instanceof AsynchronousCloseException);
      }

      handlerLatch.await();

      assertTrue(gotAsyncCloseException.get());
    } finally {
      executor.shutdown();
    }
  }

  @Test
  public void testAsyncClose_read() throws IOException, InterruptedException {
    RegularFile file = regularFile(15);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      JimfsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);

      file.writeLock().lock(); // cause another thread trying to read to block

      Future<Integer> future = channel.read(ByteBuffer.allocate(10), 0);

      final CountDownLatch handlerLatch = new CountDownLatch(1);
      final AtomicBoolean gotAsyncCloseException = new AtomicBoolean(false);
      channel.read(
          ByteBuffer.allocate(10), 0, null,
          new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
              handlerLatch.countDown();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
              gotAsyncCloseException.set(exc instanceof AsynchronousCloseException);
              handlerLatch.countDown();
            }
          });

      // give enough time to ensure both reads start blocking
      Uninterruptibles.sleepUninterruptibly(10, MILLISECONDS);

      channel.close();

      try {
        future.get();
        fail();
      } catch (ExecutionException expected) {
        assertTrue(expected.getCause() instanceof AsynchronousCloseException);
      }

      handlerLatch.await();

      assertTrue(gotAsyncCloseException.get());
    } finally {
      executor.shutdown();
    }
  }

  private static void checkAsyncRead(AsynchronousFileChannel channel) throws Throwable {
    ByteBuffer buf = buffer("1234567890");
    assertEquals(10, (int) channel.read(buf, 0).get());

    buf.flip();
    final AtomicInteger resultHolder = new AtomicInteger(-1);
    final AtomicReference<Throwable> exceptionHolder = new AtomicReference<>();
    final CountDownLatch completionLatch = new CountDownLatch(1);
    channel.read(
        buf, 0, null,
        new CompletionHandler<Integer, Object>() {
          @Override
          public void completed(Integer result, Object attachment) {
            resultHolder.set(result);
            completionLatch.countDown();
          }

          @Override
          public void failed(Throwable exc, Object attachment) {
            exceptionHolder.set(exc);
            completionLatch.countDown();
          }
        });

    completionLatch.await();
    Throwable exception = exceptionHolder.get();
    if (exception != null) {
      throw exception;
    } else {
      assertEquals(10, resultHolder.get());
    }
  }

  private static void checkAsyncWrite(AsynchronousFileChannel asyncChannel) throws Throwable {
    ByteBuffer buf = buffer("1234567890");
    assertEquals(10, (int) asyncChannel.write(buf, 0).get());

    buf.flip();
    final AtomicInteger resultHolder = new AtomicInteger(-1);
    final AtomicReference<Throwable> exceptionHolder = new AtomicReference<>();
    final CountDownLatch completionLatch = new CountDownLatch(1);
    asyncChannel.write(
        buf, 0, null,
        new CompletionHandler<Integer, Object>() {
          @Override
          public void completed(Integer result, Object attachment) {
            resultHolder.set(result);
            completionLatch.countDown();
          }

          @Override
          public void failed(Throwable exc, Object attachment) {
            exceptionHolder.set(exc);
            completionLatch.countDown();
          }
        });

    completionLatch.await();
    Throwable exception = exceptionHolder.get();
    if (exception != null) {
      throw exception;
    } else {
      assertEquals(10, resultHolder.get());
    }
  }

  private static void checkAsyncLock(AsynchronousFileChannel channel) throws Throwable {
    assertNotNull(channel.lock().get());
    assertNotNull(channel.lock(0, 10, true).get());

    final AtomicReference<FileLock> lockHolder = new AtomicReference<>();
    final AtomicReference<Throwable> exceptionHolder = new AtomicReference<>();
    final CountDownLatch completionLatch = new CountDownLatch(1);
    channel.lock(
        0, 10, true, null,
        new CompletionHandler<FileLock, Object>() {
          @Override
          public void completed(FileLock result, Object attachment) {
            lockHolder.set(result);
            completionLatch.countDown();
          }

          @Override
          public void failed(Throwable exc, Object attachment) {
            exceptionHolder.set(exc);
            completionLatch.countDown();
          }
        });

    completionLatch.await();
    Throwable exception = exceptionHolder.get();
    if (exception != null) {
      throw exception;
    } else {
      assertNotNull(lockHolder.get());
    }
  }

  private static void assertClosed(Future<?> future) throws InterruptedException {
    try {
      future.get();
      fail();
    } catch (ExecutionException expected) {
      assertTrue(expected.getCause() instanceof ClosedChannelException);
    }
  }
}
