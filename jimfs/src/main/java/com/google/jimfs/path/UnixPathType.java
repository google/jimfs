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

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.InvalidPathException;

import javax.annotation.Nullable;

/**
 * Unix-style path type.
 *
 * @author Colin Decker
 */
final class UnixPathType extends PathType {

  /**
   * Default Unix path type, with non-normalized, case sensitive names as in Linux.
   */
  static final PathType UNIX =
      new UnixPathType(Normalization.none(), Normalization.none());

  /**
   * Unix path type with normalized, case insensitive (ASCII) names as in Mac OS X.
   */
  static final PathType OS_X = new UnixPathType(
      Normalization.normalizedCaseInsensitiveAscii(), Normalization.normalized());

  UnixPathType(Normalization lookupNormalization, Normalization pathNormalization) {
    super(lookupNormalization, pathNormalization, false, '/');
  }

  @Override
  public PathType lookupNormalization(Normalization normalization) {
    return new UnixPathType(normalization, pathNormalization());
  }

  @Override
  public PathType pathNormalization(Normalization normalization) {
    return new UnixPathType(lookupNormalization(), normalization);
  }

  @Override
  public ParseResult parsePath(String path) {
    if (path.isEmpty()) {
      return emptyPath();
    }

    checkValid(path);

    String root = path.startsWith("/") ? "/" : null;
    return new ParseResult(root, splitter().split(path));
  }

  private static void checkValid(String path) {
    for (int i = 0; i < path.length(); i++) {
      if (path.charAt(i) == '\0') {
        throw new InvalidPathException(path, "nul character not allowed", i);
      }
    }
  }

  @Override
  public String toString(@Nullable String root, Iterable<String> names) {
    StringBuilder builder = new StringBuilder();
    if (root != null) {
      builder.append(root);
    }
    joiner().appendTo(builder, names);
    return builder.toString();
  }

  @Override
  public String toUriPath(String root, Iterable<String> names) {
    StringBuilder builder = new StringBuilder();
    for (String name : names) {
      builder.append('/').append(name);
    }

    if (builder.length() == 0) {
      builder.append('/');
    }
    return builder.toString();
  }

  @Override
  public ParseResult parseUriPath(String uriPath) {
    checkArgument(uriPath.startsWith("/"), "uriPath (%s) must start with /", uriPath);
    return parsePath(uriPath);
  }
}
