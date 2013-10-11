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
import com.google.jimfs.attribute.AbstractAttributeProvider;
import com.google.jimfs.attribute.AbstractAttributeView;
import com.google.jimfs.attribute.Attribute;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.AttributeReader;
import com.google.jimfs.attribute.AttributeViewProvider;
import com.google.jimfs.attribute.FileMetadata;
import com.google.jimfs.attribute.FileMetadataSupplier;

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
public final class BasicAttributeProvider extends AbstractAttributeProvider implements
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

  private static final ImmutableSet<Attribute> ATTRIBUTES = ImmutableSet.of(
      Attribute.unsettable(VIEW, SIZE, Long.class),
      Attribute.settable(VIEW, LAST_MODIFIED_TIME, FileTime.class),
      Attribute.settable(VIEW, LAST_ACCESS_TIME, FileTime.class),
      Attribute.settable(VIEW, CREATION_TIME, FileTime.class),
      Attribute.unsettable(VIEW, FILE_KEY, Object.class),
      Attribute.unsettable(VIEW, IS_DIRECTORY, Boolean.class),
      Attribute.unsettable(VIEW, IS_REGULAR_FILE, Boolean.class),
      Attribute.unsettable(VIEW, IS_SYMBOLIC_LINK, Boolean.class),
      Attribute.unsettable(VIEW, IS_OTHER, Boolean.class));

  /**
   * The singleton instance of {@link BasicAttributeProvider}.
   */
  public static final BasicAttributeProvider INSTANCE = new BasicAttributeProvider();

  private BasicAttributeProvider() {
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
  public void setInitial(FileMetadata metadata) {
    // FileTime now = store.clock().now();
    // TODO(cgdecker): re-add use of a Clock... maybe.
    long now = System.currentTimeMillis();
    metadata.setCreationTime(now);
    metadata.setLastAccessTime(now);
    metadata.setLastModifiedTime(now);
  }

  @Override
  public Object get(FileMetadata metadata, String attribute) {
    switch (attribute) {
      case SIZE:
        return metadata.size();
      case FILE_KEY:
        return metadata.id();
      case IS_DIRECTORY:
        return metadata.isDirectory();
      case IS_REGULAR_FILE:
        return metadata.isRegularFile();
      case IS_SYMBOLIC_LINK:
        return metadata.isSymbolicLink();
      case IS_OTHER:
        return !metadata.isDirectory() && !metadata.isRegularFile() && !metadata.isSymbolicLink();
      case CREATION_TIME:
        return FileTime.fromMillis(metadata.getCreationTime());
      case LAST_ACCESS_TIME:
        return FileTime.fromMillis(metadata.getLastAccessTime());
      case LAST_MODIFIED_TIME:
        return FileTime.fromMillis(metadata.getLastModifiedTime());
      default:
        return super.get(metadata, attribute);
    }
  }

  @Override
  public void set(FileMetadata metadata, String attribute, Object value) {
    switch (attribute) {
      case CREATION_TIME:
        metadata.setCreationTime(((FileTime) value).toMillis());
        break;
      case LAST_ACCESS_TIME:
        metadata.setLastAccessTime(((FileTime) value).toMillis());
        break;
      case LAST_MODIFIED_TIME:
        metadata.setLastModifiedTime(((FileTime) value).toMillis());
        break;
      default:
        super.set(metadata, attribute, value);
    }
  }

  @Override
  public Class<BasicFileAttributeView> viewType() {
    return BasicFileAttributeView.class;
  }

  @Override
  public View getView(FileMetadataSupplier supplier) {
    return new View(this, supplier);
  }

  @Override
  public Class<BasicFileAttributes> attributesType() {
    return BasicFileAttributes.class;
  }

  @Override
  public Attributes read(FileMetadata metadata) {
    try {
      return new Attributes(getView(FileMetadataSupplier.of(metadata)));
    } catch (IOException e) {
      throw new AssertionError(e); // IoSupplier.of won't throw IOException
    }
  }

  /**
   * Implementation of {@link BasicFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements BasicFileAttributeView {

    protected View(AttributeProvider provider, FileMetadataSupplier supplier) {
      super(provider, supplier);
    }

    @Override
    public BasicFileAttributes readAttributes() throws IOException {
      // create a temporary view that doesn't have to locate the file for each get
      View view = new View(provider(), FileMetadataSupplier.of(getFileMetadata()));
      return new Attributes(view);
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
        throws IOException {
      // make sure we only lookup the file once
      FileMetadata store = getFileMetadata();
      setIfNotNull(store, LAST_MODIFIED_TIME, lastModifiedTime);
      setIfNotNull(store, LAST_ACCESS_TIME, lastAccessTime);
      setIfNotNull(store, CREATION_TIME, createTime);
    }

    private void setIfNotNull(FileMetadata store, String attribute, FileTime time) {
      if (time != null) {
        provider().set(store, attribute, time);
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
