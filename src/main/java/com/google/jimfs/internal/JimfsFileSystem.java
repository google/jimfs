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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jimfs.internal.LinkHandling.NOFOLLOW_LINKS;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.jimfs.config.JimfsConfiguration;
import com.google.jimfs.internal.file.DirectoryTable;
import com.google.jimfs.internal.file.File;
import com.google.jimfs.internal.path.JimfsPath;
import com.google.jimfs.internal.path.PathService;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Colin Decker
 */
public final class JimfsFileSystem extends FileSystem {

  private final JimfsFileSystemProvider provider;
  private final URI uri;

  private final JimfsConfiguration configuration;
  private final PathService pathService;
  private final JimfsFileStore store;
  private final ImmutableSet<JimfsPath> rootDirPaths;

  private final JimfsPath workingDirPath;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private final FileTree superRootTree;
  private final FileTree workingDirTree;

  /** Set of closeables that need to be closed when this file system is closed. */
  private final Set<Closeable> openCloseables = Sets.newConcurrentHashSet();

  public JimfsFileSystem(JimfsFileSystemProvider provider, URI uri, JimfsConfiguration config) {
    this.provider = checkNotNull(provider);
    this.uri = checkNotNull(uri);
    this.configuration = checkNotNull(config);
    this.pathService = new RealJimfsPathService(this, config.getPathType());
    this.store = new JimfsFileStore("jimfs", config.getAttributeProviders());

    Set<JimfsPath> rootPaths = new HashSet<>();
    for (String root : config.getRoots()) {
      JimfsPath rootPath = pathService.parsePath(root);
      if (!rootPath.isAbsolute() && rootPath.getNameCount() == 0) {
        throw new IllegalArgumentException("Invalid root path: " + root);
      }
      rootPaths.add(rootPath);
    }
    this.rootDirPaths = ImmutableSet.copyOf(rootPaths);
    this.workingDirPath = getPath(config.getWorkingDirectory());

    if (rootDirPaths.isEmpty()) {
      this.superRootTree = null;
      this.workingDirTree = null;
    } else {
      File superRoot = store.createDirectory();
      DirectoryTable superRootTable = superRoot.content();
      for (JimfsPath path : rootDirPaths) {
        File dir = store.createDirectory();
        superRootTable.link(path.root(), dir);

        DirectoryTable dirTable = dir.content();
        dirTable.linkSelf(dir);
        dirTable.linkParent(dir);
      }


      LookupService lookupService = new LookupService(pathService);
      this.superRootTree = new FileTree(
          superRoot, pathService.emptyPath(), null, lock(), store, pathService, lookupService);

      File workingDir = createWorkingDirectory(workingDirPath);
      this.workingDirTree = new FileTree(
          workingDir, workingDirPath, superRootTree, lock(), store, pathService, lookupService);
    }
  }

  private File createWorkingDirectory(JimfsPath workingDir) {
    try {
      Files.createDirectories(workingDir);
      return superRootTree.lookup(workingDir, NOFOLLOW_LINKS)
          .requireDirectory(workingDir)
          .file();
    } catch (IOException e) {
      throw new RuntimeException("failed to create working dir", e);
    }
  }

  @Override
  public JimfsFileSystemProvider provider() {
    return provider;
  }

  /**
   * Returns the path service for this file system.
   */
  public PathService getPathService() {
    return pathService;
  }

  /**
   * Returns the configuration for this file system.
   */
  public JimfsConfiguration configuration() {
    return configuration;
  }

  @Override
  public String getSeparator() {
    return pathService.getSeparator();
  }

  @SuppressWarnings("unchecked") // safe because set is immutable
  @Override
  public ImmutableSet<Path> getRootDirectories() {
    return (ImmutableSet<Path>) (ImmutableSet) rootDirPaths;
  }

  /**
   * Returns the working directory path for this file system.
   */
  public JimfsPath getWorkingDirectory() {
    return workingDirPath;
  }

  @Override
  public ImmutableSet<FileStore> getFileStores() {
    return ImmutableSet.<FileStore>of(store);
  }

  @Override
  public ImmutableSet<String> supportedFileAttributeViews() {
    return store.supportedFileAttributeViews();
  }

  @Override
  public JimfsPath getPath(String first, String... more) {
    return pathService.parsePath(first, more);
  }

  /**
   * Gets the URI of the given path in this file system.
   */
  public URI getUri(JimfsPath path) {
    checkArgument(path.getFileSystem() == this);

    String pathString = path.toString();
    if (!pathString.startsWith("/")) {
      pathString = "/" + pathString;
    }

    return URI.create(uri.toString() + pathString);
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    return pathService.createPathMatcher(syntaxAndPattern);
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    return null;
  }

  @Override
  public WatchService newWatchService() throws IOException {
    return opened(new PollingWatchService(this));
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

  /**
   * Returns the read/write lock for this file system.
   */
  public ReadWriteLock lock() {
    return lock;
  }

  /**
   * Returns the file tree to use for the given path. If the path is absolute, the super root is
   * returned. Otherwise, the working directory is returned.
   */
  public FileTree getFileTree(JimfsPath path) {
    return path.isAbsolute() ? superRootTree : workingDirTree;
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  /**
   * Called when a closeable associated with this file system is opened. Returns the given
   * closeable.
   */
  public <C extends Closeable> C opened(C closeable) {
    openCloseables.add(closeable);
    return closeable;
  }

  /**
   * Called when an opened closeable such as a watch service is closed.
   */
  public void closed(Closeable closeable) {
    openCloseables.remove(closeable);
  }

  @Override
  public void close() throws IOException {
    Throwable thrown = null;
    for (Closeable closeable : openCloseables) {
      try {
        closeable.close();
      } catch (Throwable e) {
        if (thrown == null) {
          thrown = e;
        } else {
          thrown.addSuppressed(e);
        }
      }
    }

    Throwables.propagateIfPossible(thrown, IOException.class);
  }
}
