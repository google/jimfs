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

package com.google.common.io.jimfs;

import com.google.common.io.jimfs.config.UnixConfiguration;
import com.google.common.io.jimfs.config.WindowsConfiguration;

import java.nio.file.FileSystem;

/**
 * Static factory methods for JIMFS file systems.
 *
 * @author Colin Decker
 */
public final class Jimfs {

  private Jimfs() {}

  private static final JimfsFileSystemProvider PROVIDER = new JimfsFileSystemProvider();

  /**
   * Returns a new in-memory file system with semantics similar to UNIX.
   *
   * <p>The returned file system has a single root, "/", and uses "/" as a separator. It supports
   * symbolic and hard links. File lookup is case-sensitive. The supported file attribute views
   * are "basic", "owner", "posix" and "unix".
   *
   * <p>The working directory for the file system, which exists when it is created, is "/work".
   */
  public static FileSystem newUnixLikeFileSystem() {
    return new JimfsFileSystem(PROVIDER, new UnixConfiguration());
  }

  /**
   * Returns a new in-memory file system with semantics similar to Windows.
   *
   * <p>The returned file system has a single root, "C:\", and uses "\" as a separator. It also
   * recognizes "/" as a separator when parsing paths. It supports symbolic and hard links. File
   * lookup is not case-sensitive. The supported file attribute views are "basic", "owner", "dos",
   * "acl" and "user".
   *
   * <p>The working directory for the file system, which exists when it is created, is "C:\work".
   */
  public static FileSystem newWindowsLikeFileSystem() {
    return new JimfsFileSystem(PROVIDER, new WindowsConfiguration());
  }
}
