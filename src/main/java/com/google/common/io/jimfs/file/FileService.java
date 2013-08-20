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

package com.google.common.io.jimfs.file;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.CREATE;

import com.google.common.io.jimfs.attribute.AttributeService;
import com.google.common.io.jimfs.bytestore.ArrayByteStore;
import com.google.common.io.jimfs.path.JimfsPath;

import java.nio.file.attribute.FileAttribute;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for creating new files and copies of files.
 *
 * @author Colin Decker
 */
public final class FileService {

  private final AtomicLong idGenerator = new AtomicLong();
  private final AttributeService attributeService;

  public FileService(AttributeService attributeService) {
    this.attributeService = checkNotNull(attributeService);
  }

  private long nextFileId() {
    return idGenerator.getAndIncrement();
  }

  private File createFile(long id, FileContent content, FileAttribute<?>... attrs) {
    File file = new File(id, content);
    attributeService.setInitialAttributes(file);
    for (FileAttribute<?> attr : attrs) {
      attributeService.setAttribute(file, attr.name(), attr.value(), CREATE);
    }
    return file;
  }

  /**
   * Creates a new directory and stores it. Returns the key of the new file.
   */
  public File createDirectory(FileAttribute<?>... attrs) {
    return createFile(nextFileId(), new DirectoryTable(), attrs);
  }

  /**
   * Creates a new regular file and stores it. Returns the key of the new file.
   */
  public File createRegularFile(FileAttribute<?>... attrs) {
    return createFile(nextFileId(), new ArrayByteStore(), attrs);
  }

  /**
   * Creates a new symbolic link referencing the given target path and stores it. Returns the key
   * of the new file.
   */
  public File createSymbolicLink(JimfsPath target, FileAttribute<?>... attrs) {
    return createFile(nextFileId(), target, attrs);
  }

  /**
   * Creates copies of the given file metadata and content and stores them. Returns the key of the
   * new file.
   */
  public File copy(File file) {
    return createFile(nextFileId(), file.content().copy());
  }

  /**
   * Returns a {@link Callback} that creates a directory.
   */
  public Callback directoryCallback(final FileAttribute<?>... attrs) {
    return new Callback() {
      @Override
      public File createFile() {
        return createDirectory(attrs);
      }
    };
  }

  /**
   * Returns a {@link Callback} that creates a regular file.
   */
  public Callback regularFileCallback(final FileAttribute<?>... attrs) {
    return new Callback() {
      @Override
      public File createFile() {
        return createRegularFile(attrs);
      }
    };
  }

  /**
   * Returns a {@link Callback} that creates a symbolic link to the given path.
   */
  public Callback symbolicLinkCallback(final JimfsPath path, final FileAttribute<?>... attrs) {
    return new Callback() {
      @Override
      public File createFile() {
        return createSymbolicLink(path, attrs);
      }
    };
  }

  /**
   * Callback for creating new files.
   *
   * @author Colin Decker
   */
  public static interface Callback {

    /**
     * Creates and returns a new file.
     */
    File createFile();
  }
}
