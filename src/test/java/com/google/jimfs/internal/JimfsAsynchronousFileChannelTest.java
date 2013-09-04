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

import static com.google.jimfs.testing.TestUtils.buffer;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.internal.file.StubByteStore;
import com.google.jimfs.internal.file.ByteStore;
import com.google.jimfs.internal.file.File;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Colin Decker
 */
public class JimfsAsynchronousFileChannelTest {

  private static JimfsAsynchronousFileChannel channel(
      ByteStore store, ExecutorService executor, OpenOption... options) throws IOException {
    ImmutableSet<OpenOption> opts = ImmutableSet.copyOf(options);
    return new JimfsAsynchronousFileChannel(
        new JimfsFileChannel(new File(-1, store), opts), executor);
  }

  private static StubByteStore store(int size) throws IOException {
    return new StubByteStore(size);
  }

  /**
   * Just tests the main read/write methods... the methods all delegate to the non-async channel
   * anyway.
   */
  @Test
  public void testAsyncChannel() throws Throwable {
    StubByteStore store = store(15);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    JimfsAsynchronousFileChannel channel = channel(store, executor, READ, WRITE);

    try {
      assertEquals(15, channel.size());

      assertSame(channel, channel.truncate(5));
      assertEquals(5, channel.size());

      store.setSize(10);
      checkAsyncRead(channel);
      checkAsyncReadFailure(executor);
      checkAsyncWrite(channel);
      checkAsyncLock(channel);

      channel.close();
      assertFalse(channel.isOpen());
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
    channel.read(buf, 0, null, new CompletionHandler<Integer, Object>() {
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

  private static void checkAsyncReadFailure(ExecutorService executor) throws Throwable {
    StubByteStore store = store(10);
    store.setThrowException(true);
    AsynchronousFileChannel channel = channel(store, executor, READ);

    ByteBuffer buf = buffer("1234567890");
    try {
      channel.read(buf, 0).get();
      fail();
    } catch (ExecutionException expected) {
      assertTrue(expected.getCause() instanceof RuntimeException);
      assertEquals("error", expected.getCause().getMessage());
    }

    buf.flip();
    final AtomicReference<Throwable> exceptionHolder = new AtomicReference<>();
    final CountDownLatch completionLatch = new CountDownLatch(1);
    channel.read(buf, 0, null, new CompletionHandler<Integer, Object>() {
      @Override
      public void completed(Integer result, Object attachment) {
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
    if (exception == null) {
      fail();
    } else {
      assertTrue(exception instanceof RuntimeException);
      assertEquals("error", exception.getMessage());
    }
  }

  private static void checkAsyncWrite(AsynchronousFileChannel asyncChannel) throws Throwable {
    ByteBuffer buf = buffer("1234567890");
    assertEquals(10, (int) asyncChannel.write(buf, 0).get());

    buf.flip();
    final AtomicInteger resultHolder = new AtomicInteger(-1);
    final AtomicReference<Throwable> exceptionHolder = new AtomicReference<>();
    final CountDownLatch completionLatch = new CountDownLatch(1);
    asyncChannel.write(buf, 0, null, new CompletionHandler<Integer, Object>() {
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
    channel.lock(0, 10, true, null, new CompletionHandler<FileLock, Object>() {
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
}
