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

import java.io.IOException;
import java.io.InputStream;

/**
 * {@link InputStream} for reading from a {@link ByteStore}.
 *
 * @author Colin Decker
 */
final class ByteStoreInputStream extends InputStream {

  private volatile ByteStore store;

  private int pos;

  public ByteStoreInputStream(ByteStore store) {
    this.store = checkNotNull(store);
  }

  @Override
  public synchronized int read() throws IOException {
    checkNotClosed();
    return store.read(pos++); // it's ok for pos to go beyond size()
  }

  @Override
  public synchronized int read(byte[] b, int off, int len) throws IOException {
    checkNotClosed();
    int read = store.read(pos, b, off, len);
    if (read != -1) {
      pos += read;
    }
    return read;
  }

  @Override
  public synchronized long skip(long n) throws IOException {
    if (n <= 0) {
      return 0;
    }

    // available() must be an int, so the min must be also
    int skip = (int) Math.min(available(), n);
    pos += skip;
    return skip;
  }

  @Override
  public synchronized int available() throws IOException {
    return Math.max(store.size() - pos, 0);
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
