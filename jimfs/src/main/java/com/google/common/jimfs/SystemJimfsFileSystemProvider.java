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
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.jimfs.Jimfs.URI_SCHEME;

import com.google.auto.service.AutoService;
import com.google.common.collect.MapMaker;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link FileSystemProvider} implementation for Jimfs that is loaded by the system as a service.
 * This implementation only serves as a cache for file system instances and does not implement
 * actual file system operations.
 *
 * <p>While this class is public, it should not be used directly. To create a new file system
 * instance, see {@link Jimfs}. For other operations, use the public APIs in {@code java.nio.file}.
 *
 * @author Colin Decker
 * @since 1.1
 */
@AutoService(FileSystemProvider.class)
public final class SystemJimfsFileSystemProvider extends FileSystemProvider {

  /**
   * Env map key that maps to the already-created {@code FileSystem} instance in
   * {@code newFileSystem}.
   */
  static final String FILE_SYSTEM_KEY = "fileSystem";

  /**
   * Cache of file systems that have been created but not closed.
   *
   * <p>This cache is static to ensure that even when this provider isn't loaded by the system class
   * loader, meaning that a new instance of it must be created each time one of the methods on
   * {@link FileSystems} or {@link Paths#get(URI)} is called, cached file system instances are still
   * available.
   *
   * <p>The cache uses weak values so that it doesn't prevent file systems that are created but not
   * closed from being garbage collected if no references to them are held elsewhere. This is a
   * compromise between ensuring that any file URI continues to work as long as the file system
   * hasn't been closed (which is technically the correct thing to do but unlikely to be something
   * that most users care about) and ensuring that users don't get unexpected leaks of large amounts
   * of memory because they're creating many file systems in tests but forgetting to close them
   * (which seems likely to happen sometimes). Users that want to ensure that a file system won't be
   * garbage collected just need to ensure they hold a reference to it somewhere for as long as they
   * need it to stick around.
   */
  private static final ConcurrentMap<URI, FileSystem> fileSystems =
      new MapMaker().weakValues().makeMap();

  /**
   * @deprecated Not intended to be called directly; this class is only for use by Java itself.
   */
  @Deprecated
  public SystemJimfsFileSystemProvider() {} // a public, no-arg constructor is required

  @Override
  public String getScheme() {
    return URI_SCHEME;
  }

  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
    checkArgument(
        uri.getScheme().equalsIgnoreCase(URI_SCHEME),
        "uri (%s) scheme must be '%s'",
        uri,
        URI_SCHEME);
    checkArgument(
        isValidFileSystemUri(uri), "uri (%s) may not have a path, query or fragment", uri);
    checkArgument(
        env.get(FILE_SYSTEM_KEY) instanceof FileSystem,
        "env map (%s) must contain key '%s' mapped to an instance of %s",
        env, FILE_SYSTEM_KEY, FileSystem.class);

    FileSystem fileSystem = (FileSystem) env.get(FILE_SYSTEM_KEY);
    if (fileSystems.putIfAbsent(uri, fileSystem) != null) {
      throw new FileSystemAlreadyExistsException(uri.toString());
    }
    return fileSystem;
  }

  @Override
  public FileSystem getFileSystem(URI uri) {
    FileSystem fileSystem = fileSystems.get(uri);
    if (fileSystem == null) {
      throw new FileSystemNotFoundException(uri.toString());
    }
    return fileSystem;
  }

  @Override
  public Path getPath(URI uri) {
    checkArgument(
        URI_SCHEME.equalsIgnoreCase(uri.getScheme()),
        "uri scheme does not match this provider: %s",
        uri);

    String path = uri.getPath();
    checkArgument(!isNullOrEmpty(path), "uri must have a path: %s", uri);

    return toPath(getFileSystem(toFileSystemUri(uri)), uri);
  }

  /**
   * Returns whether or not the given URI is valid as a base file system URI. It must not have a
   * path, query or fragment.
   */
  private static boolean isValidFileSystemUri(URI uri) {
    // would like to just check null, but fragment appears to be the empty string when not present
    return isNullOrEmpty(uri.getPath())
        && isNullOrEmpty(uri.getQuery())
        && isNullOrEmpty(uri.getFragment());
  }

  /**
   * Returns the given URI with any path, query or fragment stripped off.
   */
  private static URI toFileSystemUri(URI uri) {
    try {
      return new URI(
          uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), null, null, null);
    } catch (URISyntaxException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Invokes the {@code toPath(URI)} method on the given {@code FileSystem}.
   */
  private static Path toPath(FileSystem fileSystem, URI uri) {
    // We have to invoke this method by reflection because while the file system should be
    // an instance of JimfsFileSystem, it may be loaded by a different class loader and as
    // such appear to be a totally different class.
    try {
      Method toPath = fileSystem.getClass().getDeclaredMethod("toPath", URI.class);
      return (Path) toPath.invoke(fileSystem, uri);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("invalid file system: " + fileSystem);
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
    FileSystemProvider realProvider = path.getFileSystem().provider();
    return realProvider.newFileSystem(path, env);
  }

  /**
   * Returns a runnable that, when run, removes the file system with the given URI from this
   * provider.
   */
  @SuppressWarnings("unused") // called via reflection
  public static Runnable removeFileSystemRunnable(final URI uri) {
    return new Runnable() {
      @Override
      public void run() {
        fileSystems.remove(uri);
      }
    };
  }

  @Override
  public SeekableByteChannel newByteChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(
      Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(Path path) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(
      Path path, Class<V> type, LinkOption... options) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(
      Path path, Class<A> type, LinkOption... options) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
      throws IOException {
    throw new UnsupportedOperationException();
  }
}
