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

import com.google.jimfs.path.Name;
import com.google.jimfs.path.PathType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Unix-style path for testing that supports all path-related methods but no methods that involve
 * an actual file system in any way. Returns a fake, singleton file system from the
 * {@code getFileSystem()} method just to satisfy equality checks that require two paths to have
 * the same file system.
 *
 * @author Colin Decker
 */
public class TestPath extends JimfsPath {

  public TestPath(@Nullable Name root, Iterable<Name> names) {
    this(new TestPathService(PathType.unix()), root, names);
  }

  public TestPath(TestPathService pathService, @Nullable Name root, Iterable<Name> names) {
    super(pathService, root, names);
  }

  @Override
  public FileSystem getFileSystem() {
    return FAKE_FILE_SYSTEM;
  }

  @Override
  public URI toUri() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path toAbsolutePath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path toRealPath(LinkOption... options) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
      WatchEvent.Modifier... modifiers) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Fake file system just for returning from getFileSystem().
   */
  private static final FileSystem FAKE_FILE_SYSTEM = new FileSystem() {
    @Override
    public FileSystemProvider provider() {
      return null;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public boolean isReadOnly() {
      return false;
    }

    @Override
    public String getSeparator() {
      return null;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
      return null;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
      return null;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
      return null;
    }

    @Override
    public Path getPath(String first, String... more) {
      return null;
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
      return null;
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
      return null;
    }

    @Override
    public WatchService newWatchService() throws IOException {
      return null;
    }
  };
}
