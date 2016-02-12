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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;

import javax.annotation.Nullable;

/**
 * Attribute provider that provides attributes common to all file systems,
 * the {@link BasicFileAttributeView} ("basic" or no view prefix), and allows the reading of
 * {@link BasicFileAttributes}.
 *
 * @author Colin Decker
 */
final class BasicAttributeProvider extends AttributeProvider {

  private static final ImmutableSet<String> ATTRIBUTES =
      ImmutableSet.of(
          "size",
          "fileKey",
          "isDirectory",
          "isRegularFile",
          "isSymbolicLink",
          "isOther",
          "creationTime",
          "lastAccessTime",
          "lastModifiedTime");

  @Override
  public String name() {
    return "basic";
  }

  @Override
  public ImmutableSet<String> fixedAttributes() {
    return ATTRIBUTES;
  }

  @Override
  public Object get(File file, String attribute) {
    switch (attribute) {
      case "size":
        return file.size();
      case "fileKey":
        return file.id();
      case "isDirectory":
        return file.isDirectory();
      case "isRegularFile":
        return file.isRegularFile();
      case "isSymbolicLink":
        return file.isSymbolicLink();
      case "isOther":
        return !file.isDirectory() && !file.isRegularFile() && !file.isSymbolicLink();
      case "creationTime":
        return FileTime.fromMillis(file.getCreationTime());
      case "lastAccessTime":
        return FileTime.fromMillis(file.getLastAccessTime());
      case "lastModifiedTime":
        return FileTime.fromMillis(file.getLastModifiedTime());
      default:
        return null;
    }
  }

  @Override
  public void set(File file, String view, String attribute, Object value, boolean create) {
    switch (attribute) {
      case "creationTime":
        checkNotCreate(view, attribute, create);
        file.setCreationTime(checkType(view, attribute, value, FileTime.class).toMillis());
        break;
      case "lastAccessTime":
        checkNotCreate(view, attribute, create);
        file.setLastAccessTime(checkType(view, attribute, value, FileTime.class).toMillis());
        break;
      case "lastModifiedTime":
        checkNotCreate(view, attribute, create);
        file.setLastModifiedTime(checkType(view, attribute, value, FileTime.class).toMillis());
        break;
      case "size":
      case "fileKey":
      case "isDirectory":
      case "isRegularFile":
      case "isSymbolicLink":
      case "isOther":
        throw unsettable(view, attribute);
      default:
    }
  }

  @Override
  public Class<BasicFileAttributeView> viewType() {
    return BasicFileAttributeView.class;
  }

  @Override
  public BasicFileAttributeView view(
      FileLookup lookup, ImmutableMap<String, FileAttributeView> inheritedViews) {
    return new View(lookup);
  }

  @Override
  public Class<BasicFileAttributes> attributesType() {
    return BasicFileAttributes.class;
  }

  @Override
  public BasicFileAttributes readAttributes(File file) {
    return new Attributes(file);
  }

  /**
   * Implementation of {@link BasicFileAttributeView}.
   */
  private static final class View extends AbstractAttributeView implements BasicFileAttributeView {

    protected View(FileLookup lookup) {
      super(lookup);
    }

    @Override
    public String name() {
      return "basic";
    }

    @Override
    public BasicFileAttributes readAttributes() throws IOException {
      return new Attributes(lookupFile());
    }

    @Override
    public void setTimes(
        @Nullable FileTime lastModifiedTime,
        @Nullable FileTime lastAccessTime,
        @Nullable FileTime createTime)
        throws IOException {
      File file = lookupFile();

      if (lastModifiedTime != null) {
        file.setLastModifiedTime(lastModifiedTime.toMillis());
      }

      if (lastAccessTime != null) {
        file.setLastAccessTime(lastAccessTime.toMillis());
      }

      if (createTime != null) {
        file.setCreationTime(createTime.toMillis());
      }
    }
  }

  /**
   * Implementation of {@link BasicFileAttributes}.
   */
  static class Attributes implements BasicFileAttributes {

    private final FileTime lastModifiedTime;
    private final FileTime lastAccessTime;
    private final FileTime creationTime;
    private final boolean regularFile;
    private final boolean directory;
    private final boolean symbolicLink;
    private final long size;
    private final Object fileKey;

    protected Attributes(File file) {
      this.lastModifiedTime = FileTime.fromMillis(file.getLastModifiedTime());
      this.lastAccessTime = FileTime.fromMillis(file.getLastAccessTime());
      this.creationTime = FileTime.fromMillis(file.getCreationTime());
      this.regularFile = file.isRegularFile();
      this.directory = file.isDirectory();
      this.symbolicLink = file.isSymbolicLink();
      this.size = file.size();
      this.fileKey = file.id();
    }

    @Override
    public FileTime lastModifiedTime() {
      return lastModifiedTime;
    }

    @Override
    public FileTime lastAccessTime() {
      return lastAccessTime;
    }

    @Override
    public FileTime creationTime() {
      return creationTime;
    }

    @Override
    public boolean isRegularFile() {
      return regularFile;
    }

    @Override
    public boolean isDirectory() {
      return directory;
    }

    @Override
    public boolean isSymbolicLink() {
      return symbolicLink;
    }

    @Override
    public boolean isOther() {
      return false;
    }

    @Override
    public long size() {
      return size;
    }

    @Override
    public Object fileKey() {
      return fileKey;
    }
  }
}
