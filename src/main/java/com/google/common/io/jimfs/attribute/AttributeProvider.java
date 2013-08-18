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

package com.google.common.io.jimfs.attribute;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.jimfs.file.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;

/**
 * Provider for handling a specific group of file attributes.
 *
 * @author Colin Decker
 */
public interface AttributeProvider {

  /**
   * Returns the view name that's used to get attributes from this provider.
   */
  String name();

  /**
   * Returns the names of other providers that this provider inherits attributes from.
   */
  ImmutableSet<String> inherits();

  /**
   * Reads all of the attributes associated with this provider from the given file into the given
   * map builder.
   */
  void readAll(File file, ImmutableMap.Builder<String, Object> builder);

  /**
   * Sets any initial attributes on the given file.
   */
  void setInitial(File file);

  /**
   * Returns whether or not it's currently possible to get the given attribute from the given file.
   */
  boolean isGettable(File file, String attribute);

  /**
   * Returns the value of the given attribute for the given file.
   */
  Object get(File file, String attribute);

  /**
   * Returns the types that are accepted when setting the given attribute.
   */
  ImmutableSet<Class<?>> acceptedTypes(String attribute);

  /**
   * Returns whether or not it's possible for a user to set the given attribute for the given file.
   */
  boolean isSettable(File file, String attribute);

  /**
   * Returns whether or not it's possible for a user to set the given attribute during file
   * creation (e.g. by passing a {@link FileAttribute} to
   * {@link Files#createFile(Path, FileAttribute[])}). Only called if
   * {@link #isSettable(File, String)} already returned true.
   */
  boolean isSettableOnCreate(String attribute);

  /**
   * Sets the value of the given attribute for the given file.
   */
  void set(File file, String attribute, Object value);
}
