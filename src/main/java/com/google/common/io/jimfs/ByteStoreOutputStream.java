/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndexes;
import static java.nio.file.StandardOpenOption.APPEND;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.util.Set;

/**
 * {@link OutputStream} for writing to a {@link ByteStore}.
 *
 * @author Colin Decker
 */
final class ByteStoreOutputStream extends OutputStream {

  private volatile ByteStore store;
  private final boolean append;

  private int pos;

  public ByteStoreOutputStream(ByteStore store, Set<? extends OpenOption> options) {
    this.store = checkNotNull(store);
    this.append = options.contains(APPEND);
  }

  @Override
  public synchronized void write(int b) throws IOException {
    checkNotClosed();

    if (append) {
      store.append((byte) b);
    } else {
      store.write(pos++, (byte) b);
    }
  }

  @Override
  public synchronized void write(byte[] b) throws IOException {
    checkNotClosed();

    if (append) {
      store.append(b);
    } else {
      pos += store.write(pos, b);
    }
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) throws IOException {
    checkPositionIndexes(off, off + len, b.length);
    checkNotClosed();

    if (append) {
      store.append(b, off, len);
    } else {
      pos += store.write(pos, b, off, len);
    }
  }

  private void checkNotClosed() throws IOException {
    if (store == null) {
      throw new IOException("stream is closed");
    }
  }

  @Override
  public synchronized void close() throws IOException {
    store = null;
  }
}
