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

import java.nio.file.InvalidPathException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Windows-style path type.
 *
 * @author Colin Decker
 */
final class WindowsPathType extends PathType {

  /**
   * Windows path type.
   */
  static final WindowsPathType INSTANCE = new WindowsPathType();

  /**
   * Matches the C:foo\bar path format, which has a root (C:) and names (foo\bar) and matches
   * a path relative to the working directory on that drive. Currently can't support that format
   * as it requires behavior that differs completely from Unix.
   */
  // TODO(cgdecker): Can probably support this at some point
  // It would require:
  // - A method like PathType.isAbsolute(Path) or something to that effect; this would allow
  //   WindowsPathType to distinguish between an absolute root path (C:\) and a relative root
  //   path (C:)
  // - Special handling for relative paths that have a root. This handling would determine the
  //   root directory and then determine the working directory from there. The file system would
  //   still have one working directory; for the root that working directory is under, it is the
  //   working directory. For every other root, the root itself is the working directory.
  private static final Pattern WORKING_DIR_WITH_DRIVE = Pattern.compile("^[a-zA-Z]:([^\\\\].*)?$");

  /**
   * Pattern for matching trailing spaces in file names.
   */
  private static final Pattern TRAILING_SPACES = Pattern.compile("[ ]+(\\\\|$)");

  private WindowsPathType() {
    super(true, '\\', '/');
  }

  @Override
  public ParseResult parsePath(String path) {
    String original = path;
    path = path.replace('/', '\\');

    if (WORKING_DIR_WITH_DRIVE.matcher(path).matches()) {
      throw new InvalidPathException(
          original,
          "Jimfs does not currently support the Windows syntax for a relative path "
              + "on a specific drive (e.g. \"C:foo\\bar\"");
    }

    String root;
    if (path.startsWith("\\\\")) {
      root = parseUncRoot(path, original);
    } else if (path.startsWith("\\")) {
      throw new InvalidPathException(
          original,
          "Jimfs does not currently support the Windows syntax for an absolute path "
              + "on the current drive (e.g. \"\\foo\\bar\"");
    } else {
      root = parseDriveRoot(path);
    }

    // check for root.length() > 3 because only "C:\" type roots are allowed to have :
    int startIndex = root == null || root.length() > 3 ? 0 : root.length();
    for (int i = startIndex; i < path.length(); i++) {
      char c = path.charAt(i);
      if (isReserved(c)) {
        throw new InvalidPathException(original, "Illegal char <" + c + ">", i);
      }
    }

    Matcher trailingSpaceMatcher = TRAILING_SPACES.matcher(path);
    if (trailingSpaceMatcher.find()) {
      throw new InvalidPathException(original, "Trailing char < >", trailingSpaceMatcher.start());
    }

    if (root != null) {
      path = path.substring(root.length());

      if (!root.endsWith("\\")) {
        root = root + "\\";
      }
    }

    return new ParseResult(root, splitter().split(path));
  }

  /**
   * Pattern for matching UNC \\host\share root syntax.
   */
  private static final Pattern UNC_ROOT = Pattern.compile("^(\\\\\\\\)([^\\\\]+)?(\\\\[^\\\\]+)?");

  /**
   * Parse the root of a UNC-style path, throwing an exception if the path does not start with
   * a valid UNC root.
   */
  private String parseUncRoot(String path, String original) {
    Matcher uncMatcher = UNC_ROOT.matcher(path);
    if (uncMatcher.find()) {
      String host = uncMatcher.group(2);
      if (host == null) {
        throw new InvalidPathException(original, "UNC path is missing hostname");
      }
      String share = uncMatcher.group(3);
      if (share == null) {
        throw new InvalidPathException(original, "UNC path is missing sharename");
      }

      return path.substring(uncMatcher.start(), uncMatcher.end());
    } else {
      // probably shouldn't ever reach this
      throw new InvalidPathException(original, "Invalid UNC path");
    }
  }

  /**
   * Pattern for matching normal C:\ drive letter root syntax.
   */
  private static final Pattern DRIVE_LETTER_ROOT = Pattern.compile("^[a-zA-Z]:\\\\");

  /**
   * Parses a normal drive-letter root, e.g. "C:\".
   */
  @Nullable
  private String parseDriveRoot(String path) {
    Matcher drivePathMatcher = DRIVE_LETTER_ROOT.matcher(path);
    if (drivePathMatcher.find()) {
      return path.substring(drivePathMatcher.start(), drivePathMatcher.end());
    }
    return null;
  }

  /**
   * Checks if c is one of the reserved characters that aren't allowed in Windows file names.
   */
  private static boolean isReserved(char c) {
    switch (c) {
      case '<':
      case '>':
      case ':':
      case '"':
      case '|':
      case '?':
      case '*':
        return true;
      default:
        return c <= 31;
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
    if (root.startsWith("\\\\")) {
      root = root.replace('\\', '/');
    } else {
      root = "/" + root.replace('\\', '/');
    }

    StringBuilder builder = new StringBuilder();
    builder.append(root);

    Iterator<String> iter = names.iterator();
    if (iter.hasNext()) {
      builder.append(iter.next());
      while (iter.hasNext()) {
        builder.append('/').append(iter.next());
      }
    }

    if (directory && builder.charAt(builder.length() - 1) != '/') {
      builder.append('/');
    }

    return builder.toString();
  }

  @Override
  public ParseResult parseUriPath(String uriPath) {
    uriPath = uriPath.replace('/', '\\');
    if (uriPath.charAt(0) == '\\' && uriPath.charAt(1) != '\\') {
      // non-UNC path, so the leading / was just there for the URI path format and isn't part
      // of what should be parsed
      uriPath = uriPath.substring(1);
    }
    return parsePath(uriPath);
  }
}
