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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.regex.Pattern;

/**
 * {@link PathMatcher} factory for any file system.
 *
 * @author Colin Decker
 */
final class PathMatchers {

  private PathMatchers() {}

  /**
   * Gets a {@link PathMatcher} for the given syntax and pattern as specified by
   * {@link FileSystem#getPathMatcher}. The {@code separators} string contains the path name
   * element separators (one character each) recognized by the file system. For a glob-syntax path
   * matcher, any of the given separators will be recognized as a separator in the pattern, and any
   * of them will be matched as a separator when checking a path.
   */
  // TODO(cgdecker): Should I be just canonicalizing separators rather than matching any separator?
  // Perhaps so, assuming Path always canonicalizes its separators
  public static PathMatcher getPathMatcher(
      String syntaxAndPattern, String separators, ImmutableSet<PathNormalization> normalizations) {
    int syntaxSeparator = syntaxAndPattern.indexOf(':');
    checkArgument(
        syntaxSeparator > 0, "Must be of the form 'syntax:pattern': %s", syntaxAndPattern);

    String syntax = Ascii.toLowerCase(syntaxAndPattern.substring(0, syntaxSeparator));
    String pattern = syntaxAndPattern.substring(syntaxSeparator + 1);

    switch (syntax) {
      case "glob":
        pattern = GlobToRegex.toRegex(pattern, separators);
        // fall through
      case "regex":
        return fromRegex(pattern, normalizations);
      default:
        throw new UnsupportedOperationException("Invalid syntax: " + syntaxAndPattern);
    }
  }

  private static PathMatcher fromRegex(String regex, Iterable<PathNormalization> normalizations) {
    return new RegexPathMatcher(PathNormalization.compilePattern(regex, normalizations));
  }

  /**
   * {@code PathMatcher} that matches the {@code toString()} form of a {@code Path} against a regex
   * {@code Pattern}.
   */
  @VisibleForTesting
  static final class RegexPathMatcher implements PathMatcher {

    private final Pattern pattern;

    private RegexPathMatcher(Pattern pattern) {
      this.pattern = checkNotNull(pattern);
    }

    @Override
    public boolean matches(Path path) {
      return pattern.matcher(path.toString()).matches();
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).addValue(pattern).toString();
    }
  }
}
