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
import com.google.jimfs.attribute.AttributeStore;

import java.util.Collections;
import java.util.Set;
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
final class File implements AttributeStore {

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

  @Override
  public long id() {
    return id;
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

  @Override
  public long size() {
    return content.sizeInBytes();
  }

  /**
   * Returns whether or not this file is a root directory of the file system.
   */
  public boolean isRootDirectory() {
    // only root directories have their parent link pointing to themselves
    return isDirectory() && equals(asDirectoryTable().parent());
  }

  /**
   * Returns the content of this file.
   */
  public FileContent content() {
    return content;
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

  @Override
  public Set<String> getAttributeKeys() {
    return Collections.unmodifiableSet(attributes.keySet());
  }

  @Override
  public Object getAttribute(String key) {
    return attributes.get(key);
  }

  @Override
  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  @Override
  public void deleteAttribute(String key) {
    attributes.remove(key);
  }

  @Override
  public long getCreationTime() {
    return creationTime.get();
  }

  @Override
  public long getLastAccessTime() {
    return lastAccessTime.get();
  }

  @Override
  public long getLastModifiedTime() {
    return lastModifiedTime.get();
  }

  @Override
  public void setCreationTime(long creationTime) {
    this.creationTime.set(creationTime);
  }

  @Override
  public void setLastAccessTime(long lastAccessTime) {
    this.lastAccessTime.set(lastAccessTime);
  }

  @Override
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
