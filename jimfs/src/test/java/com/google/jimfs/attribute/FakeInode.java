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

/**
 * @author Colin Decker
 */
public class FakeInode extends Inode {

  private final boolean directory;
  private final boolean regularFile;
  private final boolean symbolicLink;
  private final long size;

  public FakeInode(long id) {
    this(id, true, false, false, 0);
  }

  public FakeInode(long id,
      boolean directory, boolean regularFile, boolean symbolicLink, long size) {
    super(id);
    this.directory = directory;
    this.regularFile = regularFile;
    this.symbolicLink = symbolicLink;
    this.size = size;
  }

  @Override
  public boolean isDirectory() {
    return directory;
  }

  @Override
  public boolean isRegularFile() {
    return regularFile;
  }

  @Override
  public boolean isSymbolicLink() {
    return symbolicLink;
  }

  @Override
  public long size() {
    return size;
  }
}
