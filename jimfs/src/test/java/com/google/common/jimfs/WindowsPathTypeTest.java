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

import static com.google.common.jimfs.PathType.windows;
import static com.google.common.jimfs.PathTypeTest.assertParseResult;
import static com.google.common.jimfs.PathTypeTest.assertUriRoundTripsCorrectly;
import static com.google.common.jimfs.PathTypeTest.fileSystemUri;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URI;
import java.nio.file.InvalidPathException;

/**
 * Tests for {@link WindowsPathType}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class WindowsPathTypeTest {

  @Test
  public void testWindows() {
    PathType windows = PathType.windows();
    assertThat(windows.getSeparator()).isEqualTo("\\");
    assertThat(windows.getOtherSeparators()).isEqualTo("/");

    // "C:\\foo\bar" results from "C:\", "foo", "bar" passed to getPath
    PathType.ParseResult path = windows.parsePath("C:\\\\foo\\bar");
    assertParseResult(path, "C:\\", "foo", "bar");
    assertThat(windows.toString(path.root(), path.names())).isEqualTo("C:\\foo\\bar");

    PathType.ParseResult path2 = windows.parsePath("foo/bar/");
    assertParseResult(path2, null, "foo", "bar");
    assertThat(windows.toString(path2.root(), path2.names())).isEqualTo("foo\\bar");

    PathType.ParseResult path3 = windows.parsePath("hello world/foo/bar");
    assertParseResult(path3, null, "hello world", "foo", "bar");
    assertThat(windows.toString(null, path3.names())).isEqualTo("hello world\\foo\\bar");
  }

  @Test
  public void testWindows_relativePathsWithDriveRoot_unsupported() {
    try {
      windows().parsePath("C:");
      fail();
    } catch (InvalidPathException expected) {
    }

    try {
      windows().parsePath("C:foo\\bar");
      fail();
    } catch (InvalidPathException expected) {
    }
  }

  @Test
  public void testWindows_absolutePathOnCurrentDrive_unsupported() {
    try {
      windows().parsePath("\\foo\\bar");
      fail();
    } catch (InvalidPathException expected) {
    }

    try {
      windows().parsePath("\\");
      fail();
    } catch (InvalidPathException expected) {
    }
  }

  @Test
  public void testWindows_uncPaths() {
    PathType windows = PathType.windows();
    PathType.ParseResult path = windows.parsePath("\\\\host\\share");
    assertParseResult(path, "\\\\host\\share\\");

    path = windows.parsePath("\\\\HOST\\share\\foo\\bar");
    assertParseResult(path, "\\\\HOST\\share\\", "foo", "bar");

    try {
      windows.parsePath("\\\\");
      fail();
    } catch (InvalidPathException expected) {
      assertThat(expected.getInput()).isEqualTo("\\\\");
      assertThat(expected.getReason()).isEqualTo("UNC path is missing hostname");
    }

    try {
      windows.parsePath("\\\\host");
      fail();
    } catch (InvalidPathException expected) {
      assertThat(expected.getInput()).isEqualTo("\\\\host");
      assertThat(expected.getReason()).isEqualTo("UNC path is missing sharename");
    }

    try {
      windows.parsePath("\\\\host\\");
      fail();
    } catch (InvalidPathException expected) {
      assertThat(expected.getInput()).isEqualTo("\\\\host\\");
      assertThat(expected.getReason()).isEqualTo("UNC path is missing sharename");
    }

    try {
      windows.parsePath("//host");
      fail();
    } catch (InvalidPathException expected) {
      assertThat(expected.getInput()).isEqualTo("//host");
      assertThat(expected.getReason()).isEqualTo("UNC path is missing sharename");
    }
  }

  @Test
  public void testWindows_illegalNames() {
    try {
      windows().parsePath("foo<bar");
      fail();
    } catch (InvalidPathException expected) {
    }

    try {
      windows().parsePath("foo?");
      fail();
    } catch (InvalidPathException expected) {
    }

    try {
      windows().parsePath("foo ");
      fail();
    } catch (InvalidPathException expected) {
    }

    try {
      windows().parsePath("foo \\bar");
      fail();
    } catch (InvalidPathException expected) {
    }
  }

  @Test
  public void testWindows_toUri_normal() {
    URI fileUri =
        PathType.windows().toUri(fileSystemUri, "C:\\", ImmutableList.of("foo", "bar"), false);
    assertThat(fileUri.toString()).isEqualTo("jimfs://foo/C:/foo/bar");
    assertThat(fileUri.getPath()).isEqualTo("/C:/foo/bar");

    URI directoryUri =
        PathType.windows().toUri(fileSystemUri, "C:\\", ImmutableList.of("foo", "bar"), true);
    assertThat(directoryUri.toString()).isEqualTo("jimfs://foo/C:/foo/bar/");
    assertThat(directoryUri.getPath()).isEqualTo("/C:/foo/bar/");

    URI rootUri = PathType.windows().toUri(fileSystemUri, "C:\\", ImmutableList.<String>of(), true);
    assertThat(rootUri.toString()).isEqualTo("jimfs://foo/C:/");
    assertThat(rootUri.getPath()).isEqualTo("/C:/");
  }

  @Test
  public void testWindows_toUri_unc() {
    URI fileUri =
        PathType.windows()
            .toUri(fileSystemUri, "\\\\host\\share\\", ImmutableList.of("foo", "bar"), false);
    assertThat(fileUri.toString()).isEqualTo("jimfs://foo//host/share/foo/bar");
    assertThat(fileUri.getPath()).isEqualTo("//host/share/foo/bar");

    URI rootUri =
        PathType.windows()
            .toUri(fileSystemUri, "\\\\host\\share\\", ImmutableList.<String>of(), true);
    assertThat(rootUri.toString()).isEqualTo("jimfs://foo//host/share/");
    assertThat(rootUri.getPath()).isEqualTo("//host/share/");
  }

  @Test
  public void testWindows_toUri_escaping() {
    URI uri =
        PathType.windows()
            .toUri(fileSystemUri, "C:\\", ImmutableList.of("Users", "foo", "My Documents"), true);
    assertThat(uri.toString()).isEqualTo("jimfs://foo/C:/Users/foo/My%20Documents/");
    assertThat(uri.getRawPath()).isEqualTo("/C:/Users/foo/My%20Documents/");
    assertThat(uri.getPath()).isEqualTo("/C:/Users/foo/My Documents/");
  }

  @Test
  public void testWindows_uriRoundTrips_normal() {
    assertUriRoundTripsCorrectly(PathType.windows(), "C:\\");
    assertUriRoundTripsCorrectly(PathType.windows(), "C:\\foo");
    assertUriRoundTripsCorrectly(PathType.windows(), "C:\\foo\\bar\\baz");
    assertUriRoundTripsCorrectly(PathType.windows(), "C:\\Users\\foo\\My Documents\\");
    assertUriRoundTripsCorrectly(PathType.windows(), "C:\\foo bar");
    assertUriRoundTripsCorrectly(PathType.windows(), "C:\\foo bar\\baz");
  }

  @Test
  public void testWindows_uriRoundTrips_unc() {
    assertUriRoundTripsCorrectly(PathType.windows(), "\\\\host\\share");
    assertUriRoundTripsCorrectly(PathType.windows(), "\\\\host\\share\\");
    assertUriRoundTripsCorrectly(PathType.windows(), "\\\\host\\share\\foo");
    assertUriRoundTripsCorrectly(PathType.windows(), "\\\\host\\share\\foo\\bar\\baz");
    assertUriRoundTripsCorrectly(PathType.windows(), "\\\\host\\share\\Users\\foo\\My Documents\\");
    assertUriRoundTripsCorrectly(PathType.windows(), "\\\\host\\share\\foo bar");
    assertUriRoundTripsCorrectly(PathType.windows(), "\\\\host\\share\\foo bar\\baz");
  }
}
