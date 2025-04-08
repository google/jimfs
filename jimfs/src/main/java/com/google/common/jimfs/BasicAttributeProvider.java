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
import org.jspecify.annotations.Nullable;

/**
 * Attribute provider that provides attributes common to all file systems, the {@link
 * BasicFileAttributeView} ("basic" or no view prefix), and allows the reading of {@link
 * BasicFileAttributes}.
 *
 * <p>This provider offers the following attributes:
 * <ul>
 *   <li>size
 *   <li>fileKey
 *   <li>isDirectory
 *   <li>isRegularFile
 *   <li>isSymbolicLink
 *   <li>isOther
 *   <li>creationTime
 *   <li>lastAccessTime
 *   <li>lastModifiedTime
 * </ul>
 *
 * <p>It allows the setting of creationTime, lastAccessTime, and lastModifiedTime.
 * All other attributes here are read-only.
 *
 * <p>Implementation note: This class also provides the {@link BasicFileAttributeView}
 * and returns {@link BasicFileAttributes} instances for the given file.
 *
 * <p>Any usage beyond reading and writing these basic attributes falls outside
 * the scope of this provider.
 *
 * <p>All method parameters are non-null unless specified otherwise.
 *
 * <p>This class is thread-safe if the underlying file data is thread-safe.
 *
 * <p>Note: We have extracted a helper method for the {@code isOther} logic, introduced
 * constants for repeated attribute names, and introduced an explaining variable
 * to address code duplication and improve clarity.
 */
final class BasicAttributeProvider extends AttributeProvider {

  // Introduce constants for repeated attribute names (extract/rename technique).
  private static final String SIZE = "size";
  private static final String FILE_KEY = "fileKey";
  private static final String IS_DIRECTORY = "isDirectory";
  private static final String IS_REGULAR_FILE = "isRegularFile";
  private static final String IS_SYMBOLIC_LINK = "isSymbolicLink";
  private static final String IS_OTHER = "isOther";
  private static final String CREATION_TIME = "creationTime";
  private static final String LAST_ACCESS_TIME = "lastAccessTime";
  private static final String LAST_MODIFIED_TIME = "lastModifiedTime";

  private static final ImmutableSet<String> ATTRIBUTES =
          ImmutableSet.of(
                  SIZE,
                  FILE_KEY,
                  IS_DIRECTORY,
                  IS_REGULAR_FILE,
                  IS_SYMBOLIC_LINK,
                  IS_OTHER,
                  CREATION_TIME,
                  LAST_ACCESS_TIME,
                  LAST_MODIFIED_TIME);

  @Override
  public String name() {
    return "basic";
  }

  @Override
  public ImmutableSet<String> fixedAttributes() {
    return ATTRIBUTES;
  }

  @Override
  public @Nullable Object get(File file, String attribute) {
    switch (attribute) {
      case SIZE:
        return file.size();
      case FILE_KEY:
        return file.id();
      case IS_DIRECTORY:
        return file.isDirectory();
      case IS_REGULAR_FILE:
        return file.isRegularFile();
      case IS_SYMBOLIC_LINK:
        return file.isSymbolicLink();
      case IS_OTHER:
        // Introduce explaining variable and extract method to decompose conditional
        boolean isOtherResult = computeIsOther(file);
        return isOtherResult;
      case CREATION_TIME:
        return file.getCreationTime();
      case LAST_ACCESS_TIME:
        return file.getLastAccessTime();
      case LAST_MODIFIED_TIME:
        return file.getLastModifiedTime();
      default:
        return null;
    }
  }

  // Extracted helper method for "isOther" logic.
  private static boolean computeIsOther(File file) {
    return !file.isDirectory() && !file.isRegularFile() && !file.isSymbolicLink();
  }

  @Override
  public void set(File file, String view, String attribute, Object value, boolean create) {
    switch (attribute) {
      case CREATION_TIME:
        checkNotCreate(view, attribute, create);
        file.setCreationTime(checkType(view, attribute, value, FileTime.class));
        break;
      case LAST_ACCESS_TIME:
        checkNotCreate(view, attribute, create);
        file.setLastAccessTime(checkType(view, attribute, value, FileTime.class));
        break;
      case LAST_MODIFIED_TIME:
        checkNotCreate(view, attribute, create);
        file.setLastModifiedTime(checkType(view, attribute, value, FileTime.class));
        break;
      case SIZE:
      case FILE_KEY:
      case IS_DIRECTORY:
      case IS_REGULAR_FILE:
      case IS_SYMBOLIC_LINK:
      case IS_OTHER:
        throw unsettable(view, attribute, create);
      default:
        // no-op
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

  /** Implementation of {@link BasicFileAttributeView}. */
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
        file.setLastModifiedTime(lastModifiedTime);
      }

      if (lastAccessTime != null) {
        file.setLastAccessTime(lastAccessTime);
      }

      if (createTime != null) {
        file.setCreationTime(createTime);
      }
    }
  }

  /** Implementation of {@link BasicFileAttributes}. */
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
      this.lastModifiedTime = file.getLastModifiedTime();
      this.lastAccessTime = file.getLastAccessTime();
      this.creationTime = file.getCreationTime();
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
