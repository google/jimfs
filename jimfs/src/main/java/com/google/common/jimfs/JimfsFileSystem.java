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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

/**
 * {@link FileSystem} implementation for Jimfs. Most behavior for the file system is implemented
 * by its {@linkplain #getDefaultView() default file system view}.
 *
 * <h3>Overview of file system design</h3>
 *
 * {@link com.google.common.jimfs.JimfsFileSystem JimfsFileSystem} instances are created by
 * {@link com.google.common.jimfs.JimfsFileSystems JimfsFileSystems} using a user-provided
 * {@link com.google.common.jimfs.Configuration Configuration}. The configuration is used to create
 * the various classes that implement the file system with the correct settings and to create the
 * file system root directories and working directory. The file system is then used to create the
 * {@code Path} objects that all file system operations use.
 *
 * <p>Once created, the primary entry points to the file system are
 * {@link com.google.common.jimfs.JimfsFileSystemProvider JimfsFileSystemProvider}, which handles
 * calls to methods in {@link java.nio.file.Files}, and
 * {@link com.google.common.jimfs.JimfsSecureDirectoryStream JimfsSecureDirectoryStream}, which
 * provides methods that are similar to those of the file system provider but which treat relative
 * paths as relative to the stream's directory rather than the file system's working directory.
 *
 * <p>The implementation of the methods on both of those classes is handled by the
 * {@link com.google.common.jimfs.FileSystemView FileSystemView} class, which acts as a view of
 * the file system with a specific working directory. The file system provider uses the file
 * system's default view, while each secure directory stream uses a view specific to that stream.
 *
 * <p>File system views make use of the file system's singleton
 * {@link com.google.common.jimfs.JimfsFileStore JimfsFileStore} which handles file creation,
 * storage and attributes. The file store delegates to several other classes to handle each of
 * these:
 *
 * <ul>
 *   <li>{@link com.google.common.jimfs.FileFactory FileFactory} handles creation of new file
 *   objects.</li>
 *   <li>{@link com.google.common.jimfs.HeapDisk HeapDisk} handles allocation of blocks to
 *   {@link RegularFile RegularFile} instances.</li>
 *   <li>{@link com.google.common.jimfs.FileTree FileTree} stores the root of the file hierarchy
 *   and handles file lookup.</li>
 *   <li>{@link com.google.common.jimfs.AttributeService AttributeService} handles file
 *   attributes, using a set of
 *   {@link com.google.common.jimfs.AttributeProvider AttributeProvider} implementations to
 *   handle each supported file attribute view.</li>
 * </ul>
 *
 * <h3>Paths</h3>
 *
 * The implementation of {@link java.nio.file.Path} for the file system is
 * {@link com.google.common.jimfs.JimfsPath JimfsPath}. Paths are created by a
 * {@link com.google.common.jimfs.PathService PathService} with help from the file system's
 * configured {@link com.google.common.jimfs.PathType PathType}.
 *
 * <p>Paths are made up of {@link com.google.common.jimfs.Name Name} objects, which also serve as
 * the file names in directories. A name has two forms:
 *
 * <ul>
 *   <li>The <b>display form</b> is used in {@code Path} for {@code toString()}. It is also used for
 *   determining the equality and sort order of {@code Path} objects for most file systems.</li>
 *   <li>The <b>canonical form</b> is used for equality of two {@code Name} objects. This affects
 *   the notion of name equality in the file system itself for file lookup. A file system may be
 *   configured to use the canonical form of the name for path equality (a Windows-like file system
 *   configuration does this, as the real Windows file system implementation uses case-insensitive
 *   equality for its path objects.</li>
 * </ul>
 *
 * <p>The canonical form of a name is created by applying a series of
 * {@linkplain PathNormalization normalizations} to the original string. These
 * normalization may be either a Unicode normalization (e.g. NFD) or case folding normalization for
 * case-insensitivity. Normalizations may also be applied to the display form of a name, but this
 * is currently only done for a Mac OS X type configuration.
 *
 * <h3>Files</h3>
 *
 * All files in the file system are an instance of {@link com.google.common.jimfs.File File}. A
 * file object contains both the file's attributes and content.
 *
 * <p>There are three types of files:
 *
 * <ul>
 *   <li>{@link Directory Directory} - contains a table linking file names
 *   to {@linkplain com.google.common.jimfs.DirectoryEntry directory entries}.
 *   <li>{@link RegularFile RegularFile} - an in-memory store for raw bytes.
 *   <li>{@link com.google.common.jimfs.SymbolicLink SymbolicLink} - contains a path.
 * </ul>
 *
 * <p>{@link com.google.common.jimfs.JimfsFileChannel JimfsFileChannel},
 * {@link com.google.common.jimfs.JimfsInputStream JimfsInputStream} and
 * {@link com.google.common.jimfs.JimfsOutputStream JimfsOutputStream} implement the standard
 * channel/stream APIs for regular files.
 *
 * <p>{@link com.google.common.jimfs.JimfsSecureDirectoryStream JimfsSecureDirectoryStream} handles
 * reading the entries of a directory. The secure directory stream additionally contains a
 * {@code FileSystemView} with its directory as the working directory, allowing for operations
 * relative to the actual directory file rather than just the path to the file. This allows the
 * operations to continue to work as expected even if the directory is moved.
 *
 * <p>A directory can be watched for changes using the {@link java.nio.file.WatchService}
 * implementation, {@link com.google.common.jimfs.PollingWatchService PollingWatchService}.
 *
 * <h3>Regular files</h3>
 *
 * {@link RegularFile RegularFile} makes use of a singleton
 * {@link com.google.common.jimfs.HeapDisk HeapDisk}. A disk is a resizable factory and cache for
 * fixed size blocks of memory. These blocks are allocated to files as needed and returned to the
 * disk when a file is deleted or truncated. When cached free blocks are available, those blocks
 * are allocated to files first. If more blocks are needed, they are created.
 *
 * <h3>Linking</h3>
 *
 * When a file is mapped to a file name in a directory table, it is <i>linked</i>. Each type of
 * file has different rules governing how it is linked.
 *
 * <ul>
 *   <li>Directory - A directory has two or more links to it. The first is the link from
 *   its parent directory to it. This link is the name of the directory. The second is the
 *   <i>self</i> link (".") which links the directory to itself. The directory may also have any
 *   number of additional <i>parent</i> links ("..") from child directories back to it.</li>
 *   <li>Regular file - A regular file has one link from its parent directory by default. However,
 *   regular files are also allowed to have any number of additional user-created hard links, from
 *   the same directory with different names and/or from other directories with any names.</li>
 *   <li>Symbolic link - A symbolic link can only have one link, from its parent directory.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 *
 * All file system operations should be safe in a multithreaded environment. The file hierarchy
 * itself is protected by a file system level read-write lock. This ensures safety of all
 * modifications to directory tables as well as atomicity of operations like file moves. Regular
 * files are each protected by a read-write lock which is obtained for each read or write operation.
 * File attributes are protected by synchronization on the file object itself.
 *
 * @author Colin Decker
 */
final class JimfsFileSystem extends FileSystem {

  private final JimfsFileSystemProvider provider;
  private final URI uri;

  private final JimfsFileStore fileStore;
  private final PathService pathService;

  private final UserPrincipalLookupService userLookupService = new UserLookupService(true);

  private final FileSystemView defaultView;

  private final WatchServiceConfiguration watchServiceConfig;

  JimfsFileSystem(
      JimfsFileSystemProvider provider,
      URI uri,
      JimfsFileStore fileStore,
      PathService pathService,
      FileSystemView defaultView,
      WatchServiceConfiguration watchServiceConfig) {
    this.provider = checkNotNull(provider);
    this.uri = checkNotNull(uri);
    this.fileStore = checkNotNull(fileStore);
    this.pathService = checkNotNull(pathService);
    this.defaultView = checkNotNull(defaultView);
    this.watchServiceConfig = checkNotNull(watchServiceConfig);
  }

  @Override
  public JimfsFileSystemProvider provider() {
    return provider;
  }

  /**
   * Returns the URI for this file system.
   */
  public URI getUri() {
    return uri;
  }

  /**
   * Returns the default view for this file system.
   */
  public FileSystemView getDefaultView() {
    return defaultView;
  }

  @Override
  public String getSeparator() {
    return pathService.getSeparator();
  }

  @SuppressWarnings("unchecked") // safe cast of immutable set
  @Override
  public ImmutableSortedSet<Path> getRootDirectories() {
    ImmutableSortedSet.Builder<JimfsPath> builder = ImmutableSortedSet.orderedBy(pathService);
    for (Name name : fileStore.getRootDirectoryNames()) {
      builder.add(pathService.createRoot(name));
    }
    return (ImmutableSortedSet<Path>) (ImmutableSortedSet<?>) builder.build();
  }

  /**
   * Returns the working directory path for this file system.
   */
  public JimfsPath getWorkingDirectory() {
    return defaultView.getWorkingDirectoryPath();
  }

  /**
   * Returns the path service for this file system.
   */
  @VisibleForTesting
  PathService getPathService() {
    return pathService;
  }

  /**
   * Returns the file store for this file system.
   */
  public JimfsFileStore getFileStore() {
    return fileStore;
  }

  @Override
  public ImmutableSet<FileStore> getFileStores() {
    fileStore.state().checkOpen();
    return ImmutableSet.<FileStore>of(fileStore);
  }

  @Override
  public ImmutableSet<String> supportedFileAttributeViews() {
    return fileStore.supportedFileAttributeViews();
  }

  @Override
  public JimfsPath getPath(String first, String... more) {
    fileStore.state().checkOpen();
    return pathService.parsePath(first, more);
  }

  /**
   * Gets the URI of the given path in this file system.
   */
  public URI toUri(JimfsPath path) {
    fileStore.state().checkOpen();
    return pathService.toUri(uri, path.toAbsolutePath());
  }

  /**
   * Converts the given URI into a path in this file system.
   */
  public JimfsPath toPath(URI uri) {
    fileStore.state().checkOpen();
    return pathService.fromUri(uri);
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    fileStore.state().checkOpen();
    return pathService.createPathMatcher(syntaxAndPattern);
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    fileStore.state().checkOpen();
    return userLookupService;
  }

  @Override
  public WatchService newWatchService() throws IOException {
    return watchServiceConfig.newWatchService(defaultView, pathService);
  }

  @Nullable private ExecutorService defaultThreadPool;

  /**
   * Returns a default thread pool to use for asynchronous file channels when users do not provide
   * an executor themselves. (This is required by the spec of newAsynchronousFileChannel in
   * FileSystemProvider.)
   */
  public synchronized ExecutorService getDefaultThreadPool() {
    if (defaultThreadPool == null) {
      defaultThreadPool =
          Executors.newCachedThreadPool(
              new ThreadFactoryBuilder()
                  .setDaemon(true)
                  .setNameFormat("JimfsFileSystem-" + uri.getHost() + "-defaultThreadPool-%s")
                  .build());

      // ensure thread pool is closed when file system is closed
      fileStore
          .state()
          .register(
              new Closeable() {
                @Override
                public void close() {
                  defaultThreadPool.shutdown();
                }
              });
    }
    return defaultThreadPool;
  }

  /**
   * Returns {@code false}; currently, cannot create a read-only file system.
   *
   * @return {@code false}, always
   */
  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public boolean isOpen() {
    return fileStore.state().isOpen();
  }

  @Override
  public void close() throws IOException {
    fileStore.state().close();
  }
}
