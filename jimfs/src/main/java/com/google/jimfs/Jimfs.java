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

  /**
   * The URI scheme for the JIMFS file system ("jimfs").
   */
  public static final String URI_SCHEME = "jimfs";

  /**
   * The key used for mapping to the {@link Configuration} in the {@code env} map when creating a
   * new file system instance using {@code FileSystems.newFileSystem()}.
   */
  public static final String CONFIG_KEY = "config";

  private Jimfs() {}

  /**
   * Creates a new in-memory file system with the given configuration.
   */
  public static FileSystem newFileSystem(Configuration configuration) {
    return newFileSystem(newRandomFileSystemName(), configuration);
  }

  /**
   * Creates a new in-memory file system with the given configuration. The returned file system
   * uses the given name as the host part of its URI ("jimfs://[name]").
   */
  public static FileSystem newFileSystem(String name, Configuration configuration) {
    return newFileSystem(URI.create("jimfs://" + name), configuration);
  }

  @VisibleForTesting
  static FileSystem newFileSystem(URI uri, Configuration config) {
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

  private static String newRandomFileSystemName() {
    return UUID.randomUUID().toString();
  }
}
