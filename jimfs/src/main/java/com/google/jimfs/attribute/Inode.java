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

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

/**
 * Object for storing file metadata. Conceptually similar to a UNIX
 * <a href="http://en.wikipedia.org/wiki/Inode">inode</a>.
 *
 * @author Colin Decker
 */
public abstract class Inode {

  private final int id;

  private final AtomicInteger links = new AtomicInteger();

  private final AtomicLong creationTime;
  private final AtomicLong lastAccessTime;
  private final AtomicLong lastModifiedTime;

  private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

  protected Inode(int id) {
    this.id = id;

    long now = System.currentTimeMillis(); // TODO(cgdecker): Use a Clock
    this.creationTime = new AtomicLong(now);
    this.lastAccessTime = new AtomicLong(now);
    this.lastModifiedTime = new AtomicLong(now);
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
  public final int links() {
    return links.get();
  }

  /**
   * Increments the link count.
   */
  public final void incrementLinkCount() {
    links.incrementAndGet();
  }

  /**
   * Decrements and returns the link count.
   */
  public final int decrementLinkCount() {
    return links.decrementAndGet();
  }

  /**
   * Gets the creation time of the file.
   */
  public final long getCreationTime() {
    return creationTime.get();
  }

  /**
   * Gets the last access time of the file.
   */
  public final long getLastAccessTime() {
    return lastAccessTime.get();
  }

  /**
   * Gets the last modified time of the file.
   */
  public final long getLastModifiedTime() {
    return lastModifiedTime.get();
  }

  /**
   * Sets the creation time of the file.
   */
  public final void setCreationTime(long creationTime) {
    this.creationTime.set(creationTime);
  }

  /**
   * Sets the last access time of the file.
   */
  public final void setLastAccessTime(long lastAccessTime) {
    this.lastAccessTime.set(lastAccessTime);
  }

  /**
   * Sets the last modified time of the file.
   */
  public final void setLastModifiedTime(long lastModifiedTime) {
    this.lastModifiedTime.set(lastModifiedTime);
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
  public final Set<String> getAttributeKeys() {
    return attributes.keySet();
  }

  /**
   * Gets the value of the attribute with the given key. The value is automatically cast to the
   * target type inferred by the call site, as it is assumed that the caller knows what type the
   * value will be.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public final <T> T getAttribute(String key) {
    return (T) attributes.get(key);
  }

  /**
   * Sets the attribute with the given key to the given value.
   */
  public final void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  /**
   * Deletes the attribute with the given key.
   */
  public final void deleteAttribute(String key) {
    attributes.remove(key);
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
