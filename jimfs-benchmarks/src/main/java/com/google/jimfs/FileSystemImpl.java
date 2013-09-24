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

package com.google.jimfs;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enum of file system implementations to be measured.
 *
 * @author Colin Decker
 */
@SuppressWarnings("unused")
enum FileSystemImpl {

  /**
   * The system default file system.
   */
  /*DEFAULT {
    @Override
    public FileSystem getFileSystem() {
      return FileSystems.getDefault();
    }

    @Override
    public Path createTempDir(FileSystem fileSystem) throws IOException {
      return Files.createTempDirectory("FileSystemBenchmark");
    }
  },*/

  /**
   * A Unix-like JIMFS file system.
   */
  JIMFS {
    @Override
    public FileSystem getFileSystem() {
      return Jimfs.newUnixLikeFileSystem();
    }

    @Override
    public Path createTempDir(FileSystem fileSystem) throws IOException {
      return Files.createTempDirectory(fileSystem.getPath("/"), "FileSystemBenchmark");
    }
  };

  /**
   * Gets a file system instance.
   */
  public abstract FileSystem getFileSystem();

  /**
   * Creates a temp directory in the given file system and returns its path.
   */
  public abstract Path createTempDir(FileSystem fileSystem) throws IOException;
}
