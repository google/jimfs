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
import static com.google.common.jimfs.PathType.ParseResult;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.URI;

import javax.annotation.Nullable;

/**
 * Tests for {@link PathType}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class PathTypeTest {

  private static final FakePathType type = new FakePathType();
  static final URI fileSystemUri = URI.create("jimfs://foo");

  @Test
  public void testBasicProperties() {
    assertThat(type.getSeparator()).isEqualTo("/");
    assertThat(type.getOtherSeparators()).isEqualTo("\\");
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
    assertThat(type.toString(path.root(), path.names())).isEqualTo("foo/bar/baz");

    ParseResult path2 = type.parsePath("$/foo/bar");
    assertThat(type.toString(path2.root(), path2.names())).isEqualTo("$foo/bar");
  }

  @Test
  public void testToUri() {
    URI fileUri = type.toUri(fileSystemUri, "$", ImmutableList.of("foo", "bar"), false);
    assertThat(fileUri.toString()).isEqualTo("jimfs://foo/$/foo/bar");
    assertThat(fileUri.getPath()).isEqualTo("/$/foo/bar");

    URI directoryUri = type.toUri(fileSystemUri, "$", ImmutableList.of("foo", "bar"), true);
    assertThat(directoryUri.toString()).isEqualTo("jimfs://foo/$/foo/bar/");
    assertThat(directoryUri.getPath()).isEqualTo("/$/foo/bar/");

    URI rootUri = type.toUri(fileSystemUri, "$", ImmutableList.<String>of(), true);
    assertThat(rootUri.toString()).isEqualTo("jimfs://foo/$/");
    assertThat(rootUri.getPath()).isEqualTo("/$/");
  }

  @Test
  public void testToUri_escaping() {
    URI fileUri = type.toUri(fileSystemUri, "$", ImmutableList.of("foo", "bar baz"), false);
    assertThat(fileUri.toString()).isEqualTo("jimfs://foo/$/foo/bar%20baz");
    assertThat(fileUri.getRawPath()).isEqualTo("/$/foo/bar%20baz");
    assertThat(fileUri.getPath()).isEqualTo("/$/foo/bar baz");
  }

  @Test
  public void testUriRoundTrips() {
    assertUriRoundTripsCorrectly(type, "$");
    assertUriRoundTripsCorrectly(type, "$foo");
    assertUriRoundTripsCorrectly(type, "$foo/bar/baz");
    assertUriRoundTripsCorrectly(type, "$foo bar");
    assertUriRoundTripsCorrectly(type, "$foo/bar baz");
  }

  static void assertParseResult(ParseResult result, @Nullable String root, String... names) {
    assertThat(result.root()).isEqualTo(root);
    assertThat(result.names()).containsExactly((Object[]) names).inOrder();
  }

  static void assertUriRoundTripsCorrectly(PathType type, String path) {
    ParseResult result = type.parsePath(path);
    URI uri = type.toUri(fileSystemUri, result.root(), result.names(), false);
    ParseResult parsedUri = type.fromUri(uri);
    assertThat(parsedUri.root()).isEqualTo(result.root());
    assertThat(parsedUri.names()).containsExactlyElementsIn(result.names()).inOrder();
  }

  /**
   * Arbitrary path type with $ as the root, / as the separator and \ as an alternate separator.
   */
  private static final class FakePathType extends PathType {

    protected FakePathType() {
      super(false, '/', '\\');
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

    @Override
    public String toUriPath(String root, Iterable<String> names, boolean directory) {
      StringBuilder builder = new StringBuilder();
      builder.append('/').append(root);
      for (String name : names) {
        builder.append('/').append(name);
      }
      if (directory) {
        builder.append('/');
      }
      return builder.toString();
    }

    @Override
    public ParseResult parseUriPath(String uriPath) {
      checkArgument(uriPath.startsWith("/$"), "uriPath (%s) must start with /$", uriPath);
      return parsePath(uriPath.substring(1));
    }
  }
}
