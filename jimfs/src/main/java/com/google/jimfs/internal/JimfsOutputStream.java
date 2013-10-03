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

import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link OutputStream} for writing to a file's {@link ByteStore}.
 *
 * @author Colin Decker
 */
final class JimfsOutputStream extends OutputStream {

  private final Object lock = new Object();

  @VisibleForTesting File file;
  private ByteStore store;
  private final boolean append;

  private long pos;

  JimfsOutputStream(File file, boolean append) {
    this.file = file;
    this.store = file.asByteStore();
    this.append = append;
  }

  @Override
  public void write(int b) throws IOException {
    synchronized (lock) {
      checkNotClosed();

      store.writeLock().lock();
      try {
        if (append) {
          store.append((byte) b);
        } else {
          store.write(pos++, (byte) b);
        }

        file.updateModifiedTime();
      } finally {
        store.writeLock().unlock();
      }
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    synchronized (lock) {
      checkNotClosed();

      store.writeLock().lock();
      try {
        if (append) {
          store.append(b);
        } else {
          pos += store.write(pos, b);
        }

        file.updateModifiedTime();
      } finally {
        store.writeLock().unlock();
      }
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    checkPositionIndexes(off, off + len, b.length);
    synchronized (lock) {
      checkNotClosed();

      store.writeLock().lock();
      try {
        if (append) {
          store.append(b, off, len);
        } else {
          pos += store.write(pos, b, off, len);
        }

        file.updateModifiedTime();
      } finally {
        store.writeLock().unlock();
      }
    }
  }

  @Override
  public void flush() throws IOException {
    synchronized (lock) {
      checkNotClosed();
      // writes are synchronous to the store, so flush does nothing
    }
  }

  private void checkNotClosed() throws IOException {
    if (store == null) {
      throw new IOException("stream is closed");
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (lock) {
      file = null;
      store = null;
    }
  }
}