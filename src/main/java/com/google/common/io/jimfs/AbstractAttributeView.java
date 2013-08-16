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
import java.nio.file.attribute.FileAttributeView;

/**
 * Abstract base class for {@link FileAttributeView} implementations.
 *
 * @author Colin Decker
 */
abstract class AbstractAttributeView implements FileAttributeView {

  private final AttributeProvider attributeProvider;
  private final FileProvider fileProvider;

  public AbstractAttributeView(AttributeProvider attributeProvider,
      FileProvider fileProvider) {
    this.attributeProvider = checkNotNull(attributeProvider);
    this.fileProvider = checkNotNull(fileProvider);
  }

  @Override
  public final String name() {
    return attributeProvider.name();
  }

  /**
   * Gets the file to get or set attributes for.
   */
  public final File file() throws IOException {
    return fileProvider.getFile();
  }

  /**
   * Gets the attribute provider this view uses.
   */
  public final AttributeProvider provider() {
    return attributeProvider;
  }

  /**
   * Gets the value of the given attribute for the file located by this view.
   */
  @SuppressWarnings("unchecked")
  public final <V> V get(String attribute) throws IOException {
    return (V) attributeProvider.get(file(), attribute);
  }

  /**
   * Sets the value of the given attribute for the file located by this view.
   */
  public final void set(String attribute, Object value) throws IOException {
    attributeProvider.set(file(), attribute, value);
  }
}
