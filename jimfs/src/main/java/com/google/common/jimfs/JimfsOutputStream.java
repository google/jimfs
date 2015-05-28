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

import java.io.IOException;
import java.io.OutputStream;

import javax.annotation.concurrent.GuardedBy;

/**
 * {@link OutputStream} for writing to a {@link RegularFile}.
 *
 * @author Colin Decker
 */
final class JimfsOutputStream extends OutputStream {

  @GuardedBy("this")
  @VisibleForTesting
  RegularFile file;

  @GuardedBy("this")
  private long pos;

  private final boolean append;
  private final FileSystemState fileSystemState;

  JimfsOutputStream(RegularFile file, boolean append, FileSystemState fileSystemState) {
    this.file = checkNotNull(file);
    this.append = append;
    this.fileSystemState = fileSystemState;
    fileSystemState.register(this);
  }

  @Override
  public synchronized void write(int b) throws IOException {
    checkNotClosed();

    file.writeLock().lock();
    try {
      if (append) {
        pos = file.sizeWithoutLocking();
      }
      file.write(pos++, (byte) b);

      file.updateModifiedTime();
    } finally {
      file.writeLock().unlock();
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

    file.writeLock().lock();
    try {
      if (append) {
        pos = file.sizeWithoutLocking();
      }
      pos += file.write(pos, b, off, len);

      file.updateModifiedTime();
    } finally {
      file.writeLock().unlock();
    }
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
