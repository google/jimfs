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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.concurrent.GuardedBy;

/**
 * {@link InputStream} for reading from a file's {@link RegularFile}.
 *
 * @author Colin Decker
 */
final class JimfsInputStream extends InputStream {

  @GuardedBy("this")
  @VisibleForTesting
  RegularFile file;

  @GuardedBy("this")
  private long pos;

  @GuardedBy("this")
  private boolean finished;

  private final FileSystemState fileSystemState;

  public JimfsInputStream(RegularFile file, FileSystemState fileSystemState) {
    this.file = checkNotNull(file);
    this.fileSystemState = fileSystemState;
    fileSystemState.register(this);
  }

  @Override
  public synchronized int read() throws IOException {
    checkNotClosed();
    if (finished) {
      return -1;
    }

    file.readLock().lock();
    try {

      int b = file.read(pos++); // it's ok for pos to go beyond size()
      if (b == -1) {
        finished = true;
      } else {
        file.updateAccessTime();
      }
      return b;
    } finally {
      file.readLock().unlock();
    }
  }

  @Override
  public int read(byte[] b) throws IOException {
    return readInternal(b, 0, b.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    checkPositionIndexes(off, off + len, b.length);
    return readInternal(b, off, len);
  }

  private synchronized int readInternal(byte[] b, int off, int len) throws IOException {
    checkNotClosed();
    if (finished) {
      return -1;
    }

    file.readLock().lock();
    try {
      int read = file.read(pos, b, off, len);
      if (read == -1) {
        finished = true;
      } else {
        pos += read;
      }

      file.updateAccessTime();
      return read;
    } finally {
      file.readLock().unlock();
    }
  }

  @Override
  public long skip(long n) throws IOException {
    if (n <= 0) {
      return 0;
    }

    synchronized (this) {
      checkNotClosed();
      if (finished) {
        return 0;
      }

      // available() must be an int, so the min must be also
      int skip = (int) Math.min(Math.max(file.size() - pos, 0), n);
      pos += skip;
      return skip;
    }
  }

  @Override
  public synchronized int available() throws IOException {
    checkNotClosed();
    if (finished) {
      return 0;
    }
    long available = Math.max(file.size() - pos, 0);
    return Ints.saturatedCast(available);
  }

  @GuardedBy("this")
  private void checkNotClosed() throws IOException {
    if (file == null) {
      throw new IOException("stream is closed");
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (isOpen()) {
      fileSystemState.unregister(this);
      file.closed();

      // file is set to null here and only here
      file = null;
    }
  }

  @GuardedBy("this")
  private boolean isOpen() {
    return file != null;
  }
}
