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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.Iterables;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.jimfs.Jimfs;
import com.google.jimfs.WindowsConfiguration;
import com.google.jimfs.internal.JimfsFileSystem;
import com.google.jimfs.internal.JimfsFileSystemProvider;
import com.google.jimfs.testing.PathTester;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * @author Colin Decker
 */
public class JimfsPathTest {

  private JimfsFileSystem fs;

  @Before
  public void setUp() throws IOException {
    fs = (JimfsFileSystem) Jimfs.newUnixLikeFileSystem();
  }

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
  public void testPathParsing_withAlternateSeparator() {
    // TODO(cgdecker): Unix-style paths don't recognize \ as a separator, right?
    /*assertPathEquals("/foo/bar", "/foo\\bar");
    assertPathEquals("foo/bar/baz", "foo/bar\\baz");
    assertPathEquals("foo/bar/baz", "foo\\bar", "baz");
    assertPathEquals("/foo/bar/baz", "/foo\\bar\\baz");*/
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
    URI uri = URI.create("jimfs://foo");
    fs = new JimfsFileSystem(
        new JimfsFileSystemProvider(), uri, new WindowsConfiguration(new String[0]));
    assertEquals("C:", fs.getPath("C:").toString());
    // TODO(cgdecker): the windows implementation keeps the root in whatever format you give it
    // should try to support that while still having lookup work...
    // assertEquals("C:\\", fs.getPath("C:\\").toString());
    assertEquals("C:\\foo", fs.getPath("C:\\foo").toString());
    assertEquals("C:\\foo", fs.getPath("C:\\", "foo").toString());
    assertEquals("C:\\foo", fs.getPath("C:", "\\foo").toString());
    assertEquals("C:\\foo", fs.getPath("C:", "foo").toString());
    assertEquals("C:\\foo\\bar", fs.getPath("C:", "foo/bar").toString());
  }

  @Test
  public void testRootPath() {
    new PathTester(fs, "/")
        .root("/")
        .test("/");
  }

  @Test
  public void testRelativePath_singleName() {
    new PathTester(fs, "test")
        .names("test")
        .test("test");

    Path path = fs.getPath("test");
    assertEquals(path, path.getFileName());
  }

  @Test
  public void testRelativePath_twoNames() {
    PathTester tester = new PathTester(fs, "foo/bar")
        .names("foo", "bar");

    tester.test("foo/bar");
  }

  @Test
  public void testRelativePath_fourNames() {
    PathTester tester = new PathTester(fs, "foo/bar/baz/test")
        .names("foo", "bar", "baz", "test");

    tester.test("foo/bar/baz/test");
  }

  @Test
  public void testAbsolutePath_singleName() {
    PathTester tester = new PathTester(fs, "/foo")
        .root("/")
        .names("foo");

    tester.test("/foo");
  }

  @Test
  public void testAbsolutePath_twoNames() {
    PathTester tester = new PathTester(fs, "/foo/bar")
        .root("/")
        .names("foo", "bar");

    tester.test("/foo/bar");
  }

  @Test
  public void testAbsoluteMultiNamePath_fourNames() {
    PathTester tester = new PathTester(fs, "/foo/bar/baz/test")
        .root("/")
        .names("foo", "bar", "baz", "test");

    tester.test("/foo/bar/baz/test");
  }

  @Test
  public void testResolve_fromRoot() {
    Path root = Iterables.getOnlyElement(fs.getRootDirectories());

    assertResolvedPathEquals("/foo", root, "foo");
    assertResolvedPathEquals("/foo/bar", root, "foo/bar");
    assertResolvedPathEquals("/foo/bar", root, "foo", "bar");
    assertResolvedPathEquals("/foo/bar/baz/test", root, "foo/bar/baz/test");
    assertResolvedPathEquals("/foo/bar/baz/test", root, "foo", "bar/baz", "test");
  }

  @Test
  public void testResolve_fromAbsolute() {
    Path path = fs.getPath("/foo");

    assertResolvedPathEquals("/foo/bar", path, "bar");
    assertResolvedPathEquals("/foo/bar/baz/test", path, "bar/baz/test");
    assertResolvedPathEquals("/foo/bar/baz/test", path, "bar/baz", "test");
    assertResolvedPathEquals("/foo/bar/baz/test", path, "bar", "baz", "test");
  }

  @Test
  public void testResolve_fromRelative() {
    Path path = fs.getPath("foo");

    assertResolvedPathEquals("foo/bar", path, "bar");
    assertResolvedPathEquals("foo/bar/baz/test", path, "bar/baz/test");
    assertResolvedPathEquals("foo/bar/baz/test", path, "bar", "baz", "test");
    assertResolvedPathEquals("foo/bar/baz/test", path, "bar/baz", "test");
  }

  @Test
  public void testResolve_withThisAndParentDirNames() {
    Path path = fs.getPath("/foo");

    assertResolvedPathEquals("/foo/bar/../baz", path, "bar/../baz");
    assertResolvedPathEquals("/foo/bar/../baz", path, "bar", "..", "baz");
    assertResolvedPathEquals("/foo/./bar/baz", path, "./bar/baz");
    assertResolvedPathEquals("/foo/./bar/baz", path, ".", "bar/baz");
  }

  @Test
  public void testResolve_givenAbsolutePath() {
    assertResolvedPathEquals("/test", fs.getPath("/foo"), "/test");
    assertResolvedPathEquals("/test", fs.getPath("foo"), "/test");
  }

  @Test
  public void testResolve_givenEmptyPath() {
    assertResolvedPathEquals("/foo", fs.getPath("/foo"), "");
    assertResolvedPathEquals("foo", fs.getPath("foo"), "");
  }

  @Test
  public void testRelativize_bothAbsolute() {
    // TODO(cgdecker): When the paths have different roots, how should this work?
    // Should it work at all?
    assertRelativizedPathEquals("b/c", fs.getPath("/a"), "/a/b/c");
    assertRelativizedPathEquals("c/d", fs.getPath("/a/b"), "/a/b/c/d");
  }

  @Test
  public void testRelativize_bothRelative() {
    assertRelativizedPathEquals("b/c", fs.getPath("a"), "a/b/c");
    assertRelativizedPathEquals("d", fs.getPath("a/b/c"), "a/b/c/d");
  }

  @Test
  public void testRelativize_oneAbsoluteOneRelative() {
    try {
      fs.getPath("/foo/bar").relativize(fs.getPath("foo"));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      fs.getPath("foo").relativize(fs.getPath("/foo/bar"));
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
    assertPathEquals("/foo/bar/baz", fs.getPath("/foo/bar/baz/"));
    assertPathEquals("/foo/bar/baz", fs.getPath("/foo//bar///baz"));
    assertPathEquals("/foo/bar/baz", fs.getPath("///foo/bar/baz"));
  }

  @Test
  public void testEqualityBasedOnStringNotName() {
    Name a1 = Name.create("a", "a");
    Name a2 = Name.create("A", "a");
    Name a3 = Name.create("a", "A");

    Path path1 = JimfsPath.name(fs, a1);
    Path path2 = JimfsPath.name(fs, a2);
    Path path3 = JimfsPath.name(fs, a3);

    new EqualsTester()
        .addEqualityGroup(path1, path3)
        .addEqualityGroup(path2)
        .testEquals();
  }

  @Test
  public void testNullPointerExceptions() {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicInstanceMethods(fs.getPath("/"));
    tester.testAllPublicInstanceMethods(fs.getPath(""));
    tester.testAllPublicInstanceMethods(fs.getPath("/foo"));
    tester.testAllPublicInstanceMethods(fs.getPath("/foo/bar/baz"));
    tester.testAllPublicInstanceMethods(fs.getPath("foo"));
    tester.testAllPublicInstanceMethods(fs.getPath("foo/bar"));
    tester.testAllPublicInstanceMethods(fs.getPath("foo/bar/baz"));
    tester.testAllPublicInstanceMethods(fs.getPath("."));
    tester.testAllPublicInstanceMethods(fs.getPath(".."));
  }

  private void assertResolvedPathEquals(String expected, Path path, String firstResolvePath,
      String... moreResolvePaths) {
    Path resolved = path.resolve(firstResolvePath);
    for (String additionalPath : moreResolvePaths) {
      resolved = resolved.resolve(additionalPath);
    }
    assertPathEquals(expected, resolved);

    Path relative = fs.getPath(firstResolvePath, moreResolvePaths);
    resolved = path.resolve(relative);
    assertPathEquals(expected, resolved);

    // assert the invariant that p.relativize(p.resolve(q)).equals(q) when q does not have a root
    // p = path, q = relative, p.resolve(q) = resolved
    if (relative.getRoot() == null) {
      assertEquals(relative, path.relativize(resolved));
    }
  }

  private void assertRelativizedPathEquals(String expected, Path path, String relativizePath) {
    Path relativized = path.relativize(fs.getPath(relativizePath));
    assertPathEquals(expected, relativized);
  }

  private void assertNormalizedPathEquals(String expected, String first, String... more) {
    assertPathEquals(expected, fs.getPath(first, more).normalize());
  }

  private void assertPathEquals(String expected, String first, String... more) {
    assertPathEquals(expected, fs.getPath(first, more));
  }

  private void assertPathEquals(String expected, Path path) {
    assertEquals(fs.getPath(expected), path);
  }
}
