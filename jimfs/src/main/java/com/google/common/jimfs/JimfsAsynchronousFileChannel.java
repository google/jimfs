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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

/**
 * {@link AsynchronousFileChannel} implementation that delegates to a {@link JimfsFileChannel}.
 *
 * @author Colin Decker
 */
final class JimfsAsynchronousFileChannel extends AsynchronousFileChannel {

  private final JimfsFileChannel channel;
  private final ListeningExecutorService executor;

  public JimfsAsynchronousFileChannel(JimfsFileChannel channel, ExecutorService executor) {
    this.channel = checkNotNull(channel);
    this.executor = MoreExecutors.listeningDecorator(executor);
  }

  @Override
  public long size() throws IOException {
    return channel.size();
  }

  private <R, A> void addCallback(
      ListenableFuture<R> future, CompletionHandler<R, ? super A> handler, @Nullable A attachment) {
    future.addListener(new CompletionHandlerCallback<>(future, handler, attachment), executor);
  }

  @Override
  public AsynchronousFileChannel truncate(long size) throws IOException {
    channel.truncate(size);
    return this;
  }

  @Override
  public void force(boolean metaData) throws IOException {
    channel.force(metaData);
  }

  @Override
  public <A> void lock(
      long position,
      long size,
      boolean shared,
      @Nullable A attachment,
      CompletionHandler<FileLock, ? super A> handler) {
    checkNotNull(handler);
    addCallback(lock(position, size, shared), handler, attachment);
  }

  @Override
  public ListenableFuture<FileLock> lock(
      final long position, final long size, final boolean shared) {
    Util.checkNotNegative(position, "position");
    Util.checkNotNegative(size, "size");
    if (!isOpen()) {
      return closedChannelFuture();
    }
    if (shared) {
      channel.checkReadable();
    } else {
      channel.checkWritable();
    }
    return executor.submit(
        new Callable<FileLock>() {
          @Override
          public FileLock call() throws IOException {
            return tryLock(position, size, shared);
          }
        });
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException {
    Util.checkNotNegative(position, "position");
    Util.checkNotNegative(size, "size");
    channel.checkOpen();
    if (shared) {
      channel.checkReadable();
    } else {
      channel.checkWritable();
    }
    return new JimfsFileChannel.FakeFileLock(this, position, size, shared);
  }

  @Override
  public <A> void read(
      ByteBuffer dst,
      long position,
      @Nullable A attachment,
      CompletionHandler<Integer, ? super A> handler) {
    addCallback(read(dst, position), handler, attachment);
  }

  @Override
  public ListenableFuture<Integer> read(final ByteBuffer dst, final long position) {
    checkArgument(!dst.isReadOnly(), "dst may not be read-only");
    Util.checkNotNegative(position, "position");
    if (!isOpen()) {
      return closedChannelFuture();
    }
    channel.checkReadable();
    return executor.submit(
        new Callable<Integer>() {
          @Override
          public Integer call() throws IOException {
            return channel.read(dst, position);
          }
        });
  }

  @Override
  public <A> void write(
      ByteBuffer src,
      long position,
      @Nullable A attachment,
      CompletionHandler<Integer, ? super A> handler) {
    addCallback(write(src, position), handler, attachment);
  }

  @Override
  public ListenableFuture<Integer> write(final ByteBuffer src, final long position) {
    Util.checkNotNegative(position, "position");
    if (!isOpen()) {
      return closedChannelFuture();
    }
    channel.checkWritable();
    return executor.submit(
        new Callable<Integer>() {
          @Override
          public Integer call() throws IOException {
            return channel.write(src, position);
          }
        });
  }

  @Override
  public boolean isOpen() {
    return channel.isOpen();
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  /**
   * Immediate future indicating that the channel is closed.
   */
  private static <V> ListenableFuture<V> closedChannelFuture() {
    SettableFuture<V> future = SettableFuture.create();
    future.setException(new ClosedChannelException());
    return future;
  }

  /**
   * Runnable callback that wraps a {@link CompletionHandler} and an attachment.
   */
  private static final class CompletionHandlerCallback<R, A> implements Runnable {

    private final ListenableFuture<R> future;
    private final CompletionHandler<R, ? super A> completionHandler;
    @Nullable private final A attachment;

    private CompletionHandlerCallback(
        ListenableFuture<R> future,
        CompletionHandler<R, ? super A> completionHandler,
        @Nullable A attachment) {
      this.future = checkNotNull(future);
      this.completionHandler = checkNotNull(completionHandler);
      this.attachment = attachment;
    }

    @Override
    public void run() {
      R result;
      try {
        result = future.get();
      } catch (ExecutionException e) {
        onFailure(e.getCause());
        return;
      } catch (InterruptedException | RuntimeException | Error e) {
        // get() shouldn't be interrupted since this should only be called when the result is
        // ready, but just handle it anyway to be sure and to satisfy the compiler
        onFailure(e);
        return;
      }

      onSuccess(result);
    }

    private void onSuccess(R result) {
      completionHandler.completed(result, attachment);
    }

    private void onFailure(Throwable t) {
      completionHandler.failed(t, attachment);
    }
  }
}
