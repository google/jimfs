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

package com.google.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;

import javax.annotation.Nullable;

/**
 * A file object, containing both the file's metadata and a reference to its content.
 *
 * @author Colin Decker
 */
public final class File {

  private final int id;

  private int links;

  private long creationTime;
  private long lastAccessTime;
  private long lastModifiedTime;

  @Nullable // null when only the basic view is used (default)
  private Table<String, String, Object> attributes;

  private final FileContent content;

  File(int id, FileContent content) {
    this.id = id;

    long now = System.currentTimeMillis(); // TODO(cgdecker): Use a Clock
    this.creationTime = now;
    this.lastAccessTime = now;
    this.lastModifiedTime = now;
    this.content = checkNotNull(content);
  }

  /**
   * Returns the ID of this file.
   */
  public int id() {
    return id;
  }

  /**
   * Returns the content of this file.
   */
  FileContent content() {
    return content;
  }

  /**
   * Returns the size, in bytes, of this file's content. Directories and symbolic links have a size
   * of 0.
   */
  public long size() {
    return content.size();
  }

  /**
   * Returns whether or not this file is a directory.
   */
  public boolean isDirectory() {
    return content instanceof DirectoryTable;
  }

  /**
   * Returns whether or not this file is a regular file.
   */
  public boolean isRegularFile() {
    return content instanceof ByteStore;
  }

  /**
   * Returns whether or not this file is a symbolic link.
   */
  public boolean isSymbolicLink() {
    return content instanceof JimfsPath;
  }

  /**
   * Returns whether or not this file is a root directory of the file system.
   */
  boolean isRootDirectory() {
    // only root directories have their parent link pointing to themselves
    return isDirectory() && equals(asDirectory().parent());
  }

  /**
   * Returns a view of this file as a byte store.
   */
  ByteStore asBytes() {
    return (ByteStore) content;
  }

  /**
   * Returns a view of this file as a directory table.
   */
  DirectoryTable asDirectory() {
    return (DirectoryTable) content;
  }

  /**
   * Gets the target of this symbolic link.
   */
  public JimfsPath asTargetPath() {
    return (JimfsPath) content;
  }

  /**
   * Returns the current count of links to this file.
   */
  public synchronized int links() {
    return links;
  }

  /**
   * Increments the link count.
   */
  synchronized void incrementLinkCount() {
    links++;
  }

  /**
   * Decrements and returns the link count.
   */
  synchronized int decrementLinkCount() {
    return --links;
  }

  /**
   * Gets the creation time of the file.
   */
  public synchronized long getCreationTime() {
    return creationTime;
  }

  /**
   * Gets the last access time of the file.
   */
  public synchronized long getLastAccessTime() {
    return lastAccessTime;
  }

  /**
   * Gets the last modified time of the file.
   */
  public synchronized long getLastModifiedTime() {
    return lastModifiedTime;
  }

  /**
   * Sets the creation time of the file.
   */
  synchronized void setCreationTime(long creationTime) {
    this.creationTime = creationTime;
  }

  /**
   * Sets the last access time of the file.
   */
  synchronized void setLastAccessTime(long lastAccessTime) {
    this.lastAccessTime = lastAccessTime;
  }

  /**
   * Sets the last modified time of the file.
   */
  synchronized void setLastModifiedTime(long lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
  }

  /**
   * Sets the last access time of the file to the current time.
   */
  void updateAccessTime() {
    setLastAccessTime(System.currentTimeMillis());
  }

  /**
   * Sets the last modified time of the file to the current time.
   */
  void updateModifiedTime() {
    setLastModifiedTime(System.currentTimeMillis());
  }

  /**
   * Returns the names of the attributes contained in the given attribute view in the file's
   * attributes table.
   */
  public synchronized ImmutableSet<String> getAttributeNames(String view) {
    if (attributes == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.copyOf(attributes.row(view).keySet());
  }

  /**
   * Returns the attribute keys contained in the attributes map for the file.
   */
  @VisibleForTesting
  synchronized ImmutableSet<String> getAttributeKeys() {
    if (attributes == null) {
      return ImmutableSet.of();
    }

    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (Table.Cell<String, String, Object> cell : attributes.cellSet()) {
      builder.add(cell.getRowKey() + ':' + cell.getColumnKey());
    }
    return builder.build();
  }

  /**
   * Gets the value of the given attribute in the given view.
   */
  @Nullable
  public synchronized Object getAttribute(String view, String attribute) {
    if (attributes == null) {
      return null;
    }
    return attributes.get(view, attribute);
  }

  /**
   * Sets the given attribute in the given view to the given value.
   */
  public synchronized void setAttribute(String view, String attribute, Object value) {
    if (attributes == null) {
      attributes = HashBasedTable.create();
    }
    attributes.put(view, attribute, value);
  }

  /**
   * Deletes the given attribute from the given view.
   */
  public synchronized void deleteAttribute(String view, String attribute) {
    if (attributes != null) {
      attributes.remove(view, attribute);
    }
  }

  /**
   * Copies basic attributes (file times) from this file to the given file.
   */
  synchronized void copyBasicAttributes(File target) {
    target.setFileTimes(creationTime, lastModifiedTime, lastAccessTime);
  }

  private synchronized void setFileTimes(
      long creationTime, long lastModifiedTime, long lastAccessTime) {
    this.creationTime = creationTime;
    this.lastModifiedTime = lastModifiedTime;
    this.lastAccessTime = lastAccessTime;
  }

  /**
   * Copies the attributes from this file to the given file.
   */
  synchronized void copyAttributes(File target) {
    copyBasicAttributes(target);
    target.putAll(attributes);
  }

  private synchronized void putAll(@Nullable Table<String, String, Object> attributes) {
    if (attributes != null && this.attributes != attributes) {
      if (this.attributes == null) {
        this.attributes = HashBasedTable.create();
      }
      this.attributes.putAll(attributes);
    }
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("id", id())
        .add("contentType", content.getClass().getSimpleName())
        .toString();
  }
}
