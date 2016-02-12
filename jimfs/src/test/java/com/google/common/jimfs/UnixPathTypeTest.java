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

import static com.google.common.jimfs.PathTypeTest.assertParseResult;
import static com.google.common.jimfs.PathTypeTest.assertUriRoundTripsCorrectly;
import static com.google.common.jimfs.PathTypeTest.fileSystemUri;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URI;
import java.nio.file.InvalidPathException;

/**
 * Tests for {@link UnixPathType}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class UnixPathTypeTest {

  @Test
  public void testUnix() {
    PathType unix = PathType.unix();
    assertThat(unix.getSeparator()).isEqualTo("/");
    assertThat(unix.getOtherSeparators()).isEqualTo("");

    // "//foo/bar" is what will be passed to parsePath if "/", "foo", "bar" is passed to getPath
    PathType.ParseResult path = unix.parsePath("//foo/bar");
    assertParseResult(path, "/", "foo", "bar");
    assertThat(unix.toString(path.root(), path.names())).isEqualTo("/foo/bar");

    PathType.ParseResult path2 = unix.parsePath("foo/bar/");
    assertParseResult(path2, null, "foo", "bar");
    assertThat(unix.toString(path2.root(), path2.names())).isEqualTo("foo/bar");
  }

  @Test
  public void testUnix_toUri() {
    URI fileUri = PathType.unix().toUri(fileSystemUri, "/", ImmutableList.of("foo", "bar"), false);
    assertThat(fileUri.toString()).isEqualTo("jimfs://foo/foo/bar");
    assertThat(fileUri.getPath()).isEqualTo("/foo/bar");

    URI directoryUri =
        PathType.unix().toUri(fileSystemUri, "/", ImmutableList.of("foo", "bar"), true);
    assertThat(directoryUri.toString()).isEqualTo("jimfs://foo/foo/bar/");
    assertThat(directoryUri.getPath()).isEqualTo("/foo/bar/");

    URI rootUri = PathType.unix().toUri(fileSystemUri, "/", ImmutableList.<String>of(), true);
    assertThat(rootUri.toString()).isEqualTo("jimfs://foo/");
    assertThat(rootUri.getPath()).isEqualTo("/");
  }

  @Test
  public void testUnix_toUri_escaping() {
    URI uri = PathType.unix().toUri(fileSystemUri, "/", ImmutableList.of("foo bar"), false);
    assertThat(uri.toString()).isEqualTo("jimfs://foo/foo%20bar");
    assertThat(uri.getRawPath()).isEqualTo("/foo%20bar");
    assertThat(uri.getPath()).isEqualTo("/foo bar");
  }

  @Test
  public void testUnix_uriRoundTrips() {
    assertUriRoundTripsCorrectly(PathType.unix(), "/");
    assertUriRoundTripsCorrectly(PathType.unix(), "/foo");
    assertUriRoundTripsCorrectly(PathType.unix(), "/foo/bar/baz");
    assertUriRoundTripsCorrectly(PathType.unix(), "/foo/bar baz/one/two");
    assertUriRoundTripsCorrectly(PathType.unix(), "/foo bar");
    assertUriRoundTripsCorrectly(PathType.unix(), "/foo bar/");
    assertUriRoundTripsCorrectly(PathType.unix(), "/foo bar/baz/one");
  }

  @Test
  public void testUnix_illegalCharacters() {
    try {
      PathType.unix().parsePath("/foo/bar\0");
      fail();
    } catch (InvalidPathException expected) {
      assertEquals(8, expected.getIndex());
    }

    try {
      PathType.unix().parsePath("/\u00001/foo");
      fail();
    } catch (InvalidPathException expected) {
      assertEquals(1, expected.getIndex());
    }
  }
}
