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

package com.google.jimfs.internal.attribute;

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;

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
   * Reads all of the attributes associated with this provider from the given store into the given
   * map.
   */
  void readAll(AttributeStore attributes, Map<String, Object> map);

  /**
   * Sets any initial attributes in the given store.
   */
  void setInitial(AttributeStore store);

  /**
   * Returns whether or not it's currently possible to get the given attribute from the given store.
   */
  boolean isGettable(AttributeStore store, String attribute);

  /**
   * Returns the value of the given attribute in the given store.
   */
  Object get(AttributeStore store, String attribute);

  /**
   * Returns the types that are accepted when setting the given attribute.
   */
  ImmutableSet<Class<?>> acceptedTypes(String attribute);

  /**
   * Returns whether or not it's possible for a user to set the given attribute in the given store.
   */
  boolean isSettable(AttributeStore store, String attribute);

  /**
   * Returns whether or not it's possible for a user to set the given attribute during file
   * creation (e.g. by passing a {@link FileAttribute} to
   * {@link Files#createFile(Path, FileAttribute[])}). Only called if
   * {@link #isSettable(AttributeStore, String)} already returned true.
   */
  boolean isSettableOnCreate(String attribute);

  /**
   * Sets the value of the given attribute in the given store.
   */
  void set(AttributeStore store, String attribute, Object value);
}
