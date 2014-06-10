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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for creating new files and copying files. One piece of the file store implementation.
 *
 * @author Colin Decker
 */
final class FileFactory {

  private final AtomicInteger idGenerator = new AtomicInteger();

  private final HeapDisk disk;

  /**
   * Creates a new file factory using the given disk for regular files.
   */
  public FileFactory(HeapDisk disk) {
    this.disk = checkNotNull(disk);
  }

  private int nextFileId() {
    return idGenerator.getAndIncrement();
  }

  /**
   * Creates a new directory.
   */
  public Directory createDirectory() {
    return Directory.create(nextFileId());
  }

  /**
   * Creates a new root directory with the given name.
   */
  public Directory createRootDirectory(Name name) {
    return Directory.createRoot(nextFileId(), name);
  }

  /**
   * Creates a new regular file.
   */
  @VisibleForTesting
  RegularFile createRegularFile() {
    return RegularFile.create(nextFileId(), disk);
  }

  /**
   * Creates a new symbolic link referencing the given target path.
   */
  @VisibleForTesting
  SymbolicLink createSymbolicLink(JimfsPath target) {
    return SymbolicLink.create(nextFileId(), target);
  }

  /**
   * Creates and returns a copy of the given file.
   */
  public File copyWithoutContent(File file) throws IOException {
    return file.copyWithoutContent(nextFileId());
  }

  // suppliers to act as file creation callbacks

  private final Supplier<Directory> directorySupplier = new DirectorySupplier();

  private final Supplier<RegularFile> regularFileSupplier = new RegularFileSupplier();

  /**
   * Returns a supplier that creates directories.
   */
  public Supplier<Directory> directoryCreator() {
    return directorySupplier;
  }

  /**
   * Returns a supplier that creates regular files.
   */
  public Supplier<RegularFile> regularFileCreator() {
    return regularFileSupplier;
  }

  /**
   * Returns a supplier that creates a symbolic links to the given path.
   */
  public Supplier<SymbolicLink> symbolicLinkCreator(JimfsPath target) {
    return new SymbolicLinkSupplier(target);
  }

  private final class DirectorySupplier implements Supplier<Directory> {
    @Override
    public Directory get() {
      return createDirectory();
    }
  }

  private final class RegularFileSupplier implements Supplier<RegularFile> {
    @Override
    public RegularFile get() {
      return createRegularFile();
    }
  }

  private final class SymbolicLinkSupplier implements Supplier<SymbolicLink> {

    private final JimfsPath target;

    protected SymbolicLinkSupplier(JimfsPath target) {
      this.target = checkNotNull(target);
    }

    @Override
    public SymbolicLink get() {
      return createSymbolicLink(target);
    }
  }
}
