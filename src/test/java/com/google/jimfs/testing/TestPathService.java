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

package com.google.jimfs.testing;

import com.google.jimfs.internal.path.JimfsPath;
import com.google.jimfs.internal.path.Name;
import com.google.jimfs.internal.path.PathService;
import com.google.jimfs.internal.path.PathType;

import javax.annotation.Nullable;

/**
 * Unix-style singleton {@link PathService} for creating {@link TestPath} instances.
 *
 * @author Colin Decker
 */
public final class TestPathService extends PathService {

  public TestPathService(PathType type) {
    super(type);
  }

  @Override
  public JimfsPath createPathInternal(@Nullable Name root, Iterable<Name> names) {
    return new TestPath(this, root, names);
  }
}
