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

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Object for storing file metadata. Conceptually similar to a UNIX
 * <a href="http://en.wikipedia.org/wiki/Inode">inode</a>.
 *
 * @author Colin Decker
 */
public abstract class Inode {

  private final int id;

  private int links;

  private long creationTime;
  private long lastAccessTime;
  private long lastModifiedTime;

  @Nullable
  private Map<String, Object> attributes; // null when only the basic view is used (default)

  protected Inode(int id) {
    this.id = id;

    long now = System.currentTimeMillis(); // TODO(cgdecker): Use a Clock
    this.creationTime = now;
    this.lastAccessTime = now;
    this.lastModifiedTime = now;
  }

  /**
   * Returns the ID of this inode.
   */
  public final int id() {
    return id;
  }

  /**
   * Returns whether or not the file this inode represents is a directory.
   */
  public abstract boolean isDirectory();

  /**
   * Returns whether or not the file this inode represents is a regular file.
   */
  public abstract boolean isRegularFile();

  /**
   * Returns whether or not the file is this inode represents a symbolic link.
   */
  public abstract boolean isSymbolicLink();

  /**
   * Returns the size, in bytes, of the content of the file this inode represents.
   */
  public abstract long size();

  /**
   * Returns the current count of links to this inode.
   */
  public final synchronized int links() {
    return links;
  }

  /**
   * Increments the link count.
   */
  public final synchronized void incrementLinkCount() {
    links++;
  }

  /**
   * Decrements and returns the link count.
   */
  public final synchronized int decrementLinkCount() {
    return --links;
  }

  /**
   * Gets the creation time of the file.
   */
  public final synchronized long getCreationTime() {
    return creationTime;
  }

  /**
   * Gets the last access time of the file.
   */
  public final synchronized long getLastAccessTime() {
    return lastAccessTime;
  }

  /**
   * Gets the last modified time of the file.
   */
  public final synchronized long getLastModifiedTime() {
    return lastModifiedTime;
  }

  /**
   * Sets the creation time of the file.
   */
  public final synchronized void setCreationTime(long creationTime) {
    this.creationTime = creationTime;
  }

  /**
   * Sets the last access time of the file.
   */
  public final synchronized void setLastAccessTime(long lastAccessTime) {
    this.lastAccessTime = lastAccessTime;
  }

  /**
   * Sets the last modified time of the file.
   */
  public final synchronized void setLastModifiedTime(long lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
  }

  /**
   * Sets the last access time of the file to the current time.
   */
  public final void updateAccessTime() {
    setLastAccessTime(System.currentTimeMillis());
  }

  /**
   * Sets the last modified time of the file to the current time.
   */
  public final void updateModifiedTime() {
    setLastModifiedTime(System.currentTimeMillis());
  }

  /**
   * Returns the attribute keys contained in the attributes map for the file.
   */
  public final synchronized ImmutableSet<String> getAttributeKeys() {
    if (attributes == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.copyOf(attributes.keySet());
  }

  /**
   * Gets the value of the attribute with the given key. The value is automatically cast to the
   * target type inferred by the call site, as it is assumed that the caller knows what type the
   * value will be.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public final synchronized <T> T getAttribute(String key) {
    if (attributes == null) {
      return null;
    }
    return (T) attributes.get(key);
  }

  /**
   * Sets the attribute with the given key to the given value.
   */
  public final synchronized void setAttribute(String key, Object value) {
    if (attributes == null) {
      attributes = new HashMap<>();
    }
    attributes.put(key, value);
  }

  /**
   * Deletes the attribute with the given key.
   */
  public final synchronized void deleteAttribute(String key) {
    if (attributes != null) {
      attributes.remove(key);
    }
  }

  /**
   * Copies basic attributes (file times) from this inode to the given inode.
   */
  public final synchronized void copyBasicAttributes(Inode target) {
    target.setFileTimes(creationTime, lastModifiedTime, lastAccessTime);
  }

  private synchronized void setFileTimes(
      long creationTime, long lastModifiedTime, long lastAccessTime) {
    this.creationTime = creationTime;
    this.lastModifiedTime = lastModifiedTime;
    this.lastAccessTime = lastAccessTime;
  }

  /**
   * Copies the attributes from this inode to the given inode.
   */
  public final synchronized void copyAttributes(Inode target) {
    copyBasicAttributes(target);
    target.putAll(attributes);
  }

  private synchronized void putAll(@Nullable Map<String, Object> attributes) {
    if (attributes != null && this.attributes != attributes) {
      if (this.attributes == null) {
        this.attributes = new HashMap<>();
      }
      this.attributes.putAll(attributes);
    }
  }

  /**
   * Callback for looking up an inode.
   *
   * @author Colin Decker
   */
  public interface Lookup {

    /**
     * Looks up the inode.
     *
     * @throws IOException if the lookup fails for any reason, such as the file not existing
     */
    Inode lookup() throws IOException;
  }
}
