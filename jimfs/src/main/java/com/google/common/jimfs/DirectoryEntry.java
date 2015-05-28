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

package com.google.common.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Entry in a directory, containing references to the directory itself, the file the entry links to
 * and the name of the entry.
 *
 * <p>May also represent a non-existent entry if the name does not link to any file in the
 * directory.
 */
final class DirectoryEntry {

  private final Directory directory;
  private final Name name;

  @Nullable private final File file;

  @Nullable DirectoryEntry next; // for use in Directory

  DirectoryEntry(Directory directory, Name name, @Nullable File file) {
    this.directory = checkNotNull(directory);
    this.name = checkNotNull(name);
    this.file = file;
  }

  /**
   * Returns {@code true} if and only if this entry represents an existing file.
   */
  public boolean exists() {
    return file != null;
  }

  /**
   * Checks that this entry exists, throwing an exception if not.
   *
   * @return this
   * @throws NoSuchFileException if this entry does not exist
   */
  public DirectoryEntry requireExists(Path pathForException) throws NoSuchFileException {
    if (!exists()) {
      throw new NoSuchFileException(pathForException.toString());
    }
    return this;
  }

  /**
   * Checks that this entry does not exist, throwing an exception if it does.
   *
   * @return this
   * @throws FileAlreadyExistsException if this entry does not exist
   */
  public DirectoryEntry requireDoesNotExist(Path pathForException)
      throws FileAlreadyExistsException {
    if (exists()) {
      throw new FileAlreadyExistsException(pathForException.toString());
    }
    return this;
  }

  /**
   * Checks that this entry exists and links to a directory, throwing an exception if not.
   *
   * @return this
   * @throws NoSuchFileException if this entry does not exist
   * @throws NotDirectoryException if this entry does not link to a directory
   */
  public DirectoryEntry requireDirectory(Path pathForException)
      throws NoSuchFileException, NotDirectoryException {
    requireExists(pathForException);
    if (!file().isDirectory()) {
      throw new NotDirectoryException(pathForException.toString());
    }
    return this;
  }

  /**
   * Checks that this entry exists and links to a symbolic link, throwing an exception if not.
   *
   * @return this
   * @throws NoSuchFileException if this entry does not exist
   * @throws NotLinkException if this entry does not link to a symbolic link
   */
  public DirectoryEntry requireSymbolicLink(Path pathForException)
      throws NoSuchFileException, NotLinkException {
    requireExists(pathForException);
    if (!file().isSymbolicLink()) {
      throw new NotLinkException(pathForException.toString());
    }
    return this;
  }

  /**
   * Returns the directory containing this entry.
   */
  public Directory directory() {
    return directory;
  }

  /**
   * Returns the name of this entry.
   */
  public Name name() {
    return name;
  }

  /**
   * Returns the file this entry links to.
   *
   * @throws IllegalStateException if the file does not exist
   */
  public File file() {
    checkState(exists());
    return file;
  }

  /**
   * Returns the file this entry links to or {@code null} if the file does not exist
   */
  @Nullable
  public File fileOrNull() {
    return file;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof DirectoryEntry) {
      DirectoryEntry other = (DirectoryEntry) obj;
      return directory.equals(other.directory)
          && name.equals(other.name)
          && Objects.equals(file, other.file);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(directory, name, file);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("directory", directory)
        .add("name", name)
        .add("file", file)
        .toString();
  }
}
