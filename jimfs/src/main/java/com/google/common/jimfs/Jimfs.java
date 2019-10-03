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
import java.nio.file.ProviderNotFoundException;
import java.nio.file.spi.FileSystemProvider;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Static factory methods for creating new Jimfs file systems. File systems may either be created
 * with a basic configuration matching the current operating system or by providing a specific
 * {@link Configuration}. Basic {@linkplain Configuration#unix() UNIX}, {@linkplain
 * Configuration#osX() Mac OS X} and {@linkplain Configuration#windows() Windows} configurations are
 * provided.
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

  /** The URI scheme for the Jimfs file system ("jimfs"). */
  public static final String URI_SCHEME = "jimfs";

  private static final Logger LOGGER = Logger.getLogger(Jimfs.class.getName());

  private Jimfs() {}

  /**
   * Creates a new in-memory file system with a {@linkplain Configuration#forCurrentPlatform()
   * default configuration} appropriate to the current operating system.
   *
   * <p>More specifically, if the operating system is Windows, {@link Configuration#windows()} is
   * used; if the operating system is Mac OS X, {@link Configuration#osX()} is used; otherwise,
   * {@link Configuration#unix()} is used.
   */
  public static FileSystem newFileSystem() {
    return newFileSystem(newRandomFileSystemName());
  }

  /**
   * Creates a new in-memory file system with a {@linkplain Configuration#forCurrentPlatform()
   * default configuration} appropriate to the current operating system.
   *
   * <p>More specifically, if the operating system is Windows, {@link Configuration#windows()} is
   * used; if the operating system is Mac OS X, {@link Configuration#osX()} is used; otherwise,
   * {@link Configuration#unix()} is used.
   *
   * <p>The returned file system uses the given name as the host part of its URI and the URIs of
   * paths in the file system. For example, given the name {@code my-file-system}, the file system's
   * URI will be {@code jimfs://my-file-system} and the URI of the path {@code /foo/bar} will be
   * {@code jimfs://my-file-system/foo/bar}.
   */
  public static FileSystem newFileSystem(String name) {
    return newFileSystem(name, Configuration.forCurrentPlatform());
  }

  /** Creates a new in-memory file system with the given configuration. */
  public static FileSystem newFileSystem(Configuration configuration) {
    return newFileSystem(newRandomFileSystemName(), configuration);
  }

  /**
   * Creates a new in-memory file system with the given configuration.
   *
   * <p>The returned file system uses the given name as the host part of its URI and the URIs of
   * paths in the file system. For example, given the name {@code my-file-system}, the file system's
   * URI will be {@code jimfs://my-file-system} and the URI of the path {@code /foo/bar} will be
   * {@code jimfs://my-file-system/foo/bar}.
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

      /*
       * Now, call FileSystems.newFileSystem, passing it the FileSystem we just created. This
       * allows the system-loaded SystemJimfsFileSystemProvider instance to cache the FileSystem
       * so that methods like Paths.get(URI) work.
       * We do it in this awkward way to avoid issues when the classes in the API (this class
       * and Configuration, for example) are loaded by a different classloader than the one that
       * loads SystemJimfsFileSystemProvider using ServiceLoader. See
       * https://github.com/google/jimfs/issues/18 for gory details.
       */
      try {
        ImmutableMap<String, ?> env = ImmutableMap.of(FILE_SYSTEM_KEY, fileSystem);
        FileSystems.newFileSystem(uri, env, SystemJimfsFileSystemProvider.class.getClassLoader());
      } catch (ProviderNotFoundException | ServiceConfigurationError ignore) {
        // See the similar catch block below for why we ignore this.
        // We log there rather than here so that there's only typically one such message per VM.
      }

      return fileSystem;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * The system-loaded instance of {@code SystemJimfsFileSystemProvider}, or {@code null} if it
   * could not be found or loaded.
   */
  @NullableDecl static final FileSystemProvider systemProvider = getSystemJimfsProvider();

  /**
   * Returns the system-loaded instance of {@code SystemJimfsFileSystemProvider} or {@code null} if
   * it could not be found or loaded.
   *
   * <p>Like {@link FileSystems#newFileSystem(URI, Map, ClassLoader)}, this method first looks in
   * the list of {@linkplain FileSystemProvider#installedProviders() installed providers} and if not
   * found there, attempts to load it from the {@code ClassLoader} with {@link ServiceLoader}.
   *
   * <p>The idea is that this method should return an instance of the same class (i.e. loaded by the
   * same class loader) as the class whose static cache a {@code JimfsFileSystem} instance will be
   * placed in when {@code FileSystems.newFileSystem} is called in {@code Jimfs.newFileSystem}.
   */
  @NullableDecl
  private static FileSystemProvider getSystemJimfsProvider() {
    try {
      for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
        if (provider.getScheme().equals(URI_SCHEME)) {
          return provider;
        }
      }

      /*
       * Jimfs.newFileSystem passes SystemJimfsFileSystemProvider.class.getClassLoader() to
       * FileSystems.newFileSystem so that it will fall back to loading from that classloader if
       * the provider isn't found in the installed providers. So do the same fallback here to ensure
       * that we can remove file systems from the static cache on SystemJimfsFileSystemProvider if
       * it gets loaded that way.
       */
      ServiceLoader<FileSystemProvider> loader =
          ServiceLoader.load(
              FileSystemProvider.class, SystemJimfsFileSystemProvider.class.getClassLoader());
      for (FileSystemProvider provider : loader) {
        if (provider.getScheme().equals(URI_SCHEME)) {
          return provider;
        }
      }
    } catch (ProviderNotFoundException | ServiceConfigurationError e) {
      /*
       * This can apparently (https://github.com/google/jimfs/issues/31) occur in an environment
       * where services are not loaded from META-INF/services, such as JBoss/Wildfly. In this
       * case, FileSystems.newFileSystem will most likely fail in the same way when called from
       * Jimfs.newFileSystem above, and there will be no way to make URI-based methods like
       * Paths.get(URI) work. Rather than making the user completly unable to use Jimfs, just
       * log this exception and continue.
       *
       * Note: Catching both ProviderNotFoundException, which would occur if no provider matching
       * the "jimfs" URI scheme is found, and ServiceConfigurationError, which can occur if the
       * ServiceLoader finds the META-INF/services entry for Jimfs (or some other
       * FileSystemProvider!) but is then unable to load that class.
       */
      LOGGER.log(
          Level.INFO,
          "An exception occurred when attempting to find the system-loaded FileSystemProvider "
              + "for Jimfs. This likely means that your environment does not support loading "
              + "services via ServiceLoader or is not configured correctly. This does not prevent "
              + "using Jimfs, but it will mean that methods that look up via URI such as "
              + "Paths.get(URI) cannot work.",
          e);
    }

    return null;
  }

  private static String newRandomFileSystemName() {
    return UUID.randomUUID().toString();
  }
}
