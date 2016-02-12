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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndexes;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * A {@link FileChannel} implementation that reads and writes to a {@link RegularFile} object. The
 * read and write methods and other methods that read or change the position of the channel are
 * locked because the {@link ReadableByteChannel} and {@link WritableByteChannel} interfaces specify
 * that the read and write methods block when another thread is currently doing a read or write
 * operation.
 *
 * @author Colin Decker
 */
final class JimfsFileChannel extends FileChannel {

  /**
   * Set of threads that are currently doing an interruptible blocking operation; that is, doing
   * something that requires acquiring the file's lock. These threads must be interrupted if the
   * channel is closed by another thread.
   */
  @GuardedBy("blockingThreads")
  private final Set<Thread> blockingThreads = new HashSet<Thread>();

  private final RegularFile file;
  private final FileSystemState fileSystemState;

  private final boolean read;
  private final boolean write;
  private final boolean append;

  @GuardedBy("this")
  private long position;

  public JimfsFileChannel(
      RegularFile file, Set<OpenOption> options, FileSystemState fileSystemState) {
    this.file = file;
    this.fileSystemState = fileSystemState;
    this.read = options.contains(READ);
    this.write = options.contains(WRITE);
    this.append = options.contains(APPEND);

    fileSystemState.register(this);
  }

  /**
   * Returns an {@link AsynchronousFileChannel} view of this channel using the given executor for
   * asynchronous operations.
   */
  public AsynchronousFileChannel asAsynchronousFileChannel(ExecutorService executor) {
    return new JimfsAsynchronousFileChannel(this, executor);
  }

  void checkReadable() {
    if (!read) {
      throw new NonReadableChannelException();
    }
  }

  void checkWritable() {
    if (!write) {
      throw new NonWritableChannelException();
    }
  }

  void checkOpen() throws ClosedChannelException {
    if (!isOpen()) {
      throw new ClosedChannelException();
    }
  }

  /**
   * Begins a blocking operation, making the operation interruptible. Returns {@code true} if the
   * channel was open and the thread was added as a blocking thread; returns {@code false} if the
   * channel was closed.
   */
  private boolean beginBlocking() {
    begin();
    synchronized (blockingThreads) {
      if (isOpen()) {
        blockingThreads.add(Thread.currentThread());
        return true;
      }

      return false;
    }
  }

  /**
   * Ends a blocking operation, throwing an exception if the thread was interrupted while blocking
   * or if the channel was closed from another thread.
   */
  private void endBlocking(boolean completed) throws AsynchronousCloseException {
    synchronized (blockingThreads) {
      blockingThreads.remove(Thread.currentThread());
    }
    end(completed);
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    checkNotNull(dst);
    checkOpen();
    checkReadable();

    int read = 0; // will definitely either be assigned or an exception will be thrown

    synchronized (this) {
      boolean completed = false;
      try {
        if (!beginBlocking()) {
          return 0; // AsynchronousCloseException will be thrown
        }
        file.readLock().lockInterruptibly();
        try {
          read = file.read(position, dst);
          if (read != -1) {
            position += read;
          }
          file.updateAccessTime();
          completed = true;
        } finally {
          file.readLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }
    }

    return read;
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    checkPositionIndexes(offset, offset + length, dsts.length);
    List<ByteBuffer> buffers = Arrays.asList(dsts).subList(offset, offset + length);
    Util.checkNoneNull(buffers);
    checkOpen();
    checkReadable();

    long read = 0; // will definitely either be assigned or an exception will be thrown

    synchronized (this) {
      boolean completed = false;
      try {
        if (!beginBlocking()) {
          return 0; // AsynchronousCloseException will be thrown
        }
        file.readLock().lockInterruptibly();
        try {
          read = file.read(position, buffers);
          if (read != -1) {
            position += read;
          }
          file.updateAccessTime();
          completed = true;
        } finally {
          file.readLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }
    }

    return read;
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    checkNotNull(src);
    checkOpen();
    checkWritable();

    int written = 0; // will definitely either be assigned or an exception will be thrown

    synchronized (this) {
      boolean completed = false;
      try {
        if (!beginBlocking()) {
          return 0; // AsynchronousCloseException will be thrown
        }
        file.writeLock().lockInterruptibly();
        try {
          if (append) {
            position = file.size();
          }
          written = file.write(position, src);
          position += written;
          file.updateModifiedTime();
          completed = true;
        } finally {
          file.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }
    }

    return written;
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    checkPositionIndexes(offset, offset + length, srcs.length);
    List<ByteBuffer> buffers = Arrays.asList(srcs).subList(offset, offset + length);
    Util.checkNoneNull(buffers);
    checkOpen();
    checkWritable();

    long written = 0; // will definitely either be assigned or an exception will be thrown

    synchronized (this) {
      boolean completed = false;
      try {
        if (!beginBlocking()) {
          return 0; // AsynchronousCloseException will be thrown
        }
        file.writeLock().lockInterruptibly();
        try {
          if (append) {
            position = file.size();
          }
          written = file.write(position, buffers);
          position += written;
          file.updateModifiedTime();
          completed = true;
        } finally {
          file.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }
    }

    return written;
  }

  @Override
  public long position() throws IOException {
    checkOpen();

    long pos;

    synchronized (this) {
      boolean completed = false;
      try {
        begin(); // don't call beginBlocking() because this method doesn't block
        if (!isOpen()) {
          return 0; // AsynchronousCloseException will be thrown
        }
        pos = this.position;
        completed = true;
      } finally {
        end(completed);
      }
    }

    return pos;
  }

  @Override
  public FileChannel position(long newPosition) throws IOException {
    Util.checkNotNegative(newPosition, "newPosition");
    checkOpen();

    synchronized (this) {
      boolean completed = false;
      try {
        begin(); // don't call beginBlocking() because this method doesn't block
        if (!isOpen()) {
          return this; // AsynchronousCloseException will be thrown
        }
        this.position = newPosition;
        completed = true;
      } finally {
        end(completed);
      }
    }

    return this;
  }

  @Override
  public long size() throws IOException {
    checkOpen();

    long size = 0; // will definitely either be assigned or an exception will be thrown

    boolean completed = false;
    try {
      if (!beginBlocking()) {
        return 0; // AsynchronousCloseException will be thrown
      }
      file.readLock().lockInterruptibly();
      try {
        size = file.sizeWithoutLocking();
        completed = true;
      } finally {
        file.readLock().unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      endBlocking(completed);
    }

    return size;
  }

  @Override
  public FileChannel truncate(long size) throws IOException {
    Util.checkNotNegative(size, "size");
    checkOpen();
    checkWritable();

    synchronized (this) {
      boolean completed = false;
      try {
        if (!beginBlocking()) {
          return this; // AsynchronousCloseException will be thrown
        }
        file.writeLock().lockInterruptibly();
        try {
          file.truncate(size);
          if (position > size) {
            position = size;
          }
          file.updateModifiedTime();
          completed = true;
        } finally {
          file.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }
    }

    return this;
  }

  @Override
  public void force(boolean metaData) throws IOException {
    checkOpen();

    // nothing to do since writes are all direct to the storage
    // however, we should handle the thread being interrupted anyway
    boolean completed = false;
    try {
      begin();
      completed = true;
    } finally {
      end(completed);
    }
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
    checkNotNull(target);
    Util.checkNotNegative(position, "position");
    Util.checkNotNegative(count, "count");
    checkOpen();
    checkReadable();

    long transferred = 0; // will definitely either be assigned or an exception will be thrown

    // no need to synchronize here; this method does not make use of the channel's position
    boolean completed = false;
    try {
      if (!beginBlocking()) {
        return 0; // AsynchronousCloseException will be thrown
      }
      file.readLock().lockInterruptibly();
      try {
        transferred = file.transferTo(position, count, target);
        file.updateAccessTime();
        completed = true;
      } finally {
        file.readLock().unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      endBlocking(completed);
    }

    return transferred;
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
    checkNotNull(src);
    Util.checkNotNegative(position, "position");
    Util.checkNotNegative(count, "count");
    checkOpen();
    checkWritable();

    long transferred = 0; // will definitely either be assigned or an exception will be thrown

    if (append) {
      // synchronize because appending does update the channel's position
      synchronized (this) {
        boolean completed = false;
        try {
          if (!beginBlocking()) {
            return 0; // AsynchronousCloseException will be thrown
          }

          file.writeLock().lockInterruptibly();
          try {
            position = file.sizeWithoutLocking();
            transferred = file.transferFrom(src, position, count);
            this.position = position + transferred;
            file.updateModifiedTime();
            completed = true;
          } finally {
            file.writeLock().unlock();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endBlocking(completed);
        }
      }
    } else {
      // don't synchronize because the channel's position is not involved
      boolean completed = false;
      try {
        if (!beginBlocking()) {
          return 0; // AsynchronousCloseException will be thrown
        }
        file.writeLock().lockInterruptibly();
        try {
          transferred = file.transferFrom(src, position, count);
          file.updateModifiedTime();
          completed = true;
        } finally {
          file.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }
    }

    return transferred;
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException {
    checkNotNull(dst);
    Util.checkNotNegative(position, "position");
    checkOpen();
    checkReadable();

    int read = 0; // will definitely either be assigned or an exception will be thrown

    // no need to synchronize here; this method does not make use of the channel's position
    boolean completed = false;
    try {
      if (!beginBlocking()) {
        return 0; // AsynchronousCloseException will be thrown
      }
      file.readLock().lockInterruptibly();
      try {
        read = file.read(position, dst);
        file.updateAccessTime();
        completed = true;
      } finally {
        file.readLock().unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      endBlocking(completed);
    }

    return read;
  }

  @Override
  public int write(ByteBuffer src, long position) throws IOException {
    checkNotNull(src);
    Util.checkNotNegative(position, "position");
    checkOpen();
    checkWritable();

    int written = 0; // will definitely either be assigned or an exception will be thrown

    if (append) {
      // synchronize because appending does update the channel's position
      synchronized (this) {
        boolean completed = false;
        try {
          if (!beginBlocking()) {
            return 0; // AsynchronousCloseException will be thrown
          }

          file.writeLock().lockInterruptibly();
          try {
            position = file.sizeWithoutLocking();
            written = file.write(position, src);
            this.position = position + written;
            file.updateModifiedTime();
            completed = true;
          } finally {
            file.writeLock().unlock();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endBlocking(completed);
        }
      }
    } else {
      // don't synchronize because the channel's position is not involved
      boolean completed = false;
      try {
        if (!beginBlocking()) {
          return 0; // AsynchronousCloseException will be thrown
        }
        file.writeLock().lockInterruptibly();
        try {
          written = file.write(position, src);
          file.updateModifiedTime();
          completed = true;
        } finally {
          file.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        endBlocking(completed);
      }
    }

    return written;
  }

  @Override
  public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
    // would like this to pretend to work, but can't create an implementation of MappedByteBuffer
    // well, a direct buffer could be cast to MappedByteBuffer, but it couldn't work in general
    throw new UnsupportedOperationException();
  }

  @Override
  public FileLock lock(long position, long size, boolean shared) throws IOException {
    checkLockArguments(position, size, shared);

    // lock is interruptible
    boolean completed = false;
    try {
      begin();
      completed = true;
      return new FakeFileLock(this, position, size, shared);
    } finally {
      try {
        end(completed);
      } catch (ClosedByInterruptException e) {
        throw new FileLockInterruptionException();
      }
    }
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException {
    checkLockArguments(position, size, shared);

    // tryLock is not interruptible
    return new FakeFileLock(this, position, size, shared);
  }

  private void checkLockArguments(long position, long size, boolean shared) throws IOException {
    Util.checkNotNegative(position, "position");
    Util.checkNotNegative(size, "size");
    checkOpen();
    if (shared) {
      checkReadable();
    } else {
      checkWritable();
    }
  }

  @Override
  protected void implCloseChannel() {
    // interrupt the current blocking threads, if any, causing them to throw
    // ClosedByInterruptException
    try {
      synchronized (blockingThreads) {
        for (Thread thread : blockingThreads) {
          thread.interrupt();
        }
      }
    } finally {
      fileSystemState.unregister(this);
      file.closed();
    }
  }

  /**
   * A file lock that does nothing, since only one JVM process has access to this file system.
   */
  static final class FakeFileLock extends FileLock {

    private final AtomicBoolean valid = new AtomicBoolean(true);

    public FakeFileLock(FileChannel channel, long position, long size, boolean shared) {
      super(channel, position, size, shared);
    }

    public FakeFileLock(AsynchronousFileChannel channel, long position, long size, boolean shared) {
      super(channel, position, size, shared);
    }

    @Override
    public boolean isValid() {
      return valid.get();
    }

    @Override
    public void release() throws IOException {
      valid.set(false);
    }
  }
}
