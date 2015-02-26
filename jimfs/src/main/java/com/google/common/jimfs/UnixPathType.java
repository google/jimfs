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

package com.google.common.jimfs;

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
   * Unix path type.
   */
  static final PathType INSTANCE = new UnixPathType();

  private UnixPathType() {
    super(false, '/');
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
    int nulIndex = path.indexOf('\0');
    if (nulIndex != -1) {
      throw new InvalidPathException(path, "nul character not allowed", nulIndex);
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
  public String toUriPath(String root, Iterable<String> names, boolean directory) {
    StringBuilder builder = new StringBuilder();
    for (String name : names) {
      builder.append('/').append(name);
    }

    if (directory || builder.length() == 0) {
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
