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

package com.google.jimfs.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.jimfs.attribute.AttributeStore;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Colin Decker
 */
public class TestAttributeStore implements AttributeStore {

  private final long id;
  private final Type type;
  private long size;
  private int links;
  private long creationTime;

  public TestAttributeStore(long id, Type type) {
    this.id = id;
    this.type = checkNotNull(type);
  }

  private long lastAccessTime;
  private long lastModifiedTime;
  private final Map<String, Object> attributes = new HashMap<>();

  @Override
  public long id() {
    return id;
  }

  @Override
  public boolean isDirectory() {
    return type == Type.DIRECTORY;
  }

  @Override
  public boolean isRegularFile() {
    return type == Type.REGULAR_FILE;
  }

  @Override
  public boolean isSymbolicLink() {
    return type == Type.SYMBOLIC_LINK;
  }

  @Override
  public long size() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  @Override
  public int links() {
    return links;
  }

  public void setLinks(int links) {
    this.links = links;
  }

  @Override
  public long getCreationTime() {
    return creationTime;
  }

  @Override
  public long getLastAccessTime() {
    return lastAccessTime;
  }

  @Override
  public long getLastModifiedTime() {
    return lastModifiedTime;
  }

  @Override
  public void setCreationTime(long creationTime) {
    this.creationTime = creationTime;
  }

  @Override
  public void setLastAccessTime(long lastAccessTime) {
    this.lastAccessTime = lastAccessTime;
  }

  @Override
  public void setLastModifiedTime(long lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
  }

  @Override
  public Iterable<String> getAttributeKeys() {
    return attributes.keySet();
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

  public enum Type {
    DIRECTORY,
    REGULAR_FILE,
    SYMBOLIC_LINK
  }
}
