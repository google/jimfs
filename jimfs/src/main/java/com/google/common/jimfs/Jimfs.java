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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.jimfs.SystemJimfsFileSystemProvider.FILE_SYSTEM_KEY;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.UUID;

/**
 * Static factory methods for creating new Jimfs file systems. File systems may either be created
 * with a basic configuration matching the current operating system or by providing a specific
 * {@link Configuration}. Basic {@linkplain Configuration#unix() UNIX},
 * {@linkplain Configuration#osX() Mac OS X} and {@linkplain Configuration#windows() Windows}
 * configurations are provided.
 *
 * <p>Examples:
 *
 * <pre>
 *   // A file system with a configuration similar to the current OS
 *   FileSystem fileSystem = Jimfs.newFileSystem();
 *
 *   // A file system with paths and behavior generally matching that of Windows
 *   FileSystem windows = Jimfs.newFileSystem(Configuration.windows());  </pre>
 *
 * <p>Additionally, various behavior of the file system can be customized by creating a custom
 * {@link Configuration}. A modified version of one of the existing default configurations can be
 * created using {@link Configuration#toBuilder()} or a new configuration can be created from
 * scratch with {@link Configuration#builder(PathType)}. See {@link Configuration.Builder} for what
 * can be configured.
 *
 * <p>Examples:
 *
 * <pre>
 *   // Modify the default UNIX configuration
 *   FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix()
 *       .toBuilder()
 *       .setAttributeViews("basic", "owner", "posix", "unix")
 *       .setWorkingDirectory("/home/user")
 *       .setBlockSize(4096)
 *       .build());
 *
 *   // Create a custom configuration
 *   Configuration config = Configuration.builder(PathType.windows())
 *       .setRoots("C:\\", "D:\\", "E:\\")
 *       // ...
 *       .build();  </pre>
 *
 * @author Colin Decker
 */
public final class Jimfs {

  /**
   * The URI scheme for the Jimfs file system ("jimfs").
   */
  public static final String URI_SCHEME = "jimfs";

  private Jimfs() {}

  /**
   * Creates a new in-memory file system with a
   * {@linkplain Configuration#forCurrentPlatform() default configuration} appropriate to the
   * current operating system.
   *
   * <p>More specifically, if the operating system is Windows, {@link Configuration#windows()} is
   * used; if the operating system is Mac OS X, {@link Configuration#osX()} is used; otherwise,
   * {@link Configuration#unix()} is used.
   */
  public static FileSystem newFileSystem() {
    return newFileSystem(newRandomFileSystemName());
  }

  /**
   * Creates a new in-memory file system with a
   * {@linkplain Configuration#forCurrentPlatform() default configuration} appropriate to the
   * current operating system.
   *
   * <p>More specifically, if the operating system is Windows, {@link Configuration#windows()} is
   * used; if the operating system is Mac OS X, {@link Configuration#osX()} is used; otherwise,
   * {@link Configuration#unix()} is used.
   *
   * <p>The returned file system uses the given name as the host part of its URI and the URIs of
   * paths in the file system. For example, given the name {@code my-file-system}, the file
   * system's URI will be {@code jimfs://my-file-system} and the URI of the path {@code /foo/bar}
   * will be {@code jimfs://my-file-system/foo/bar}.
   */
  public static FileSystem newFileSystem(String name) {
    return newFileSystem(name, Configuration.forCurrentPlatform());
  }

  /**
   * Creates a new in-memory file system with the given configuration.
   */
  public static FileSystem newFileSystem(Configuration configuration) {
    return newFileSystem(newRandomFileSystemName(), configuration);
  }

  /**
   * Creates a new in-memory file system with the given configuration.
   *
   * <p>The returned file system uses the given name as the host part of its URI and the URIs of
   * paths in the file system. For example, given the name {@code my-file-system}, the file
   * system's URI will be {@code jimfs://my-file-system} and the URI of the path {@code /foo/bar}
   * will be {@code jimfs://my-file-system/foo/bar}.
   */
  public static FileSystem newFileSystem(String name, Configuration configuration) {
    try {
      URI uri = new URI(URI_SCHEME, name, null, null);
      return newFileSystem(uri, configuration);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @VisibleForTesting
  static FileSystem newFileSystem(URI uri, Configuration config) {
    checkArgument(
        URI_SCHEME.equals(uri.getScheme()), "uri (%s) must have scheme %s", uri, URI_SCHEME);

    try {
      // Create the FileSystem. It uses JimfsFileSystemProvider as its provider, as that is
      // the provider that actually implements the operations needed for Files methods to work.
      JimfsFileSystem fileSystem =
          JimfsFileSystems.newFileSystem(JimfsFileSystemProvider.instance(), uri, config);

      // Now, call FileSystems.newFileSystem, passing it the FileSystem we just created. This
      // allows the system-loaded SystemJimfsFileSystemProvider instance to cache the FileSystem
      // so that methods like Paths.get(URI) work.
      // We do it in this awkward way to avoid issues when the classes in the API (this class
      // and Configuration, for example) are loaded by a different classloader than the one that
      // loads SystemJimfsFileSystemProvider using ServiceLoader. See
      // https://github.com/google/jimfs/issues/18 for gory details.
      ImmutableMap<String, ?> env = ImmutableMap.of(FILE_SYSTEM_KEY, fileSystem);
      FileSystems.newFileSystem(uri, env, SystemJimfsFileSystemProvider.class.getClassLoader());

      return fileSystem;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static String newRandomFileSystemName() {
    return UUID.randomUUID().toString();
  }
}
