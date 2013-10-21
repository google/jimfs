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

import static com.google.common.base.Preconditions.checkPositionIndexes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@link InputStream} for reading from a file's {@link ByteStore}.
 *
 * @author Colin Decker
 */
final class JimfsInputStream extends InputStream {

  @VisibleForTesting File file;
  private ByteStore store;
  private boolean finished;

  private long pos;

  public JimfsInputStream(File file) {
    this.file = file;
    this.store = file.bytes();
  }

  @Override
  public int read() throws IOException {
    synchronized (this) {
      checkNotClosed();
      if (finished) {
        return -1;
      }

      store.readLock().lock();
      try {

        int b = store.read(pos++); // it's ok for pos to go beyond size()
        if (b == -1) {
          finished = true;
        } else {
          file.updateAccessTime();
        }
        return b;
      } finally {
        store.readLock().unlock();
      }
    }
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    checkPositionIndexes(off, off + len, b.length);

    synchronized (this) {
      checkNotClosed();
      if (finished) {
        return -1;
      }

      store.readLock().lock();
      try {
        int read = store.read(pos, b, off, len);
        if (read == -1) {
          finished = true;
        } else {
          pos += read;
        }

        file.updateAccessTime();
        return read;
      } finally {
        store.readLock().unlock();
      }
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
      int skip = (int) Math.min(Math.max(store.size() - pos, 0), n);
      pos += skip;
      return skip;
    }
  }

  @Override
  public int available() throws IOException {
    synchronized (this) {
      checkNotClosed();
      if (finished) {
        return 0;
      }
      long available = Math.max(store.size() - pos, 0);
      return Ints.saturatedCast(available);
    }
  }

  private void checkNotClosed() throws IOException {
    if (store == null) {
      throw new IOException("stream is closed");
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (this) {
      if (store != null) {
        store.closed();
        file = null;
        store = null;
      }
    }
  }
}
