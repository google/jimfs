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

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.JimfsConfiguration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;

/**
 * @author Colin Decker
 */
final class JimfsFileSystem extends FileSystem {

  private final JimfsFileSystemProvider provider;
  private final JimfsConfiguration configuration;
  private final URI uri;

  private final FileSystemService fileSystemService;
  private final PathService pathService;



  JimfsFileSystem(JimfsFileSystemProvider provider, JimfsConfiguration config, URI uri,
      FileSystemService fileSystemService) {
    this.provider = checkNotNull(provider);
    this.uri = checkNotNull(uri);
    this.configuration = checkNotNull(config);
    this.fileSystemService = checkNotNull(fileSystemService);
    this.pathService = fileSystemService.getPathService();
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

  /**
   * Returns the file system service.
   */
  public FileSystemService getFileSystemService() {
    return fileSystemService;
  }

  @Override
  public String getSeparator() {
    return pathService.getSeparator();
  }

  @Override
  public ImmutableSet<Path> getRootDirectories() {
    ImmutableSet.Builder<Path> builder = ImmutableSet.builder();
    DirectoryTable superRootTable = fileSystemService.getSuperRoot().content();
    for (Name name : superRootTable.snapshot()) {
      builder.add(pathService.createRoot(name));
    }
    return builder.build();
  }

  /**
   * Returns the working directory path for this file system.
   */
  public JimfsPath getWorkingDirectory() {
    return fileSystemService.getWorkingDirectoryPath();
  }

  @Override
  public ImmutableSet<FileStore> getFileStores() {
    return ImmutableSet.<FileStore>of(fileSystemService.getFileStore());
  }

  @Override
  public ImmutableSet<String> supportedFileAttributeViews() {
    return fileSystemService.getFileStore().supportedFileAttributeViews();
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
    return new PollingWatchService(fileSystemService);
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
    return true;
  }

  @Override
  public void close() throws IOException {
    fileSystemService.getResourceManager().close();
  }
}
