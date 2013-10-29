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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
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
 * {@link FileSystem} implementation for JimFS. Most behavior for the file system is implemented
 * by its {@linkplain #getDefaultView() default file system view}.
 *
 * @author Colin Decker
 */
final class JimfsFileSystem extends FileSystem {

  private final JimfsFileSystemProvider provider;
  private final URI uri;

  private final JimfsFileStore fileStore;
  private final PathService pathService;

  private final ResourceManager resourceManager = new ResourceManager();
  private final UserPrincipalLookupService userLookupService = new UserLookupService(true);

  private final FileSystemView defaultView;

  JimfsFileSystem(JimfsFileSystemProvider provider, URI uri, JimfsFileStore fileStore,
      PathService pathService, FileSystemView defaultView) {
    this.provider = checkNotNull(provider);
    this.uri = checkNotNull(uri);
    this.fileStore = checkNotNull(fileStore);
    this.pathService = checkNotNull(pathService);
    this.defaultView = checkNotNull(defaultView);
  }

  @Override
  public JimfsFileSystemProvider provider() {
    return provider;
  }

  /**
   * Returns the URI for this file system.
   */
  public URI uri() {
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

  @Override
  public ImmutableSet<Path> getRootDirectories() {
    ImmutableSet.Builder<Path> builder = ImmutableSet.builder();
    for (Name name : fileStore.getRootDirectoryNames()) {
      builder.add(pathService.createRoot(name));
    }
    return builder.build();
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
    return ImmutableSet.<FileStore>of(fileStore);
  }

  @Override
  public ImmutableSet<String> supportedFileAttributeViews() {
    return fileStore.supportedFileAttributeViews();
  }

  @Override
  public JimfsPath getPath(String first, String... more) {
    return pathService.parsePath(first, more);
  }

  /**
   * Gets the URI of the given path in this file system.
   */
  public URI toUri(JimfsPath path) {
    return pathService.toUri(uri, path.toAbsolutePath());
  }

  /**
   * Converts the given URI into a path in this file system.
   */
  public JimfsPath toPath(URI uri) {
    return pathService.fromUri(uri);
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    return pathService.createPathMatcher(syntaxAndPattern);
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    return userLookupService;
  }

  @Override
  public synchronized WatchService newWatchService() throws IOException {
    return new PollingWatchService(defaultView, pathService, resourceManager);
  }

  @Nullable
  private ExecutorService defaultThreadPool;

  /**
   * Returns a default thread pool to use for asynchronous file channels when users do not provide
   * an executor themselves.
   */
  public synchronized ExecutorService getDefaultThreadPool() {
    if (defaultThreadPool == null) {
      defaultThreadPool = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
          .setDaemon(true)
          .setNameFormat("JimfsFileSystem-" + uri.getHost() + "-defaultThreadPool-%s")
          .build());

      // ensure thread pool is closed when file system is closed
      resourceManager.register(new Closeable() {
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

  private boolean open = true;

  @Override
  public synchronized boolean isOpen() {
    return open;
  }

  @Override
  public synchronized void close() throws IOException {
    if (open) {
      open = false;
      try {
        resourceManager.close();
      } finally {
        provider.remove(this);
      }
    }
  }
}
