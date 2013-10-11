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

package com.google.jimfs.attribute;

import java.nio.file.attribute.FileAttributeView;

/**
 * Interface to be implemented by any {@link AttributeProvider} that provides the ability to
 * create an object of a specific class extending {@link FileAttributeView} that acts as a live
 * view of a specified file.
 *
 * @author Colin Decker
 */
public interface AttributeViewProvider<V extends FileAttributeView> extends AttributeProvider {

  /**
   * Returns the type of the view interface that this provider supports.
   */
  Class<V> viewType();

  /**
   * Returns a view of the file metadata located by the given supplier.
   */
  V getView(FileMetadataSupplier supplier);
}
