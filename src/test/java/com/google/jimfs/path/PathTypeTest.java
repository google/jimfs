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

import static com.google.jimfs.path.PathType.ParseResult;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import javax.annotation.Nullable;

/**
 * Tests for {@link PathType}.
 *
 * @author Colin Decker
 */
public class PathTypeTest {

  private final FakePathType type = new FakePathType(CaseSensitivity.CASE_SENSITIVE);

  @Test
  public void testBasicProperties() {
    ASSERT.that(type.getSeparator()).is("/");
    ASSERT.that(type.getOtherSeparators()).is("\\");
    ASSERT.that(type.getCaseSensitivity()).is(CaseSensitivity.CASE_SENSITIVE);
  }

  @Test
  public void testNames() {
    ASSERT.that(type.getName("foo")).is(Name.simple("foo"));
    ASSERT.that(type.asNames(ImmutableList.of("foo", "bar")))
        .iteratesAs(Name.simple("foo"), Name.simple("bar"));
  }

  @Test
  public void testNames_caseInsensitiveAscii() {
    FakePathType type2 = new FakePathType(CaseSensitivity.CASE_INSENSITIVE_ASCII);
    ASSERT.that(type2.getName("foo")).is(Name.caseInsensitiveAscii("foo"));
    ASSERT.that(type2.getName("foo")).isEqualTo(type2.getName("FOO"));
    ASSERT.that(type2.asNames(ImmutableList.of("foo", "bar")))
        .iteratesAs(type2.asNames(ImmutableList.of("FOO", "bAr")));
  }

  @Test
  public void testParsePath() {
    ParseResult path = type.parsePath("foo/bar/baz/one\\two");
    ASSERT.that(path.isAbsolute()).isFalse();
    ASSERT.that(path.names()).iteratesAs("foo", "bar", "baz", "one", "two");

    ParseResult path2 = type.parsePath("$one//\\two");
    ASSERT.that(path2.isAbsolute()).isTrue();
    ASSERT.that(path2.root()).is("$");
    ASSERT.that(path2.names()).iteratesAs("one", "two");
  }

  @Test
  public void testToString() {
    ParseResult path = type.parsePath("foo/bar\\baz");
    ASSERT.that(type.toString(path.root(), path.names())).is("foo/bar/baz");

    ParseResult path2 = type.parsePath("$/foo/bar");
    ASSERT.that(type.toString(path2.root(), path2.names())).is("$foo/bar");
  }

  @Test
  public void testUnix() {
    PathType unix = PathType.unix();
    ASSERT.that(unix.getSeparator()).is("/");
    ASSERT.that(unix.getOtherSeparators()).is("");
    ASSERT.that(unix.getCaseSensitivity()).is(CaseSensitivity.CASE_SENSITIVE);

    // "//foo/bar" is what will be passed to parsePath if "/", "foo", "bar" is passed to getPath
    ParseResult path = unix.parsePath("//foo/bar");
    ASSERT.that(path.isAbsolute()).isTrue();
    ASSERT.that(path.root()).is("/");
    ASSERT.that(path.names()).iteratesAs("foo", "bar");
    ASSERT.that(unix.toString(path.root(), path.names())).is("/foo/bar");

    ParseResult path2 = unix.parsePath("foo/bar/");
    ASSERT.that(path2.isAbsolute()).isFalse();
    ASSERT.that(path2.names()).iteratesAs("foo", "bar");
    ASSERT.that(unix.toString(path2.root(), path2.names())).is("foo/bar");
  }

  @Test
  public void testWindows() {
    PathType windows = PathType.windows();
    ASSERT.that(windows.getSeparator()).is("\\");
    ASSERT.that(windows.getOtherSeparators()).is("/");
    ASSERT.that(windows.getCaseSensitivity()).is(CaseSensitivity.CASE_INSENSITIVE_ASCII);

    ASSERT.that(windows.getName("foo")).isEqualTo(Name.caseInsensitiveAscii("foo"));
    ASSERT.that(windows.getRootName("C:")).isEqualTo(windows.getRootName("c:"));

    // "C:\\foo\bar" results from "C:\", "foo", "bar" passed to getPath
    ParseResult path = windows.parsePath("C:\\\\foo\\bar");
    ASSERT.that(path.isAbsolute()).isTrue();
    ASSERT.that(path.root()).is("C:");
    ASSERT.that(path.names()).iteratesAs("foo", "bar");
    ASSERT.that(windows.toString(path.root(), path.names())).is("C:\\foo\\bar");

    ParseResult path2 = windows.parsePath("foo/bar/");
    ASSERT.that(path2.isAbsolute()).isFalse();
    ASSERT.that(path2.names())
        .iteratesAs("foo", "bar");
    ASSERT.that(windows.toString(path2.root(), path2.names())).is("foo\\bar");
  }

  /**
   * Arbitrary path type with $ as the root, / as the separator and \ as an alternate separator.
   */
  private static final class FakePathType extends PathType {

    protected FakePathType(CaseSensitivity caseSensitivity) {
      super(caseSensitivity, '/', '\\');
    }

    @Override
    public ParseResult parsePath(String path) {
      String root = null;
      if (path.startsWith("$")) {
        root = "$";
        path = path.substring(1);
      }

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
}
