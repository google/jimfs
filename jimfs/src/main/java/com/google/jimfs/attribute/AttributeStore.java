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

import java.util.Set;

/**
 * An object that stores the attributes of a file.
 *
 * @author Colin Decker
 */
public interface AttributeStore {

  /**
   * Returns the ID of the file.
   */
  long id();

  /**
   * Returns whether or not the file is a directory.
   */
  boolean isDirectory();

  /**
   * Returns whether or not the file is a regular file.
   */
  boolean isRegularFile();

  /**
   * Returns whether or not the file is a symbolic link.
   */
  boolean isSymbolicLink();

  /**
   * Returns the size, in bytes, of the file.
   */
  long size();

  /**
   * Returns the current count of links to the file.
   */
  int links();

  /**
   * Gets the creation time of the file.
   */
  long getCreationTime();

  /**
   * Gets the last access time of the file.
   */
  long getLastAccessTime();

  /**
   * Gets the last modified time of the file.
   */
  long getLastModifiedTime();

  /**
   * Sets the creation time of the file.
   */
  void setCreationTime(long creationTime);

  /**
   * Sets the last access time of the file.
   */
  void setLastAccessTime(long lastAccessTime);

  /**
   * Sets the last modified time of the file.
   */
  void setLastModifiedTime(long lastModifiedTime);

  /**
   * Returns the attribute keys contained in the attributes map for the file.
   */
  Set<String> getAttributeKeys();

  /**
   * Gets the value of the attribute with the given key.
   */
  Object getAttribute(String key);

  /**
   * Sets the attribute with the given key to the given value.
   */
  void setAttribute(String key, Object value);

  /**
   * Deletes the attribute with the given key.
   */
  void deleteAttribute(String key);
}
