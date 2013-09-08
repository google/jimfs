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

import com.google.common.collect.ImmutableMap;
import com.google.jimfs.config.JimfsConfiguration;
import com.google.jimfs.config.UnixConfiguration;
import com.google.jimfs.config.WindowsConfiguration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.UUID;

/**
 * Static factory methods for JIMFS file systems.
 *
 * @author Colin Decker
 */
public final class Jimfs {

  private Jimfs() {}

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
    return newFileSystem(new UnixConfiguration());
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
    return newFileSystem(new WindowsConfiguration());
  }

  private static FileSystem newFileSystem(JimfsConfiguration config) {
    ImmutableMap<String, ?> env = ImmutableMap.of("config", config);
    try {
      // Need to use Jimfs.class.getClassLoader() to ensure the class that loaded the jar is used
      // to locate the FileSystemProvider
      return FileSystems.newFileSystem(newRandomUri(), env, Jimfs.class.getClassLoader());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static URI newRandomUri() {
    return URI.create("jimfs://" + UUID.randomUUID());
  }
}
