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

package com.google.jimfs.internal.file;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.common.primitives.Longs;
import com.google.jimfs.internal.bytestore.ByteStore;
import com.google.jimfs.internal.path.JimfsPath;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single file object. Similar in concept to an <i>inode</i> in that it mostly stores file
 * metadata, but also keeps a reference to the file's content.
 *
 * @author Colin Decker
 */
public final class File {

  private final long id;

  private final AtomicInteger links = new AtomicInteger();
  private final AtomicLong creationTime = new AtomicLong();
  private final AtomicLong lastAccessTime = new AtomicLong();
  private final AtomicLong lastModifiedTime = new AtomicLong();

  private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

  private final FileContent content;

  public File(long id, FileContent content) {
    this.id = id;
    this.content = checkNotNull(content);
  }

  /**
   * Returns the ID of this file.
   */
  public long id() {
    return id;
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
  public boolean isRootDirectory() {
    // only root directories have their parent link pointing to themselves
    return isDirectory() && equals(((DirectoryTable) content()).parent());
  }

  /**
   * Returns the file content, with a cast to allow the type to be inferred at the call site.
   */
  @SuppressWarnings("unchecked")
  public <C extends FileContent> C content() {
    return (C) content;
  }

  /**
   * Returns the current count of links to this file.
   */
  public int links() {
    return links.get();
  }

  /**
   * Increments the link count.
   */
  public void linked() {
    links.incrementAndGet();
  }

  /**
   * Decrements and returns the link count.
   */
  public int unlinked() {
    return links.decrementAndGet();
  }

  /**
   * Returns the attribute keys contained in the attributes map for this file.
   */
  public Iterable<String> getAttributeKeys() {
    return attributes.keySet();
  }

  /**
   * Gets the value of the attribute with the given key.
   */
  public Object getAttribute(String key) {
    return attributes.get(key);
  }

  /**
   * Sets the attribute with the given key to the given value.
   */
  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  /**
   * Deletes the attribute with the given key.
   */
  public void deleteAttribute(String key) {
    attributes.remove(key);
  }

  /**
   * Gets the creation time of this file.
   */
  public long getCreationTime() {
    return creationTime.get();
  }

  /**
   * Gets the last access time of this file.
   */
  public long getLastAccessTime() {
    return lastAccessTime.get();
  }

  /**
   * Gets the last updateModifiedTime time of this file.
   */
  public long getLastModifiedTime() {
    return lastModifiedTime.get();
  }

  /**
   * Sets the creation time of this file.
   */
  public void setCreationTime(long creationTime) {
    this.creationTime.set(creationTime);
  }

  /**
   * Sets the last access time of this file.
   */
  public void setLastAccessTime(long lastAccessTime) {
    this.lastAccessTime.set(lastAccessTime);
  }

  /**
   * Sets the last updateModifiedTime time of this file.
   */
  public void setLastModifiedTime(long lastModifiedTime) {
    this.lastModifiedTime.set(lastModifiedTime);
  }

  /**
   * Updates the last access time of this file to the current time. Called when the file content
   * is read.
   */
  public void updateAccessTime() {
    setLastAccessTime(System.currentTimeMillis());
  }

  /**
   * Updates the last modified time of this file to the current time. Called when the file content
   * is written.
   */
  public void updateModifiedTime() {
    setLastModifiedTime(System.currentTimeMillis());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof File) {
      File other = (File) obj;
      return id == other.id;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(id);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("id", id)
        .toString();
  }
}
