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

package com.google.jimfs.internal.file;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndexes;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * A {@link FileChannel} implementation that reads and writes to a {@link ByteStore} object. The
 * read and write methods and other methods that read or change the position of the channel are
 * synchronized because the {@link ReadableByteChannel} and {@link WritableByteChannel} interfaces
 * specify that the read and write methods block when another thread is currently doing a read or
 * write operation.
 *
 * @author Colin Decker
 */
public final class JimfsFileChannel extends FileChannel {

  private final Object lock = new Object();

  private File file;
  private ByteStore store;

  private final boolean readable;
  private final boolean writable;
  private final boolean append;

  private int position;

  public JimfsFileChannel(File file, Set<? extends OpenOption> options) {
    this.file = file;
    this.store = file.content();
    this.readable = options.contains(READ);
    this.writable = options.contains(WRITE);
    this.append = options.contains(APPEND);
  }

  /**
   * Returns an {@link InputStream} view of this channel.
   */
  public InputStream asInputStream() {
    checkReadable();
    return Channels.newInputStream(this);
  }

  /**
   * Returns an {@link OutputStream} view of this channel.
   */
  public OutputStream asOutputStream() {
    checkWritable();
    return Channels.newOutputStream(this);
  }

  /**
   * Returns an {@link AsynchronousFileChannel} view of this channel using the given executor for
   * asynchronous operations.
   */
  public AsynchronousFileChannel asAsynchronousFileChannel(ExecutorService executor) {
    return new JimfsAsynchronousFileChannel(this, executor);
  }

  void checkReadable() {
    if (!readable) {
      throw new NonReadableChannelException();
    }
  }

  void checkWritable() {
    if (!writable) {
      throw new NonWritableChannelException();
    }
  }

  void checkOpen() throws ClosedChannelException {
    if (store == null) {
      throw new ClosedChannelException();
    }
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    synchronized (lock) {
      checkOpen();
      checkReadable();

      file.updateAccessTime();

      int read = store.read(position, dst);
      if (read != -1) {
        position += read;
      }
      return read;
    }
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    checkPositionIndexes(offset, offset + length, dsts.length);
    return read(Arrays.asList(dsts).subList(offset, offset + length));
  }

  private int read(List<ByteBuffer> buffers) throws IOException {
    synchronized (lock) {
      checkOpen();
      checkReadable();

      file.updateAccessTime();

      int read = store.read(position, buffers);
      if (read != -1) {
        position += read;
      }
      return read;
    }
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    synchronized (lock) {
      checkOpen();
      checkWritable();

      file.updateModifiedTime();

      int written;
      if (append) {
        written = store.append(src);
        position = store.sizeInBytes();
      } else {
        written = store.write(position, src);
        position += written;
      }

      return written;
    }
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    checkPositionIndexes(offset, offset + length, srcs.length);
    return write(Arrays.asList(srcs).subList(offset, offset + length));
  }

  private int write(List<ByteBuffer> srcs) throws IOException {
    synchronized (lock) {
      checkOpen();
      checkWritable();

      file.updateModifiedTime();

      int written;
      if (append) {
        written = store.append(srcs);
        position = store.sizeInBytes();
      } else {
        written = store.write(position, srcs);
        position += written;
      }

      return written;
    }
  }

  @Override
  public long position() throws IOException {
    synchronized (lock) {
      checkOpen();
      return position;
    }
  }

  @Override
  public FileChannel position(long newPosition) throws IOException {
    checkNotNegative(newPosition, "newPosition");

    synchronized (lock) {
      checkOpen();
      this.position = (int) newPosition;
      return this;
    }
  }

  @Override
  public long size() throws IOException {
    synchronized (lock) {
      checkOpen();
      return store.sizeInBytes();
    }
  }

  @Override
  public FileChannel truncate(long size) throws IOException {
    checkNotNegative(size, "size");

    synchronized (lock) {
      checkOpen();
      checkWritable();

      file.updateModifiedTime();

      store.truncate((int) size);
      if (position > size) {
        position = (int) size;
      }

      return this;
    }
  }

  @Override
  public void force(boolean metaData) throws IOException {
    synchronized (lock) {
      checkOpen();
      // do nothing... writes are all synchronous anyway
    }
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
    checkNotNull(target);
    checkNotNegative(position, "position");
    checkNotNegative(count, "count");

    synchronized (lock) {
      checkOpen();
      checkReadable();

      file.updateAccessTime();

      return store.transferTo((int) position, (int) count, target);
    }
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
    checkNotNull(src);
    checkNotNegative(position, "position");
    checkNotNegative(count, "count");

    synchronized (lock) {
      checkOpen();
      checkWritable();

      file.updateModifiedTime();

      if (append) {
        long appended = store.appendFrom(src, (int) count);
        this.position = store.sizeInBytes();
        return appended;
      } else {
        return store.transferFrom(src, (int) position, (int) count);
      }
    }
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException {
    checkNotNull(dst);
    checkNotNegative(position, "position");

    synchronized (lock) {
      checkOpen();
      checkReadable();

      file.updateAccessTime();

      return store.read((int) position, dst);
    }
  }

  @Override
  public int write(ByteBuffer src, long position) throws IOException {
    checkNotNull(src);
    checkNotNegative(position, "position");

    synchronized (lock) {
      checkOpen();
      checkWritable();

      file.updateModifiedTime();

      int written;
      if (append) {
        written = store.append(src);
        this.position = store.sizeInBytes();
      } else {
        written = store.write((int) position, src);
      }

      return written;
    }
  }

  @Override
  public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
    // would like this to pretend to work, but can't create an implementation of MappedByteBuffer
    throw new UnsupportedOperationException();
  }

  // TODO(cgdecker): Throw UOE from these lock methods since we aren't really supporting it?

  @Override
  public FileLock lock(long position, long size, boolean shared) throws IOException {
    checkNotNegative(position, "position");
    checkNotNegative(size, "size");
    
    synchronized (lock) {
      checkOpen();
      if (shared) {
        // shared is for a read lock
        checkReadable();
      } else {
        // non-shared is for a write lock
        checkWritable();
      }
      return new FakeFileLock(this, position, size, shared);
    }
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException {
    // lock doesn't wait anyway
    return lock(position, size, shared);
  }

  @Override
  protected void implCloseChannel() throws IOException {
    // if the file has been deleted, allow it to be GCed even if a reference to this channel is
    // held after closing for some reason
    synchronized (lock) {
      file = null;
      store = null;
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
