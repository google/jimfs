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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

/**
 * A provider of a {@link File} object. May provide the file directly or by looking it up in a
 * file tree.
 *
 * @author Colin Decker
 */
public abstract class FileProvider {

  /**
   * Gets a file.
   *
   * @throws IOException if an I/O error occurred looking up the file, the file doesn't exist, etc.
   */
  public abstract File getFile() throws IOException;

  /**
   * Returns a {@link FileProvider} that always returns the given {@link File} instance.
   */
  public static FileProvider ofFile(final File file) {
    checkNotNull(file);
    return new FileProvider() {
      @Override
      public File getFile() throws IOException {
        return file;
      }
    };
  }
}
