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

import com.google.jimfs.path.PathType;

import javax.annotation.Nullable;

/**
 * Unix-style singleton {@link PathService} for creating {@link TestPath} instances.
 *
 * @author Colin Decker
 */
public final class TestPathService extends PathService {

  public static final TestPathService UNIX = new TestPathService(PathType.unix());
  public static final TestPathService WINDOWS = new TestPathService(PathType.windows());

  public TestPathService(PathType type) {
    super(type);
  }

  @Override
  public void setFileSystem(JimfsFileSystem fileSystem) {
  }

  @Override
  public JimfsPath createPathInternal(@Nullable Name root, Iterable<Name> names) {
    return new TestPath(this, root, names);
  }
}
