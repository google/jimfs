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

package com.google.jimfs.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Supplier;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for creating new files and copying files. One piece of the file store implementation.
 *
 * @author Colin Decker
 */
final class FileFactory {

  private final AtomicInteger idGenerator = new AtomicInteger();

  private final MemoryDisk disk;

  /**
   * Creates a new file factory using the given disk for regular files.
   */
  public FileFactory(MemoryDisk disk) {
    this.disk = checkNotNull(disk);
  }

  private int nextFileId() {
    return idGenerator.getAndIncrement();
  }

  /**
   * Creates a new directory and stores it. Returns the key of the new file.
   */
  public File createDirectory() {
    return new File(nextFileId(), new DirectoryTable());
  }

  /**
   * Creates a new regular file and stores it. Returns the key of the new file.
   */
  public File createRegularFile() {
    return new File(nextFileId(), disk.createByteStore());
  }

  /**
   * Creates a new symbolic link referencing the given target path and stores it. Returns the key of
   * the new file.
   */
  public File createSymbolicLink(JimfsPath target) {
    return new File(nextFileId(), target);
  }

  /**
   * Creates and returns a copy of the given file.
   */
  public File copy(File file) {
    return new File(nextFileId(), file.content().copy());
  }

  // suppliers to act as file creation callbacks

  /**
   * Directory supplier instance.
   */
  private final Supplier<File> directorySupplier = new DirectorySupplier();

  /**
   * Regular file supplier instance.
   */
  private final Supplier<File> regularFileSupplier = new RegularFileSupplier();

  /**
   * Returns a supplier that creates directories and sets the given attributes.
   */
  public Supplier<File> directorySupplier() {
    return directorySupplier;
  }

  /**
   * Returns a supplier that creates a regular files and sets the given attributes.
   */
  public Supplier<File> regularFileSupplier() {
    return regularFileSupplier;
  }

  /**
   * Returns a supplier that creates a symbolic links to the given path and sets the given
   * attributes.
   */
  public Supplier<File> symbolicLinkSupplier(JimfsPath target) {
    return new SymbolicLinkSupplier(target);
  }

  private final class DirectorySupplier implements Supplier<File> {

    @Override
    public File get() {
      return createDirectory();
    }
  }

  private final class RegularFileSupplier implements Supplier<File> {

    @Override
    public File get() {
      return createRegularFile();
    }
  }

  private final class SymbolicLinkSupplier implements Supplier<File> {

    private final JimfsPath target;

    protected SymbolicLinkSupplier(JimfsPath target) {
      this.target = checkNotNull(target);
    }

    @Override
    public File get() {
      return createSymbolicLink(target);
    }
  }
}
