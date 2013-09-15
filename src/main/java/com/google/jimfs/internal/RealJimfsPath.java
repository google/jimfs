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

import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;

import javax.annotation.Nullable;

/**
 * Real implementation of {@link JimfsPath} with access to the file system.
 *
 * @author Colin Decker
 */
final class RealJimfsPath extends JimfsPath {

  RealJimfsPath(RealPathService pathService, @Nullable Name root, Iterable<Name> names) {
    super(pathService, root, names);
  }

  @Override
  public JimfsFileSystem getFileSystem() {
    return ((RealPathService) pathService).getFileSystem();
  }

  @Override
  public URI toUri() {
    return getFileSystem().getUri(this);
  }

  @Override
  public JimfsPath toAbsolutePath() {
    return isAbsolute() ? this : getFileSystem().getWorkingDirectory().resolve(this);
  }

  @Override
  public Path toRealPath(LinkOption... options) throws IOException {
    return getFileSystem().getFileTree(this).toRealPath(this, LinkHandling.fromOptions(options));
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
      WatchEvent.Modifier... modifiers) throws IOException {
    checkNotNull(modifiers);
    return register(watcher, events);
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
    checkNotNull(watcher);
    checkNotNull(events);
    if (!(watcher instanceof AbstractWatchService)) {
      throw new IllegalArgumentException(
          "watcher (" + watcher + ") is not associated with this file system");
    }

    AbstractWatchService service = (AbstractWatchService) watcher;
    return service.register(this, Arrays.asList(events));
  }
}
