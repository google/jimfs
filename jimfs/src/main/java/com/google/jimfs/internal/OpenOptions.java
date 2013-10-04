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

package com.google.jimfs.internal;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.SPARSE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.Set;

/**
 * Options controlling how files are opened and what operations are permitted on the opened file.
 *
 * @author Colin Decker
 */
final class OpenOptions extends LinkOptions {

  /**
   * Returns the default options for reading when no options are specified.
   */
  public static final OpenOptions DEFAULT_READ = from(READ);

  /**
   * Returns the default options for writing used when no options are specified.
   */
  public static final OpenOptions DEFAULT_WRITE = from(WRITE, TRUNCATE_EXISTING, CREATE);

  /**
   * Creates a new options object from the given options.
   */
  public static OpenOptions from(Object... options) {
    return from(Arrays.asList(options));
  }

  /**
   * Creates a new options object from the given options.
   */
  public static OpenOptions from(Iterable<?> options) {
    if (options instanceof Set<?>) {
      return new OpenOptions((Set<?>) options);
    }
    return new OpenOptions(ImmutableSet.copyOf(options));
  }

  private final ImmutableSet<?> options;

  private OpenOptions(Set<?> options) {
    super(options);
    this.options = ImmutableSet.copyOf(options);
  }

  /**
   * Returns whether or not to open the file for reading.
   */
  public boolean isRead() {
    return options.contains(READ);
  }

  /**
   * Returns whether or not to open the file for writing.
   */
  public boolean isWrite() {
    return options.contains(WRITE);
  }

  /**
   * Returns whether or not to open the file in append mode.
   */
  public boolean isAppend() {
    return options.contains(APPEND);
  }

  /**
   * Returns whether or not to truncate the file when opening it.
   */
  public boolean isTruncateExisting() {
    return options.contains(TRUNCATE_EXISTING);
  }

  /**
   * Returns whether or not to create the file if it doesn't exist.
   */
  public boolean isCreate() {
    return options.contains(CREATE);
  }

  /**
   * Returns whether or not to create the file and throw an exception if it already exists.
   */
  public boolean isCreateNew() {
    return options.contains(CREATE_NEW);
  }

  /**
   * Returns whether or not the created file should be sparse, if supported.
   */
  public boolean isSparse() {
    return options.contains(SPARSE);
  }
}
