/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

package com.google.common.io.jimfs.attribute;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.jimfs.file.File;
import com.google.common.io.jimfs.file.FileProvider;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/**
 * Attribute provider that provides attributes common to all file systems,
 * the {@link BasicFileAttributeView} ("basic" or no view prefix), and allows the reading of
 * {@link BasicFileAttributes}.
 *
 * @author Colin Decker
 */
public class BasicAttributeProvider extends AbstractAttributeProvider implements
    AttributeViewProvider<BasicFileAttributeView>, AttributeReader<BasicFileAttributes> {

  public static final String VIEW = "basic";

  public static final String SIZE = "size";
  public static final String LAST_MODIFIED_TIME = "lastModifiedTime";
  public static final String LAST_ACCESS_TIME = "lastAccessTime";
  public static final String CREATION_TIME = "creationTime";
  public static final String FILE_KEY = "fileKey";
  public static final String IS_DIRECTORY = "isDirectory";
  public static final String IS_REGULAR_FILE = "isRegularFile";
  public static final String IS_SYMBOLIC_LINK = "isSymbolicLink";
  public static final String IS_OTHER = "isOther";

  private static final ImmutableSet<AttributeSpec> ATTRIBUTES = ImmutableSet.of(
      AttributeSpec.unsettable("size", Long.class),
      AttributeSpec.settable("lastModifiedTime", FileTime.class),
      AttributeSpec.settable("lastAccessTime", FileTime.class),
      AttributeSpec.settable("creationTime", FileTime.class),
      AttributeSpec.unsettable("fileKey", Object.class),
      AttributeSpec.unsettable("isDirectory", Boolean.class),
      AttributeSpec.unsettable("isRegularFile", Boolean.class),
      AttributeSpec.unsettable("isSymbolicLink", Boolean.class),
      AttributeSpec.unsettable("isOther", Boolean.class));

  public BasicAttributeProvider() {
    super(ATTRIBUTES);
  }

  @Override
  public String name() {
    return VIEW;
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of();
  }

  @Override
  public void setInitial(File file) {
    // FileTime now = file.clock().now();
    // TODO(cgdecker): re-add use of a Clock... maybe.
    FileTime now = FileTime.fromMillis(System.currentTimeMillis());
    set(file, LAST_MODIFIED_TIME, now);
    set(file, LAST_ACCESS_TIME, now);
    set(file, CREATION_TIME, now);
  }

  @Override
  public Object get(File file, String attribute) {
    switch (attribute) {
      case SIZE:
        return (long) file.content().size();
      case FILE_KEY:
        return file.id();
      case IS_DIRECTORY:
        return file.isDirectory();
      case IS_REGULAR_FILE:
        return file.isRegularFile();
      case IS_SYMBOLIC_LINK:
        return file.isSymbolicLink();
      case IS_OTHER:
        return false;
      default:
        return super.get(file, attribute);
    }
  }

  @Override
  public Class<BasicFileAttributeView> viewType() {
    return BasicFileAttributeView.class;
  }

  @Override
  public View getView(FileProvider fileProvider) {
    return new View(this, fileProvider);
  }

  @Override
  public Class<BasicFileAttributes> attributesType() {
    return BasicFileAttributes.class;
  }

  @Override
  public Attributes read(File file) {
    try {
      return new Attributes(getView(FileProvider.ofFile(file)));
    } catch (IOException e) {
      throw new AssertionError(e); // FileProvider.ofFile won't throw IOException
    }
  }

  /**
   * Implementation of {@link BasicFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements BasicFileAttributeView {

    protected View(AttributeProvider provider, FileProvider fileProvider) {
      super(provider, fileProvider);
    }

    @Override
    public BasicFileAttributes readAttributes() throws IOException {
      // create a temporary view that doesn't have to locate the file for each get
      View view = new View(provider(), FileProvider.ofFile(file()));
      return new Attributes(view);
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
        throws IOException {
      File file = file();

      if (lastModifiedTime != null) {
        provider().set(file, LAST_MODIFIED_TIME, lastModifiedTime);
      }

      if (lastAccessTime != null) {
        provider().set(file, LAST_ACCESS_TIME, lastAccessTime);
      }

      if (createTime != null) {
        provider().set(file, CREATION_TIME, createTime);
      }
    }
  }

  /**
   * Implementation of {@link BasicFileAttributes}.
   */
  public static class Attributes implements BasicFileAttributes {

    private final FileTime lastModifiedTime;
    private final FileTime lastAccessTime;
    private final FileTime creationTime;
    private final boolean isRegularFile;
    private final boolean isDirectory;
    private final boolean isSymbolicLink;
    private final boolean isOther;
    private final long size;
    private final Object fileKey;

    private Attributes(View view) throws IOException {
      this.lastModifiedTime = view.get(LAST_MODIFIED_TIME);
      this.lastAccessTime = view.get(LAST_ACCESS_TIME);
      this.creationTime = view.get(CREATION_TIME);
      this.isRegularFile = view.get(IS_REGULAR_FILE);
      this.isDirectory = view.get(IS_DIRECTORY);
      this.isSymbolicLink = view.get(IS_SYMBOLIC_LINK);
      this.isOther = view.get(IS_OTHER);
      this.size = view.get(SIZE);
      this.fileKey = view.get(FILE_KEY);
    }

    protected Attributes(BasicFileAttributes attributes) {
      this.lastModifiedTime = attributes.lastModifiedTime();
      this.lastAccessTime = attributes.lastAccessTime();
      this.creationTime = attributes.creationTime();
      this.isRegularFile = attributes.isRegularFile();
      this.isDirectory = attributes.isDirectory();
      this.isSymbolicLink = attributes.isSymbolicLink();
      this.isOther = attributes.isOther();
      this.size = attributes.size();
      this.fileKey = attributes.fileKey();
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
      return isRegularFile;
    }

    @Override
    public boolean isDirectory() {
      return isDirectory;
    }

    @Override
    public boolean isSymbolicLink() {
      return isSymbolicLink;
    }

    @Override
    public boolean isOther() {
      return isOther;
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
