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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.nio.file.InvalidPathException;

import javax.annotation.Nullable;

/**
 * An object defining a specific type of path. Knows how to parse strings to a path and how to
 * render a path as a string as well as what the path separator is and what other separators are
 * recognized when parsing paths.
 *
 * @author Colin Decker
 */
public abstract class PathType {

  /**
   * Returns a Unix-style path type. "/" is both the root and the only separator. Any path starting
   * with "/" is considered absolute. Paths are case sensitive.
   */
  public static PathType unix() {
    return UnixPathType.INSTANCE;
  }

  /**
   * Returns a Unix-style path type. "/" is both the root and the only separator. Any path starting
   * with "/" is considered absolute. Paths use the given case sensitivity setting.
   */
  public static PathType unix(CaseSensitivity caseSensitivity) {
    return new UnixPathType(caseSensitivity);
  }

  /**
   * Returns a Windows-style path type. The canonical separator character is "\". "/" is also
   * treated as a separator when parsing paths. Any initial name in the path consisting of a single
   * alphabet letter followed by ":" is considered to be a root. Paths are case insensitive for
   * ASCII characters.
   */
  public static PathType windows() {
    return WindowsPathType.INSTANCE;
  }

  /**
   * Returns a Windows-style path type. The canonical separator character is "\". "/" is also
   * treated as a separator when parsing paths. Any initial name in the path consisting of a single
   * alphabet letter followed by ":" is considered to be a root. Paths use the given case
   * sensitivity setting.
   */
  public static PathType windows(CaseSensitivity caseSensitivity) {
    return new WindowsPathType(caseSensitivity);
  }

  private final CaseSensitivity caseSensitivity;
  private final String separator;
  private final String otherSeparators;
  private final Joiner joiner;
  private final Splitter splitter;

  protected PathType(
      CaseSensitivity caseSensitivity, char separator, char... otherSeparators) {
    this.caseSensitivity = checkNotNull(caseSensitivity);
    this.separator = String.valueOf(separator);
    this.otherSeparators = String.valueOf(otherSeparators);
    this.joiner = Joiner.on(separator);
    // TODO(cgdecker): This uses CharMatcher, which is @Beta... what to do
    this.splitter = otherSeparators.length == 0
        ? Splitter.on(separator).omitEmptyStrings()
        : Splitter.on(CharMatcher.anyOf(this.separator + this.otherSeparators)).omitEmptyStrings();
  }

  /**
   * Returns the canonical separator for this path type. The returned string always has a length of
   * one.
   */
  public final String getSeparator() {
    return separator;
  }

  /**
   * Returns the other separators that are recognized when parsing a path. If no other separators
   * are recognized, the empty string is returned.
   */
  public final String getOtherSeparators() {
    return otherSeparators;
  }

  /**
   * Returns the path joiner for this path type.
   */
  public final Joiner joiner() {
    return joiner;
  }

  /**
   * Returns the path splitter for this path type.
   */
  public final Splitter splitter() {
    return splitter;
  }

  /**
   * Returns the case sensitivity setting of paths of this type.
   */
  public final CaseSensitivity getCaseSensitivity() {
    return caseSensitivity;
  }

  /**
   * Returns an empty path.
   */
  protected final ParseResult emptyPath() {
    return new ParseResult(null, ImmutableList.of(""));
  }

  /**
   * Parses the given strings as a path.
   */
  public abstract ParseResult parsePath(String path);

  /**
   * Returns the string form of the given path.
   */
  public abstract String toString(String root, Iterable<String> names);

  /**
   * Unix-style path type.
   */
  private static final class UnixPathType extends PathType {

    /**
     * Default Unix path type, with case sensitive names as in Linux.
     */
    private static final UnixPathType INSTANCE = new UnixPathType(CaseSensitivity.CASE_SENSITIVE);

    private UnixPathType(CaseSensitivity caseSensitivity) {
      super(caseSensitivity, '/');
    }

    @Override
    public ParseResult parsePath(String path) {
      if (path.isEmpty()) {
        return emptyPath();
      }

      String root = path.startsWith("/") ? "/" : null;
      return new ParseResult(root, splitter().split(path));
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
  }

  /**
   * Windows-style path type.
   */
  private static final class WindowsPathType extends PathType {

    /**
     * Default Windows path type, with ASCII case insensitive names, as Windows is case insensitive
     * by default and ASCII case insensitivity should be fine for most usages.
     */
    private static final WindowsPathType INSTANCE
        = new WindowsPathType(CaseSensitivity.CASE_INSENSITIVE_ASCII);

    private WindowsPathType(CaseSensitivity caseSensitivity) {
      super(caseSensitivity, '\\', '/');
    }

    private static final int ROOT_LENGTH = 3;

    @Override
    public ParseResult parsePath(String path) {
      String root = null;
      if (startsWithRoot(path)) {
        root = path.substring(0, ROOT_LENGTH);
      }

      int startIndex = root == null ? 0 : ROOT_LENGTH;
      for (int i = startIndex; i < path.length(); i++) {
        char c = path.charAt(i);
        if (isReserved(c)) {
          throw new InvalidPathException(path, "Illegal char <" + c + ">", i);
        }
      }

      if (root != null) {
        path = path.substring(3);
      }

      return new ParseResult(root, splitter().split(path));
    }

    private static boolean startsWithRoot(String string) {
      return string.length() >= ROOT_LENGTH
          && isLetter(string.charAt(0))
          && string.charAt(1) == ':'
          && string.charAt(2) == '\\';
    }

    /**
     * Checks if c is one of the reserved characters that aren't allowed in Windows file names.
     * See <a href="http://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx#naming_conventions">this article</a>.
     */
    // TODO(cgdecker): consider making this an overridable method in PathType itself?
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

    private static boolean isLetter(char c) {
      return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
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
  }

  /**
   * Simple result of parsing a path.
   */
  public static final class ParseResult {

    @Nullable
    private final String root;
    private final Iterable<String> names;

    public ParseResult(String root, Iterable<String> names) {
      this.root = root;
      this.names = names;
    }

    /**
     * Returns whether or not this result is an absolute path.
     */
    public boolean isAbsolute() {
      return root != null;
    }

    /**
     * Returns the parsed root element, or null if there was no root.
     */
    @Nullable
    public String root() {
      return root;
    }

    /**
     * Returns the parsed name elements.
     */
    public Iterable<String> names() {
      return names;
    }
  }
}
