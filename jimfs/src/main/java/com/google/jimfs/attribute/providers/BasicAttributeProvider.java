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

package com.google.jimfs.attribute.providers;

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.FileMetadata;

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
  public Object get(FileMetadata metadata, String attribute) {
    switch (attribute) {
      case "size":
        return metadata.size();
      case "fileKey":
        return metadata.id();
      case "isDirectory":
        return metadata.isDirectory();
      case "isRegularFile":
        return metadata.isRegularFile();
      case "isSymbolicLink":
        return metadata.isSymbolicLink();
      case "isOther":
        return !metadata.isDirectory() && !metadata.isRegularFile() && !metadata.isSymbolicLink();
      case "creationTime":
        return FileTime.fromMillis(metadata.getCreationTime());
      case "lastAccessTime":
        return FileTime.fromMillis(metadata.getLastAccessTime());
      case "lastModifiedTime":
        return FileTime.fromMillis(metadata.getLastModifiedTime());
      default:
        return null;
    }
  }

  @Override
  public void set(FileMetadata metadata,
      String view, String attribute, Object value, boolean create) {
    switch (attribute) {
      case "creationTime":
        checkNotCreate(view, attribute, create);
        metadata.setCreationTime(
            checkType(view, attribute, value, FileTime.class).toMillis());
        break;
      case "lastAccessTime":
        checkNotCreate(view, attribute, create);
        metadata.setLastAccessTime(checkType(view, attribute, value, FileTime.class).toMillis());
        break;
      case "lastModifiedTime":
        checkNotCreate(view, attribute, create);
        metadata.setLastModifiedTime(checkType(view, attribute, value, FileTime.class).toMillis());
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
  public BasicFileAttributeView view(FileMetadata.Lookup lookup,
      Map<String, FileAttributeView> inheritedViews) {
    return new View(lookup);
  }

  @Override
  public Class<BasicFileAttributes> attributesType() {
    return BasicFileAttributes.class;
  }

  @Override
  public BasicFileAttributes readAttributes(FileMetadata metadata) {
    return new Attributes(metadata);
  }

  /**
   * Implementation of {@link BasicFileAttributeView}.
   */
  private static final class View extends AbstractAttributeView implements BasicFileAttributeView {

    protected View(FileMetadata.Lookup lookup) {
      super(lookup);
    }

    @Override
    public String name() {
      return "basic";
    }

    @Override
    public BasicFileAttributes readAttributes() throws IOException {
      return new Attributes(lookupMetadata());
    }

    @Override
    public void setTimes(
        @Nullable FileTime lastModifiedTime,
        @Nullable FileTime lastAccessTime,
        @Nullable FileTime createTime) throws IOException {
      FileMetadata metadata = lookupMetadata();

      if (lastModifiedTime != null) {
        metadata.setLastModifiedTime(lastModifiedTime.toMillis());
      }

      if (lastAccessTime != null) {
        metadata.setLastAccessTime(lastAccessTime.toMillis());
      }

      if (createTime != null) {
        metadata.setCreationTime(createTime.toMillis());
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

    protected Attributes(FileMetadata metadata) {
      this.lastModifiedTime = FileTime.fromMillis(metadata.getLastModifiedTime());
      this.lastAccessTime = FileTime.fromMillis(metadata.getLastAccessTime());
      this.creationTime = FileTime.fromMillis(metadata.getCreationTime());
      this.regularFile = metadata.isRegularFile();
      this.directory = metadata.isDirectory();
      this.symbolicLink = metadata.isSymbolicLink();
      this.size = metadata.size();
      this.fileKey = metadata.id();
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
