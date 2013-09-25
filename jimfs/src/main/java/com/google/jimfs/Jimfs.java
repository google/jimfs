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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.jimfs.path.PathType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

/**
 * Static factory methods for JIMFS file systems.
 *
 * @author Colin Decker
 */
public final class Jimfs {

  /**
   * The URI scheme for the JIMFS file system ("jimfs").
   */
  public static final String URI_SCHEME = "jimfs";

  /**
   * The key used for mapping to the {@link FileSystemConfiguration} in the env map when creating a
   * new file system instance.
   */
  public static final String CONFIG_KEY = "config";

  private Jimfs() {}

  /**
   * Returns a new in-memory file system with semantics similar to UNIX.
   *
   * <p>The returned file system has a single root, "/", and uses "/" as a separator. It supports
   * symbolic and hard links. File lookup is case-sensitive. Only the "basic" file attribute view
   * is supported. The working directory for the file system is "/work".
   *
   * <p>For more advanced configuration, including changing the working directory, supported
   * attribute views or type of storage to use or for setting the host name to be used in the file
   * system's URI, use {@link #newUnixLikeConfiguration()}.
   */
  public static FileSystem newUnixLikeFileSystem() {
    return newUnixLikeConfiguration().createFileSystem();
  }

  /**
   * Returns a new {@link FileSystemConfiguration} instance with defaults for a UNIX-like file
   * system. If no changes are made to the configuration, the file system it creates will be
   * identical to that created by {@link #newUnixLikeFileSystem()}.
   *
   * <p>Example usage:
   *
   * <pre>
   *   // the returned file system has URI "jimfs://unix" and supports
   *   // the "basic", "owner", "posix" and "unix" attribute views
   *   FileSystem fs = Jimfs.newUnixLikeConfiguration()
   *       .name("unix")
   *       .workingDirectory("/home/user")
   *       .attributes(AttributeConfiguration.unix())
   *       .createFileSystem(); </pre>
   */
  public static FileSystemConfiguration newUnixLikeConfiguration() {
    return newFileSystemConfiguration(PathType.unix())
        .addRoots("/")
        .workingDirectory("/work");
  }

  /**
   * Returns a new in-memory file system with semantics similar to Windows.
   *
   * <p>The returned file system has a single root, "C:\", and uses "\" as a separator. It also
   * recognizes "/" as a separator when parsing paths. It supports symbolic and hard links. File
   * lookup is not case-sensitive. Only the "basic" file attribute view is supported. The working
   * directory for the file system is "C:\work".
   *
   * <p>For more advanced configuration, including changing the working directory, supported
   * attribute views or type of storage to use or for setting the host name to be used in the file
   * system's URI, use {@link #newWindowsLikeConfiguration()}.
   */
  public static FileSystem newWindowsLikeFileSystem() {
    return newWindowsLikeConfiguration().createFileSystem();
  }

  /**
   * Returns a new {@link FileSystemConfiguration} instance with defaults for a Windows-like file
   * system. If no changes are made to the configuration, the file system it creates will be
   * identical to that created by {@link #newWindowsLikeFileSystem()}.
   *
   * <p>Example usage:
   *
   * <pre>
   *   // the returned file system has URI "jimfs://win", has root directories
   *   // "C:\", "E:\" and "F:\" and supports the "basic", "owner", "dos",
   *   // "acl and "user" attribute views
   *   FileSystem fs = Jimfs.newWindowsLikeConfiguration()
   *       .name("win")
   *       .addRoots("E:\\", "F:\\")
   *       .workingDirectory("C:\\Users\\user")
   *       .attributes(AttributeConfiguration.windows())
   *       .createFileSystem(); </pre>
   */
  public static FileSystemConfiguration newWindowsLikeConfiguration() {
    return newFileSystemConfiguration(PathType.windows())
        .addRoots("C:\\")
        .workingDirectory("C:\\work");
  }

  /**
   * Returns a new {@link FileSystemConfiguration} instance using the given path type. At least one
   * root must be added to the configuration before creating a file system with it.
   */
  public static FileSystemConfiguration newFileSystemConfiguration(PathType pathType) {
    return new FileSystemConfiguration(pathType);
  }

  @VisibleForTesting
  static FileSystem newFileSystem(URI uri, FileSystemConfiguration config) {
    checkArgument(URI_SCHEME.equals(uri.getScheme()),
        "uri (%s) must have scheme %s", uri, URI_SCHEME);

    ImmutableMap<String, ?> env = ImmutableMap.of(CONFIG_KEY, config);
    try {
      // Need to use Jimfs.class.getClassLoader() to ensure the class that loaded the jar is used
      // to locate the FileSystemProvider
      return FileSystems.newFileSystem(uri, env, Jimfs.class.getClassLoader());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
