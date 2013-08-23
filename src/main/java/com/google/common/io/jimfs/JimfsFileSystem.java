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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.jimfs.attribute.AttributeService;
import com.google.common.io.jimfs.config.JimfsConfiguration;
import com.google.common.io.jimfs.file.FileService;
import com.google.common.io.jimfs.file.FileTree;
import com.google.common.io.jimfs.path.JimfsPath;
import com.google.common.io.jimfs.path.Name;
import com.google.common.io.jimfs.path.PathMatchers;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Abstract base implementation of {@link FileSystem} for JIMFS. Abstract to allow for a fake,
 * lightweight subclass for testing that doesn't implement the actual file system.
 *
 * @author Colin Decker
 */
public abstract class JimfsFileSystem extends FileSystem {

  private final JimfsFileSystemProvider provider;
  private final JimfsConfiguration configuration;
  private final ImmutableSet<String> supportedFileAttributeViews;

  private final ImmutableSet<Path> roots;
  private final JimfsPath workingDir;

  JimfsFileSystem(JimfsFileSystemProvider provider, JimfsConfiguration configuration) {
    this.provider = checkNotNull(provider);
    this.configuration = checkNotNull(configuration);
    this.supportedFileAttributeViews =
        configuration.getAttributeService().supportedFileAttributeViews();

    this.roots = createRootPaths(configuration.getRoots());
    this.workingDir = getPath(configuration.getWorkingDirectory());
  }

  private ImmutableSet<Path> createRootPaths(Iterable<String> roots) {
    ImmutableSet.Builder<Path> rootPathsBuilder = ImmutableSet.builder();
    for (String root : roots) {
      rootPathsBuilder.add(getPath(root));
    }
    return rootPathsBuilder.build();
  }

  @Override
  public JimfsFileSystemProvider provider() {
    return provider;
  }

  /**
   * Returns the configuration for this file system.
   */
  public JimfsConfiguration configuration() {
    return configuration;
  }

  @Override
  public String getSeparator() {
    return configuration.getSeparator();
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    return roots;
  }

  /**
   * Returns the working directory path for this file system.
   */
  public JimfsPath getWorkingDirectory() {
    return workingDir;
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return ImmutableList.of();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return supportedFileAttributeViews;
  }

  @Override
  public JimfsPath getPath(String first, String... more) {
    List<String> parts = new ArrayList<>();
    for (String s : Lists.asList(first, more)) {
      if (!s.isEmpty()) {
        parts.add(s);
      }
    }
    return configuration.parsePath(this, parts);
  }

  /**
   * Returns the {@link Name} representation of the given string for this file system.
   */
  public Name name(String name) {
    return configuration.createName(name, false);
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    return PathMatchers.getPathMatcher(syntaxAndPattern, configuration.getRecognizedSeparators());
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
  public abstract ReadWriteLock lock();

  /**
   * Returns the attribute service for the file system, which provides methods for reading and
   * setting file attributes and getting attribute views.
   */
  public abstract AttributeService getAttributeService();

  /**
   * Returns the file service for this file system.
   */
  public abstract FileService getFileService();

  /**
   * Returns the super root for this file system.
   */
  public abstract FileTree getSuperRootTree();

  /**
   * Returns the working directory tree for this file system.
   */
  public abstract FileTree getWorkingDirectoryTree();

  /**
   * Returns the file tree to use for the given path. If the path is absolute, the super root is
   * returned. Otherwise, the working directory is returned.
   */
  public abstract FileTree getFileTree(JimfsPath path);
}
