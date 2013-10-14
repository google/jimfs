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

package com.google.jimfs.attribute.providers;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.jimfs.attribute.FileMetadata;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;

/**
 * Abstract base class for {@link FileAttributeView} implementations.
 *
 * @author Colin Decker
 */
abstract class AbstractAttributeView implements FileAttributeView {

  private final FileMetadata.Lookup lookup;

  public AbstractAttributeView(FileMetadata.Lookup lookup) {
    this.lookup = checkNotNull(lookup);
  }

  /**
   * Gets the file metadata object to get or set attributes on.
   */
  public final FileMetadata lookupMetadata() throws IOException {
    return lookup.lookup();
  }
}
