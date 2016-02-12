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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Tests for {@link JimfsPath}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class JimfsPathTest {

  private final PathService pathService = PathServiceTest.fakeUnixPathService();

  @Test
  public void testPathParsing() {
    assertPathEquals("/", "/");
    assertPathEquals("/foo", "/foo");
    assertPathEquals("/foo", "/", "foo");
    assertPathEquals("/foo/bar", "/foo/bar");
    assertPathEquals("/foo/bar", "/", "foo", "bar");
    assertPathEquals("/foo/bar", "/foo", "bar");
    assertPathEquals("/foo/bar", "/", "foo/bar");
    assertPathEquals("foo/bar/baz", "foo/bar/baz");
    assertPathEquals("foo/bar/baz", "foo", "bar", "baz");
    assertPathEquals("foo/bar/baz", "foo/bar", "baz");
    assertPathEquals("foo/bar/baz", "foo", "bar/baz");
  }

  @Test
  public void testPathParsing_withExtraSeparators() {
    assertPathEquals("/foo/bar", "///foo/bar");
    assertPathEquals("/foo/bar", "/foo///bar//");
    assertPathEquals("/foo/bar/baz", "/foo", "/bar", "baz/");
    //assertPathEquals("/foo/bar/baz", "/foo\\/bar//\\\\/baz\\/");
  }

  @Test
  public void testPathParsing_windowsStylePaths() throws IOException {
    PathService windowsPathService = PathServiceTest.fakeWindowsPathService();
    assertEquals("C:\\", pathService.parsePath("C:\\").toString());
    assertEquals("C:\\foo", windowsPathService.parsePath("C:\\foo").toString());
    assertEquals("C:\\foo", windowsPathService.parsePath("C:\\", "foo").toString());
    assertEquals("C:\\foo", windowsPathService.parsePath("C:", "\\foo").toString());
    assertEquals("C:\\foo", windowsPathService.parsePath("C:", "foo").toString());
    assertEquals("C:\\foo\\bar", windowsPathService.parsePath("C:", "foo/bar").toString());
  }

  @Test
  public void testParsing_windowsStylePaths_invalidPaths() {
    PathService windowsPathService = PathServiceTest.fakeWindowsPathService();

    try {
      // The actual windows implementation seems to allow "C:" but treat it as a *name*, not a root
      // despite the fact that a : is illegal except in a root... a : at any position other than
      // index 1 in the string will produce an exception.
      // Here, I choose to be more strict
      windowsPathService.parsePath("C:");
      fail();
    } catch (InvalidPathException expected) {
    }

    try {
      // "1:\" isn't a root because 1 isn't a letter
      windowsPathService.parsePath("1:\\foo");
      fail();
    } catch (InvalidPathException expected) {
    }

    try {
      // < and > are reserved characters
      windowsPathService.parsePath("foo<bar>");
      fail();
    } catch (InvalidPathException expected) {
    }
  }

  @Test
  public void testPathParsing_withAlternateSeparator() {
    // windows recognizes / as an alternate separator
    PathService windowsPathService = PathServiceTest.fakeWindowsPathService();
    assertEquals(
        windowsPathService.parsePath("foo\\bar\\baz"), windowsPathService.parsePath("foo/bar/baz"));
    assertEquals(
        windowsPathService.parsePath("C:\\foo\\bar"), windowsPathService.parsePath("C:\\foo/bar"));
    assertEquals(
        windowsPathService.parsePath("c:\\foo\\bar\\baz"),
        windowsPathService.parsePath("c:", "foo/", "bar/baz"));
  }

  @Test
  public void testRootPath() {
    new PathTester(pathService, "/").root("/").test("/");
  }

  @Test
  public void testRelativePath_singleName() {
    new PathTester(pathService, "test").names("test").test("test");

    Path path = pathService.parsePath("test");
    assertEquals(path, path.getFileName());
  }

  @Test
  public void testRelativePath_twoNames() {
    PathTester tester = new PathTester(pathService, "foo/bar").names("foo", "bar");

    tester.test("foo/bar");
  }

  @Test
  public void testRelativePath_fourNames() {
    new PathTester(pathService, "foo/bar/baz/test")
        .names("foo", "bar", "baz", "test").test("foo/bar/baz/test");
  }

  @Test
  public void testAbsolutePath_singleName() {
    new PathTester(pathService, "/foo")
        .root("/")
        .names("foo")
        .test("/foo");
  }

  @Test
  public void testAbsolutePath_twoNames() {
    new PathTester(pathService, "/foo/bar")
        .root("/")
        .names("foo", "bar")
        .test("/foo/bar");
  }

  @Test
  public void testAbsoluteMultiNamePath_fourNames() {
    new PathTester(pathService, "/foo/bar/baz/test")
        .root("/")
        .names("foo", "bar", "baz", "test")
        .test("/foo/bar/baz/test");
  }

  @Test
  public void testResolve_fromRoot() {
    Path root = pathService.parsePath("/");

    assertResolvedPathEquals("/foo", root, "foo");
    assertResolvedPathEquals("/foo/bar", root, "foo/bar");
    assertResolvedPathEquals("/foo/bar", root, "foo", "bar");
    assertResolvedPathEquals("/foo/bar/baz/test", root, "foo/bar/baz/test");
    assertResolvedPathEquals("/foo/bar/baz/test", root, "foo", "bar/baz", "test");
  }

  @Test
  public void testResolve_fromAbsolute() {
    Path path = pathService.parsePath("/foo");

    assertResolvedPathEquals("/foo/bar", path, "bar");
    assertResolvedPathEquals("/foo/bar/baz/test", path, "bar/baz/test");
    assertResolvedPathEquals("/foo/bar/baz/test", path, "bar/baz", "test");
    assertResolvedPathEquals("/foo/bar/baz/test", path, "bar", "baz", "test");
  }

  @Test
  public void testResolve_fromRelative() {
    Path path = pathService.parsePath("foo");

    assertResolvedPathEquals("foo/bar", path, "bar");
    assertResolvedPathEquals("foo/bar/baz/test", path, "bar/baz/test");
    assertResolvedPathEquals("foo/bar/baz/test", path, "bar", "baz", "test");
    assertResolvedPathEquals("foo/bar/baz/test", path, "bar/baz", "test");
  }

  @Test
  public void testResolve_withThisAndParentDirNames() {
    Path path = pathService.parsePath("/foo");

    assertResolvedPathEquals("/foo/bar/../baz", path, "bar/../baz");
    assertResolvedPathEquals("/foo/bar/../baz", path, "bar", "..", "baz");
    assertResolvedPathEquals("/foo/./bar/baz", path, "./bar/baz");
    assertResolvedPathEquals("/foo/./bar/baz", path, ".", "bar/baz");
  }

  @Test
  public void testResolve_givenAbsolutePath() {
    assertResolvedPathEquals("/test", pathService.parsePath("/foo"), "/test");
    assertResolvedPathEquals("/test", pathService.parsePath("foo"), "/test");
  }

  @Test
  public void testResolve_givenEmptyPath() {
    assertResolvedPathEquals("/foo", pathService.parsePath("/foo"), "");
    assertResolvedPathEquals("foo", pathService.parsePath("foo"), "");
  }

  @Test
  public void testResolve_againstEmptyPath() {
    assertResolvedPathEquals("foo/bar", pathService.emptyPath(), "foo/bar");
  }

  @Test
  public void testResolveSibling_givenEmptyPath() {
    Path path = pathService.parsePath("foo/bar");
    Path resolved = path.resolveSibling("");
    assertPathEquals("foo", resolved);

    path = pathService.parsePath("foo");
    resolved = path.resolveSibling("");
    assertPathEquals("", resolved);
  }

  @Test
  public void testResolveSibling_againstEmptyPath() {
    Path path = pathService.parsePath("");
    Path resolved = path.resolveSibling("foo");
    assertPathEquals("foo", resolved);

    path = pathService.parsePath("");
    resolved = path.resolveSibling("");
    assertPathEquals("", resolved);
  }

  @Test
  public void testRelativize_bothAbsolute() {
    // TODO(cgdecker): When the paths have different roots, how should this work?
    // Should it work at all?
    assertRelativizedPathEquals("b/c", pathService.parsePath("/a"), "/a/b/c");
    assertRelativizedPathEquals("c/d", pathService.parsePath("/a/b"), "/a/b/c/d");
  }

  @Test
  public void testRelativize_bothRelative() {
    assertRelativizedPathEquals("b/c", pathService.parsePath("a"), "a/b/c");
    assertRelativizedPathEquals("d", pathService.parsePath("a/b/c"), "a/b/c/d");
  }

  @Test
  public void testRelativize_againstEmptyPath() {
    assertRelativizedPathEquals("foo/bar", pathService.emptyPath(), "foo/bar");
  }

  @Test
  public void testRelativize_oneAbsoluteOneRelative() {
    try {
      pathService.parsePath("/foo/bar").relativize(pathService.parsePath("foo"));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      pathService.parsePath("foo").relativize(pathService.parsePath("/foo/bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testNormalize_withParentDirName() {
    assertNormalizedPathEquals("/foo/baz", "/foo/bar/../baz");
    assertNormalizedPathEquals("/foo/baz", "/foo", "bar", "..", "baz");
  }

  @Test
  public void testNormalize_withThisDirName() {
    assertNormalizedPathEquals("/foo/bar/baz", "/foo/bar/./baz");
    assertNormalizedPathEquals("/foo/bar/baz", "/foo", "bar", ".", "baz");
  }

  @Test
  public void testNormalize_withThisAndParentDirNames() {
    assertNormalizedPathEquals("foo/test", "foo/./bar/../././baz/../test");
  }

  @Test
  public void testNormalize_withLeadingParentDirNames() {
    assertNormalizedPathEquals("../../foo/baz", "../../foo/bar/../baz");
  }

  @Test
  public void testNormalize_withLeadingThisAndParentDirNames() {
    assertNormalizedPathEquals("../../foo/baz", "./.././.././foo/bar/../baz");
  }

  @Test
  public void testNormalize_withExtraParentDirNamesAtRoot() {
    assertNormalizedPathEquals("/", "/..");
    assertNormalizedPathEquals("/", "/../../..");
    assertNormalizedPathEquals("/", "/foo/../../..");
    assertNormalizedPathEquals("/", "/../foo/../../bar/baz/../../../..");
  }

  @Test
  public void testPathWithExtraSlashes() {
    assertPathEquals("/foo/bar/baz", pathService.parsePath("/foo/bar/baz/"));
    assertPathEquals("/foo/bar/baz", pathService.parsePath("/foo//bar///baz"));
    assertPathEquals("/foo/bar/baz", pathService.parsePath("///foo/bar/baz"));
  }

  @Test
  public void testEqualityBasedOnStringNotName() {
    Name a1 = Name.create("a", "a");
    Name a2 = Name.create("A", "a");
    Name a3 = Name.create("a", "A");

    Path path1 = pathService.createFileName(a1);
    Path path2 = pathService.createFileName(a2);
    Path path3 = pathService.createFileName(a3);

    new EqualsTester()
        .addEqualityGroup(path1, path3)
        .addEqualityGroup(path2)
        .testEquals();
  }

  @Test
  public void testNullPointerExceptions() throws NoSuchMethodException {
    NullPointerTester tester =
        new NullPointerTester().ignore(JimfsPath.class.getMethod("toRealPath", LinkOption[].class));
    // ignore toRealPath because the pathService creates fake paths that do not have a
    // JimfsFileSystem instance, causing it to fail since it needs to access the file system

    tester.testAllPublicInstanceMethods(pathService.parsePath("/"));
    tester.testAllPublicInstanceMethods(pathService.parsePath(""));
    tester.testAllPublicInstanceMethods(pathService.parsePath("/foo"));
    tester.testAllPublicInstanceMethods(pathService.parsePath("/foo/bar/baz"));
    tester.testAllPublicInstanceMethods(pathService.parsePath("foo"));
    tester.testAllPublicInstanceMethods(pathService.parsePath("foo/bar"));
    tester.testAllPublicInstanceMethods(pathService.parsePath("foo/bar/baz"));
    tester.testAllPublicInstanceMethods(pathService.parsePath("."));
    tester.testAllPublicInstanceMethods(pathService.parsePath(".."));
  }

  private void assertResolvedPathEquals(
      String expected, Path path, String firstResolvePath, String... moreResolvePaths) {
    Path resolved = path.resolve(firstResolvePath);
    for (String additionalPath : moreResolvePaths) {
      resolved = resolved.resolve(additionalPath);
    }
    assertPathEquals(expected, resolved);

    Path relative = pathService.parsePath(firstResolvePath, moreResolvePaths);
    resolved = path.resolve(relative);
    assertPathEquals(expected, resolved);

    // assert the invariant that p.relativize(p.resolve(q)).equals(q) when q does not have a root
    // p = path, q = relative, p.resolve(q) = resolved
    if (relative.getRoot() == null) {
      assertEquals(relative, path.relativize(resolved));
    }
  }

  private void assertRelativizedPathEquals(String expected, Path path, String relativizePath) {
    Path relativized = path.relativize(pathService.parsePath(relativizePath));
    assertPathEquals(expected, relativized);
  }

  private void assertNormalizedPathEquals(String expected, String first, String... more) {
    assertPathEquals(expected, pathService.parsePath(first, more).normalize());
  }

  private void assertPathEquals(String expected, String first, String... more) {
    assertPathEquals(expected, pathService.parsePath(first, more));
  }

  private void assertPathEquals(String expected, Path path) {
    assertEquals(pathService.parsePath(expected), path);
  }
}
