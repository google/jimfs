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
import static com.google.jimfs.path.PathType.windows;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;

import java.nio.file.InvalidPathException;

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
  public void testParsePath() {
    ParseResult path = type.parsePath("foo/bar/baz/one\\two");
    assertParseResult(path, null, "foo", "bar", "baz", "one", "two");

    ParseResult path2 = type.parsePath("$one//\\two");
    assertParseResult(path2, "$", "one", "two");
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
    assertParseResult(path, "/", "foo", "bar");
    ASSERT.that(unix.toString(path.root(), path.names())).is("/foo/bar");

    ParseResult path2 = unix.parsePath("foo/bar/");
    assertParseResult(path2, null, "foo", "bar");
    ASSERT.that(unix.toString(path2.root(), path2.names())).is("foo/bar");
  }

  @Test
  public void testWindows() {
    PathType windows = PathType.windows();
    ASSERT.that(windows.getSeparator()).is("\\");
    ASSERT.that(windows.getOtherSeparators()).is("/");
    ASSERT.that(windows.getCaseSensitivity()).is(CaseSensitivity.CASE_INSENSITIVE_ASCII);

    // "C:\\foo\bar" results from "C:\", "foo", "bar" passed to getPath
    ParseResult path = windows.parsePath("C:\\\\foo\\bar");
    assertParseResult(path, "C:\\", "foo", "bar");
    ASSERT.that(windows.toString(path.root(), path.names())).is("C:\\foo\\bar");

    ParseResult path2 = windows.parsePath("foo/bar/");
    assertParseResult(path2, null, "foo", "bar");
    ASSERT.that(windows.toString(path2.root(), path2.names())).is("foo\\bar");

    ParseResult path3 = windows.parsePath("hello world/foo/bar");
    assertParseResult(path3, null, "hello world", "foo", "bar");
    ASSERT.that(windows.toString(null, path3.names())).is("hello world\\foo\\bar");
  }

  @Test
  public void testWindows_relativePathsWithDriveRoot_unsupported() {
    try {
      windows().parsePath("C:");
      fail();
    } catch (InvalidPathException expected) {}

    try {
      windows().parsePath("C:foo\\bar");
      fail();
    } catch (InvalidPathException expected) {}
  }

  @Test
  public void testWindows_uncPaths() {
    PathType windows = PathType.windows();
    ParseResult path = windows.parsePath("\\\\host\\share");
    assertParseResult(path, "\\\\host\\share\\");

    path = windows.parsePath("\\\\HOST\\share\\foo\\bar");
    assertParseResult(path, "\\\\HOST\\share\\", "foo", "bar");

    try {
      windows.parsePath("\\\\");
      fail();
    } catch (InvalidPathException expected) {
      ASSERT.that(expected.getInput()).is("\\\\");
      ASSERT.that(expected.getReason()).is("UNC path is missing hostname");
    }

    try {
      windows.parsePath("\\\\host");
      fail();
    } catch (InvalidPathException expected) {
      ASSERT.that(expected.getInput()).is("\\\\host");
      ASSERT.that(expected.getReason()).is("UNC path is missing sharename");
    }

    try {
      windows.parsePath("\\\\host\\");
      fail();
    } catch (InvalidPathException expected) {
      ASSERT.that(expected.getInput()).is("\\\\host\\");
      ASSERT.that(expected.getReason()).is("UNC path is missing sharename");
    }

    try {
      windows.parsePath("//host");
      fail();
    } catch (InvalidPathException expected) {
      ASSERT.that(expected.getInput()).is("//host");
      ASSERT.that(expected.getReason()).is("UNC path is missing sharename");
    }
  }

  @Test
  public void testWindows_illegalNames() {
    try {
      windows().parsePath("foo<bar");
      fail();
    } catch (InvalidPathException expected) {}

    try {
      windows().parsePath("foo?");
      fail();
    } catch (InvalidPathException expected) {}

    try {
      windows().parsePath("foo ");
      fail();
    } catch (InvalidPathException expected) {}

    try {
      windows().parsePath("foo \\bar");
      fail();
    } catch (InvalidPathException expected) {}
  }

  private static void assertParseResult(
      ParseResult result, @Nullable String root, String... names) {
    ASSERT.that(result.root()).is(root);
    ASSERT.that(result.names()).iteratesAs(names);
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
