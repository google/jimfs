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

package com.google.jimfs.path;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

/**
 * Simplest representation of a path; contains an optional root and a list of names.
 *
 * @author Colin Decker
 */
public class SimplePath {

  @Nullable
  protected final Name root;
  protected final ImmutableList<Name> names;

  /**
   * Creates a new path with the given root and names.
   */
  public SimplePath(@Nullable Name root, Iterable<Name> names) {
    this.root = root;
    this.names = ImmutableList.copyOf(names);
  }

  /**
   * Returns whether or not this path is absolute.
   */
  public boolean isAbsolute() {
    return root != null;
  }

  /**
   * Returns the root of this path.
   */
  @Nullable
  public Name root() {
    return root;
  }

  /**
   * Returns the names for this path.
   */
  public ImmutableList<Name> names() {
    return names;
  }
}
