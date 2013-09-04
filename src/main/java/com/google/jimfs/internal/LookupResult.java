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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Objects;
import com.google.jimfs.internal.file.File;
import com.google.jimfs.internal.path.Name;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Result of a file lookup.
 *
 * <p>There are several states a result can have.
 *
 * <ul>
 *   <li>the parent directory that should have contained the file was not found:
 *   {@code parentFound() == false}</li>
 *   <li>the parent directory that should have contained the file was found, the file was not:
 *   {@code parentFound() == true}, but {@code found() == false}</li>
 *   <li>the parent directory and the file itself were found: {@code found() == true}</li>
 * </ul>
 *
 * @author Colin Decker
 */
final class LookupResult {

  /**
   * Returns a lookup result with neither the parent nor the file.
   */
  public static LookupResult notFound() {
    return new LookupResult(null, null, null);
  }

  /**
   * Returns a lookup result with only the parent.
   */
  public static LookupResult parentFound(File parent) {
    return new LookupResult(checkNotNull(parent), null, null);
  }

  /**
   * Returns a successful lookup result with a parent, file and file name.
   */
  public static LookupResult found(File parent, File file, Name name) {
    return new LookupResult(checkNotNull(parent), checkNotNull(file), checkNotNull(name));
  }

  @Nullable
  private final File parent;

  @Nullable
  private final File file;

  @Nullable
  private final Name name;

  private LookupResult(
      @Nullable File parent, @Nullable File file, @Nullable Name name) {
    this.parent = parent;
    this.file = file;
    this.name = name;
  }

  /**
   * Returns whether or not the file being looked up was found.
   */
  public boolean found() {
    return file != null;
  }

  /**
   * Throws an exception if the file was not found. Returns this result.
   */
  public LookupResult requireFound(Path pathForException) throws NoSuchFileException {
    if (!found()) {
      throw new NoSuchFileException(pathForException.toString());
    }
    return this;
  }

  /**
   * Throws an exception if the file was found. Returns this result.
   */
  public LookupResult requireNotFound(Path pathForException) throws FileAlreadyExistsException {
    if (found()) {
      throw new FileAlreadyExistsException(pathForException.toString());
    }
    return this;
  }

  /**
   * Throws an exception if the file was not found or if it was found but is not a directory.
   */
  public LookupResult requireDirectory(Path pathForException) throws IOException {
    requireFound(pathForException);
    if (!file.isDirectory()) {
      throw new NotDirectoryException(pathForException.toString());
    }
    return this;
  }

  /**
   * Returns whether or not the parent of the file being looked up was found.
   */
  public boolean parentFound() {
    return parent != null;
  }

  /**
   * Throws an exception if the parent dir was not found. Returns this result.
   */
  public LookupResult requireParentFound(Path pathForException) throws NoSuchFileException {
    if (!parentFound()) {
      throw new NoSuchFileException(pathForException.toString());
    }
    return this;
  }

  /**
   * Gets the parent directory of the path being looked up.
   *
   * @throws IllegalStateException if the result did not
   *     {@linkplain #parentFound() find a parent key}
   */
  public File parent() {
    checkState(parentFound(), "parent was not found");
    return parent;
  }

  /**
   * Gets the file that was being looked up.
   *
   * @throws IllegalStateException if the result was not {@linkplain #found() not found}
   */
  public File file() {
    checkState(found(), "file was not found");
    return file;
  }

  /**
   * Gets the actual name for the directory entry that was found.
   *
   * @throws IllegalStateException if the result was not {@linkplain #found() not found}
   */
  public Name name() {
    checkState(found(), "file was not found");
    return name;
  }

  /**
   * Returns the file key that was found or {@code null} if not found.
   */
  @Nullable
  public File orNull() {
    return file;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .omitNullValues()
        .add("parent", parent)
        .add("file", file)
        .toString();
  }
}
