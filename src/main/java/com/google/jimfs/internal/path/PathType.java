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

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import com.ibm.icu.text.Normalizer2;

import java.util.regex.Pattern;

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

  protected PathType(
      CaseSensitivity caseSensitivity, char separator, char... otherSeparators) {
    this.caseSensitivity = checkNotNull(caseSensitivity);
    this.separator = String.valueOf(separator);
    this.otherSeparators = String.valueOf(otherSeparators);
  }

  /**
   * Returns the canonical separator for this path type. The returned string always has a length of
   * one.
   */
  public String getSeparator() {
    return separator;
  }

  /**
   * Returns the other separators that are recognized when parsing a path. If no other separators
   * are recognized, the empty string is returned.
   */
  public String getOtherSeparators() {
    return otherSeparators;
  }

  /**
   * Returns the case sensitivity setting of paths of this type.
   */
  public CaseSensitivity getCaseSensitivity() {
    return caseSensitivity;
  }

  /**
   * Returns a name object for the given string.
   */
  public abstract Name getName(String name, boolean root);

  /**
   * Returns a view of the given iterable of non-root name strings as name objects.
   */
  public final Iterable<Name> asNames(Iterable<String> names) {
    return Iterables.transform(names, new Function<String, Name>() {
      @Nullable
      @Override
      public Name apply(String input) {
        return getName(input, false);
      }
    });
  }

  /**
   * Parses the given strings as a path.
   */
  public abstract JimfsPath parsePath(PathService service, String first, String... more);

  /**
   * Returns the string form of the given path.
   */
  public abstract String toString(JimfsPath path);

  /**
   * Unix-style path type.
   */
  private static final class UnixPathType extends PathType {

    private static final UnixPathType INSTANCE = new UnixPathType(CaseSensitivity.CASE_SENSITIVE);

    private static final Joiner JOINER = Joiner.on('/');
    private static final Splitter SPLITTER = Splitter.on('/').omitEmptyStrings();

    private UnixPathType(CaseSensitivity caseSensitivity) {
      super(caseSensitivity, '/');
    }

    @Override
    public Name getName(String name, boolean root) {
      return getCaseSensitivity().createName(name);
    }

    @Override
    public JimfsPath parsePath(PathService service, String first, String... more) {
      if (first.isEmpty() && more.length == 0) {
        return service.emptyPath();
      }

      Name root = null;
      if (first.startsWith("/")) {
        root = getName("/", true);
        first = first.substring(1);
      }

      String joined = JOINER.join(Lists.asList(first, more));
      return service.createPath(root, asNames(SPLITTER.split(joined)));
    }

    @Override
    public String toString(JimfsPath path) {
      StringBuilder builder = new StringBuilder();
      if (path.isAbsolute()) {
        builder.append(path.root());
      }
      JOINER.appendTo(builder, path.names());
      return builder.toString();
    }
  }

  /**
   * Windows-style path type.
   */
  private static final class WindowsPathType extends PathType {

    private static final WindowsPathType INSTANCE
        = new WindowsPathType(CaseSensitivity.CASE_INSENSITIVE_ASCII);

    private static final Joiner JOINER = Joiner.on('\\');
    private static final Splitter SPLITTER = Splitter.on(Pattern.compile("[/\\\\]"))
        .omitEmptyStrings();

    private WindowsPathType(CaseSensitivity caseSensitivity) {
      super(caseSensitivity, '\\', '/');
    }

    @Override
    public Name getName(String name, boolean root) {
      if (root) {
        Name canonical = getCaseSensitivity().createName(Ascii.toUpperCase(name) + "\\");
        return Name.create(name, canonical);
      }
      return getCaseSensitivity().createName(name);
    }

    @Override
    public JimfsPath parsePath(PathService service, String first, String... more) {
      String joined = JOINER.join(Lists.asList(first, more));
      Name root = null;
      if (joined.length() >= 2
          && CharMatcher.JAVA_LETTER.matches(joined.charAt(0))
          && joined.charAt(1) == ':') {
        root = getName(joined.substring(0, 2), true);
        joined = joined.substring(2);
      }

      return service.createPath(root, asNames(SPLITTER.split(joined)));
    }

    @Override
    public String toString(JimfsPath path) {
      StringBuilder builder = new StringBuilder();
      Name root = path.root();
      if (root != null) {
        String rootString = root.toString();
        builder.append(rootString);
        if (!rootString.endsWith("\\") && path.getNameCount() > 0) {
          builder.append("\\");
        }
      }
      JOINER.appendTo(builder, path.names());
      return builder.toString();
    }
  }

  /**
   * Case sensitivity settings for paths. Note that path case sensitivity only affects the case
   * sensitivity of lookups. Two path objects with the same characters in different cases will
   * always be considered unequal.
   */
  @SuppressWarnings("unused")
  public enum CaseSensitivity {
    /**
     * Paths are case sensitive.
     */
    CASE_SENSITIVE {
      @Override
      protected Name createName(String string) {
        return Name.simple(string);
      }
    },

    /**
     * Paths are case insensitive, but only for ASCII characters. Faster than
     * {@link #CASE_INSENSITIVE_UNICODE} if you only plan on using ASCII file names anyway.
     */
    CASE_INSENSITIVE_ASCII {
      @Override
      protected Name createName(String string) {
        return Name.caseInsensitiveAscii(string);
      }
    },

    /**
     * Paths are case sensitive by way of Unicode NFKC Casefolding normalization. Requires ICU4J
     * on your classpath.
     */
    CASE_INSENSITIVE_UNICODE {
      @Override
      protected Name createName(String string) {
        return Name.normalizing(string, Normalizer2.getNFKCCasefoldInstance());
      }
    };

    /**
     * Creates a new name with these case sensitivity settings.
     */
    protected abstract Name createName(String string);
  }
}
