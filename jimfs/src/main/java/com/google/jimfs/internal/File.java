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

import com.google.common.base.Objects;
import com.google.common.primitives.Longs;
import com.google.jimfs.attribute.FileMetadata;

/**
 * A single file object. Similar in concept to an <i>inode</i> in that it mostly stores file
 * metadata, but also keeps a reference to the file's content.
 *
 * @author Colin Decker
 */
final class File extends FileMetadata {

  private final FileContent content;

  public File(long id, FileContent content) {
    super(id);
    this.content = checkNotNull(content);
  }

  /**
   * Returns the content of this file.
   */
  public FileContent content() {
    return content;
  }

  @Override
  public long size() {
    return content.sizeInBytes();
  }

  @Override
  public boolean isDirectory() {
    return content instanceof DirectoryTable;
  }

  @Override
  public boolean isRegularFile() {
    return content instanceof ByteStore;
  }

  @Override
  public boolean isSymbolicLink() {
    return content instanceof JimfsPath;
  }

  /**
   * Returns whether or not this file is a root directory of the file system.
   */
  public boolean isRootDirectory() {
    // only root directories have their parent link pointing to themselves
    return isDirectory() && equals(asDirectoryTable().parent());
  }

  /**
   * Returns a view of this file as a byte store.
   */
  public ByteStore asByteStore() {
    return (ByteStore) content;
  }

  /**
   * Returns a view of this file as a directory table.
   */
  public DirectoryTable asDirectoryTable() {
    return (DirectoryTable) content;
  }

  /**
   * Gets the target of this symbolic link.
   */
  public JimfsPath getTarget() {
    return (JimfsPath) content;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof File) {
      File other = (File) obj;
      return id() == other.id();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(id());
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("id", id())
        .toString();
  }
}
