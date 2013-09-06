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

package com.google.jimfs.internal.path;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.nio.file.FileSystem;
import java.nio.file.PathMatcher;

import javax.annotation.Nullable;

/**
 * Service for creating {@link JimfsPath} instances and handling other path-related operations.
 *
 * @author Colin Decker
 */
public abstract class PathService {

  private final PathType type;

  protected PathService(PathType type) {
    this.type = checkNotNull(type);
  }

  /**
   * Returns the path type for this service.
   */
  public final PathType type() {
    return type;
  }

  private volatile JimfsPath emptyPath;

  /**
   * Returns an empty path which has a single name, the empty string.
   */
  public final JimfsPath emptyPath() {
    JimfsPath result = emptyPath;
    if (result == null) {
      // use createPathInternal to avoid recursive call from createPath()
      result = createPathInternal(null, ImmutableList.of(type.getName("", false)));
      emptyPath = result;
      return result;
    }
    return result;
  }

  /**
   * Returns a root path with the given name.
   */
  public final JimfsPath createRoot(Name root) {
    return createPath(checkNotNull(root), ImmutableList.<Name>of());
  }

  /**
   * Returns a single filename path with the given name.
   */
  public final JimfsPath createFileName(Name name) {
    return createPath(null, ImmutableList.of(name));
  }

  /**
   * Returns a relative path with the given names.
   */
  public final JimfsPath createRelativePath(Iterable<Name> names) {
    return createPath(null, ImmutableList.copyOf(names));
  }

  /**
   * Returns a path with the given root (or no root, if null) and the given names.
   */
  public final JimfsPath createPath(@Nullable Name root, Iterable<Name> names) {
    ImmutableList<Name> nameList = ImmutableList.copyOf(Iterables.filter(names, NOT_EMPTY));
    if (root == null && nameList.isEmpty()) {
      // ensure the canonical empty path (one empty string name) is used rather than a path with
      // no root and no names
      return emptyPath();
    }
    return createPathInternal(root, nameList);
  }

  /**
   * Returns a path with the given root (or no root, if null) and the given names.
   */
  protected abstract JimfsPath createPathInternal(@Nullable Name root, Iterable<Name> names);

  /**
   * Parses the given strings as a path.
   */
  public final JimfsPath parsePath(String first, String... more) {
    return type.parsePath(this, first, more);
  }

  /**
   * Returns the string form of the given path.
   */
  public final String toString(JimfsPath path) {
    return type.toString(path);
  }

  /**
   * Returns a {@link PathMatcher} for the given syntax and pattern as specified by
   * {@link FileSystem#getPathMatcher(String)}.
   */
  public final PathMatcher createPathMatcher(String syntaxAndPattern) {
    return PathMatchers.getPathMatcher(
        syntaxAndPattern, type.getSeparator() + type.getOtherSeparators());
  }

  private static final Predicate<Name> NOT_EMPTY = new Predicate<Name>() {
    @Override
    public boolean apply(Name input) {
      return !input.toString().isEmpty();
    }
  };
}
