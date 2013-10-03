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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndexes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

/**
 * A {@link FileChannel} implementation that reads and writes to a {@link ByteStore} object. The
 * read and write methods and other methods that read or change the position of the channel are
 * locked because the {@link ReadableByteChannel} and {@link WritableByteChannel} interfaces specify
 * that the read and write methods block when another thread is currently doing a read or write
 * operation.
 *
 * @author Colin Decker
 */
final class JimfsFileChannel extends FileChannel {

  /**
   * Thread that is currently doing an interruptible blocking operation; that is, doing something
   * that requires acquiring the byte store's lock. Since a thread has to already have this
   * channel's lock to do that, there can only be one such thread at a time. This thread must be
   * interrupted if the channel is closed by another thread.
   */
  @Nullable
  private volatile Thread blockingThread;

  /**
   * Lock enforcing one thread at a time for operations on the channel.
   */
  private final Lock lock = new ReentrantLock();

  private final File file;
  private final ByteStore store;

  private final OpenOptions options;

  private long position;

  public JimfsFileChannel(File file, OpenOptions options) {
    this.file = file;
    this.store = file.asByteStore();
    this.options = checkNotNull(options);
  }

  /**
   * Returns an {@link InputStream} view of this channel.
   */
  public InputStream asInputStream() {
    checkReadable();
    return new JimfsInputStream(file);
  }

  /**
   * Returns an {@link OutputStream} view of this channel.
   */
  public OutputStream asOutputStream() {
    checkWritable();
    return new JimfsOutputStream(file, options.isAppend());
  }

  /**
   * Returns an {@link AsynchronousFileChannel} view of this channel using the given executor for
   * asynchronous operations.
   */
  public AsynchronousFileChannel asAsynchronousFileChannel(ExecutorService executor) {
    return new JimfsAsynchronousFileChannel(this, executor);
  }

  void checkReadable() {
    if (!options.isRead()) {
      throw new NonReadableChannelException();
    }
  }

  void checkWritable() {
    if (!options.isWrite()) {
      throw new NonWritableChannelException();
    }
  }

  void checkOpen() throws ClosedChannelException {
    if (!isOpen()) {
      throw new ClosedChannelException();
    }
  }

  /**
   * Begins a blocking operation, making the operation interruptible.
   */
  private void beginBlocking() {
    begin();
    blockingThread = Thread.currentThread();
  }

  /**
   * Ends a blocking operation, throwing an exception if the thread was interrupted while blocking
   * or if the channel was closed from another thread.
   */
  private void endBlocking(boolean completed) throws AsynchronousCloseException {
    blockingThread = null;
    end(completed);
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    checkOpen();
    checkReadable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }

        store.readLock().lockInterruptibly();
        try {
          int read = store.read(position, dst);
          if (read != -1) {
            position += read;
          }

          file.updateAccessTime();
          completed = true;
          return read;
        } finally {
          store.readLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    checkPositionIndexes(offset, offset + length, dsts.length);
    return read(Arrays.asList(dsts).subList(offset, offset + length));
  }

  private long read(List<ByteBuffer> buffers) throws IOException {
    checkOpen();
    checkReadable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }

        store.readLock().lockInterruptibly();
        try {
          long read = store.read(position, buffers);
          if (read != -1) {
            position += read;
          }

          file.updateAccessTime();
          completed = true;
          return read;
        } finally {
          store.readLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    checkOpen();
    checkWritable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }

        store.writeLock().lockInterruptibly();
        try {
          int written;
          if (options.isAppend()) {
            written = store.append(src);
            position = store.sizeInBytes();
          } else {
            written = store.write(position, src);
            position += written;
          }

          file.updateModifiedTime();
          completed = true;
          return written;
        } finally {
          store.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    checkPositionIndexes(offset, offset + length, srcs.length);
    return write(Arrays.asList(srcs).subList(offset, offset + length));
  }

  private long write(List<ByteBuffer> srcs) throws IOException {
    checkOpen();
    checkWritable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }

        store.writeLock().lockInterruptibly();
        try {
          long written;
          if (options.isAppend()) {
            written = store.append(srcs);
            position = store.sizeInBytes();
          } else {
            written = store.write(position, srcs);
            position += written;
          }

          file.updateModifiedTime();
          completed = true;
          return written;
        } finally {
          store.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long position() throws IOException {
    checkOpen();

    lock.lock();
    try {
      return position;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public FileChannel position(long newPosition) throws IOException {
    checkNotNegative(newPosition, "newPosition");
    checkOpen();

    lock.lock();
    try {
      this.position = (int) newPosition;
      return this;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long size() throws IOException {
    checkOpen();

    lock.lock();
    try {
      return store.sizeInBytes();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public FileChannel truncate(long size) throws IOException {
    checkNotNegative(size, "size");
    checkOpen();
    checkWritable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return this; // AsynchronousCloseException will be thrown
        }

        store.writeLock().lockInterruptibly();
        try {
          store.truncate((int) size);
          if (position > size) {
            position = (int) size;
          }

          file.updateModifiedTime();
          completed = true;
          return this;
        } finally {
          store.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void force(boolean metaData) throws IOException {
    lock.lock();
    try {
      checkOpen();
      // do nothing... writes are all synchronous anyway
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
    checkNotNull(target);
    checkNotNegative(position, "position");
    checkNotNegative(count, "count");
    checkOpen();
    checkReadable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }

        store.readLock().lockInterruptibly();
        try {
          long transferred = store.transferTo((int) position, (int) count, target);
          file.updateAccessTime();
          completed = true;
          return transferred;
        } finally {
          store.readLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
    checkNotNull(src);
    checkNotNegative(position, "position");
    checkNotNegative(count, "count");
    checkOpen();
    checkWritable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }

        store.writeLock().lockInterruptibly();
        try {
          long transferred;
          if (options.isAppend()) {
            transferred = store.appendFrom(src, (int) count);
            this.position = store.sizeInBytes();
            file.updateModifiedTime();
          } else {
            file.updateModifiedTime();
            transferred = store.transferFrom(src, (int) position, (int) count);
          }

          completed = true;
          return transferred;
        } finally {
          store.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException {
    checkNotNull(dst);
    checkNotNegative(position, "position");
    checkOpen();
    checkReadable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }

        store.readLock().lockInterruptibly();
        try {
          int read = store.read((int) position, dst);
          file.updateAccessTime();
          completed = true;
          return read;
        } finally {
          store.readLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public int write(ByteBuffer src, long position) throws IOException {
    checkNotNull(src);
    checkNotNegative(position, "position");
    checkOpen();
    checkWritable();

    lock.lock();
    try {
      boolean completed = false;
      try {
        beginBlocking();
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }

        store.writeLock().lockInterruptibly();
        try {
          int written;
          if (options.isAppend()) {
            written = store.append(src);
            this.position = store.size();
          } else {
            written = store.write((int) position, src);
          }

          file.updateModifiedTime();
          completed = true;
          return written;
        } finally {
          store.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }

      // if InterruptedException is caught, endBlocking will throw ClosedByInterruptException
      throw new AssertionError();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
    // would like this to pretend to work, but can't create an implementation of MappedByteBuffer
    // well, a direct buffer could be cast to MappedByteBuffer, but it couldn't work in general
    throw new UnsupportedOperationException();
  }

  // TODO(cgdecker): Throw UOE from these lock methods since we aren't really supporting it?

  @Override
  public FileLock lock(long position, long size, boolean shared) throws IOException {
    checkNotNegative(position, "position");
    checkNotNegative(size, "size");
    checkOpen();
    if (shared) {
      checkReadable();
    } else {
      checkWritable();
    }

    lock.lock();
    try {
      checkOpen();
      return new FakeFileLock(this, position, size, shared);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException {
    // lock doesn't wait anyway
    return lock(position, size, shared);
  }

  @Override
  protected void implCloseChannel() {
    // interrupt the current blocking thread, if any, causing it to throw ClosedByInterruptException
    final Thread thread = blockingThread;
    if (thread != null) {
      thread.interrupt();
    }
  }

  /**
   * A file lock that does nothing, since only one JVM process has access to this file system.
   */
  static final class FakeFileLock extends FileLock {

    private boolean valid = true;

    public FakeFileLock(FileChannel channel, long position, long size, boolean shared) {
      super(channel, position, size, shared);
    }

    public FakeFileLock(AsynchronousFileChannel channel, long position, long size, boolean shared) {
      super(channel, position, size, shared);
    }

    @Override
    public boolean isValid() {
      return valid;
    }

    @Override
    public void release() throws IOException {
      valid = false;
    }
  }

  static void checkNotNegative(long n, String type) {
    checkArgument(n >= 0, "%s must not be negative: %s", type, n);
  }
}
