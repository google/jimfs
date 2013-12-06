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

  // these fields are guarded by synchronization on "this"
  @VisibleForTesting File file;
  private ByteStore store;
  private long pos;

  private final boolean append;

  JimfsOutputStream(File file, boolean append) {
    this.file = file;
    this.store = file.asBytes();
    this.append = append;
  }

  @Override
  public synchronized void write(int b) throws IOException {
    checkNotClosed();

    store.writeLock().lock();
    try {
      if (append) {
        pos = store.sizeWithoutLocking();
      }
      store.write(pos++, (byte) b);

      file.updateModifiedTime();
    } finally {
      store.writeLock().unlock();
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    writeInternal(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    checkPositionIndexes(off, off + len, b.length);
    writeInternal(b, off, len);
  }

  private synchronized void writeInternal(byte[] b, int off, int len) throws IOException {
    checkNotClosed();

    store.writeLock().lock();
    try {
      if (append) {
        pos = store.sizeWithoutLocking();
      }
      pos += store.write(pos, b, off, len);

      file.updateModifiedTime();
    } finally {
      store.writeLock().unlock();
    }
  }

  @Override
  public synchronized void flush() throws IOException {
    checkNotClosed();
    // writes are synchronous to the store, so flush does nothing
  }

  private void checkNotClosed() throws IOException {
    if (store == null) {
      throw new IOException("stream is closed");
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (isOpen()) {
      store.closed();

      // file and store are both set to null here and only here
      file = null;
      store = null;
    }
  }

  private boolean isOpen() {
    return store != null;
  }

  @Override
  protected void finalize() throws Throwable {
    close();
  }
}