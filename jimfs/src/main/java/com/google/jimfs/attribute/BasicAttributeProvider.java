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
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Attribute provider that provides attributes common to all file systems,
 * the {@link BasicFileAttributeView} ("basic" or no view prefix), and allows the reading of
 * {@link BasicFileAttributes}.
 *
 * @author Colin Decker
 */
final class BasicAttributeProvider extends AttributeProvider<BasicFileAttributeView> {

  private static final ImmutableSet<String> ATTRIBUTES = ImmutableSet.of(
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
  public Object get(Inode inode, String attribute) {
    switch (attribute) {
      case "size":
        return inode.size();
      case "fileKey":
        return inode.id();
      case "isDirectory":
        return inode.isDirectory();
      case "isRegularFile":
        return inode.isRegularFile();
      case "isSymbolicLink":
        return inode.isSymbolicLink();
      case "isOther":
        return !inode.isDirectory() && !inode.isRegularFile() && !inode.isSymbolicLink();
      case "creationTime":
        return FileTime.fromMillis(inode.getCreationTime());
      case "lastAccessTime":
        return FileTime.fromMillis(inode.getLastAccessTime());
      case "lastModifiedTime":
        return FileTime.fromMillis(inode.getLastModifiedTime());
      default:
        return null;
    }
  }

  @Override
  public void set(Inode inode,
      String view, String attribute, Object value, boolean create) {
    switch (attribute) {
      case "creationTime":
        checkNotCreate(view, attribute, create);
        inode.setCreationTime(
            checkType(view, attribute, value, FileTime.class).toMillis());
        break;
      case "lastAccessTime":
        checkNotCreate(view, attribute, create);
        inode.setLastAccessTime(checkType(view, attribute, value, FileTime.class).toMillis());
        break;
      case "lastModifiedTime":
        checkNotCreate(view, attribute, create);
        inode.setLastModifiedTime(checkType(view, attribute, value, FileTime.class).toMillis());
        break;
      case "size":
      case "fileKey":
      case "isDirectory":
      case "isRegularFile":
      case "isSymbolicLink":
      case "isOther":
        throw unsettable(view, attribute);
    }
  }

  @Override
  public Class<BasicFileAttributeView> viewType() {
    return BasicFileAttributeView.class;
  }

  @Override
  public BasicFileAttributeView view(Inode.Lookup lookup,
      Map<String, FileAttributeView> inheritedViews) {
    return new View(lookup);
  }

  @Override
  public Class<BasicFileAttributes> attributesType() {
    return BasicFileAttributes.class;
  }

  @Override
  public BasicFileAttributes readAttributes(Inode inode) {
    return new Attributes(inode);
  }

  /**
   * Implementation of {@link BasicFileAttributeView}.
   */
  private static final class View extends AbstractAttributeView implements BasicFileAttributeView {

    protected View(Inode.Lookup lookup) {
      super(lookup);
    }

    @Override
    public String name() {
      return "basic";
    }

    @Override
    public BasicFileAttributes readAttributes() throws IOException {
      return new Attributes(lookupInode());
    }

    @Override
    public void setTimes(
        @Nullable FileTime lastModifiedTime,
        @Nullable FileTime lastAccessTime,
        @Nullable FileTime createTime) throws IOException {
      Inode inode = lookupInode();

      if (lastModifiedTime != null) {
        inode.setLastModifiedTime(lastModifiedTime.toMillis());
      }

      if (lastAccessTime != null) {
        inode.setLastAccessTime(lastAccessTime.toMillis());
      }

      if (createTime != null) {
        inode.setCreationTime(createTime.toMillis());
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

    protected Attributes(Inode inode) {
      this.lastModifiedTime = FileTime.fromMillis(inode.getLastModifiedTime());
      this.lastAccessTime = FileTime.fromMillis(inode.getLastAccessTime());
      this.creationTime = FileTime.fromMillis(inode.getCreationTime());
      this.regularFile = inode.isRegularFile();
      this.directory = inode.isDirectory();
      this.symbolicLink = inode.isSymbolicLink();
      this.size = inode.size();
      this.fileKey = inode.id();
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
