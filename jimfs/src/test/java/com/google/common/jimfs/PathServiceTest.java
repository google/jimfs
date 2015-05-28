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

import static com.google.common.jimfs.PathSubject.paths;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;

/**
 * Tests for {@link PathService}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class PathServiceTest {

  private static final ImmutableSet<PathNormalization> NO_NORMALIZATIONS = ImmutableSet.of();

  private final PathService service = fakeUnixPathService();

  @Test
  public void testBasicProperties() {
    assertThat(service.getSeparator()).isEqualTo("/");
    assertThat(fakeWindowsPathService().getSeparator()).isEqualTo("\\");
  }

  @Test
  public void testPathCreation() {
    assertAbout(paths()).that(service.emptyPath())
        .hasRootComponent(null)
        .and().hasNameComponents("");

    assertAbout(paths()).that(service.createRoot(service.name("/")))
        .isAbsolute()
        .and().hasRootComponent("/")
        .and().hasNoNameComponents();

    assertAbout(paths()).that(service.createFileName(service.name("foo")))
        .hasRootComponent(null)
        .and().hasNameComponents("foo");

    JimfsPath relative = service.createRelativePath(service.names(ImmutableList.of("foo", "bar")));
    assertAbout(paths()).that(relative)
        .hasRootComponent(null)
        .and().hasNameComponents("foo", "bar");

    JimfsPath absolute =
        service.createPath(service.name("/"), service.names(ImmutableList.of("foo", "bar")));
    assertAbout(paths()).that(absolute)
        .isAbsolute()
        .and().hasRootComponent("/")
        .and().hasNameComponents("foo", "bar");
  }

  @Test
  public void testPathCreation_emptyPath() {
    // normalized to empty path with single empty string name
    assertAbout(paths()).that(service.createPath(null, ImmutableList.<Name>of()))
        .hasRootComponent(null)
        .and().hasNameComponents("");
  }

  @Test
  public void testPathCreation_parseIgnoresEmptyString() {
    // if the empty string wasn't ignored, the resulting path would be "/foo" since the empty
    // string would be joined with foo
    assertAbout(paths()).that(service.parsePath("", "foo"))
        .hasRootComponent(null)
        .and().hasNameComponents("foo");
  }

  @Test
  public void testToString() {
    // not much to test for this since it just delegates to PathType anyway
    JimfsPath path =
        new JimfsPath(service, null, ImmutableList.of(Name.simple("foo"), Name.simple("bar")));
    assertThat(service.toString(path)).isEqualTo("foo/bar");

    path = new JimfsPath(service, Name.simple("/"), ImmutableList.of(Name.simple("foo")));
    assertThat(service.toString(path)).isEqualTo("/foo");
  }

  @Test
  public void testHash_usingDisplayForm() {
    PathService pathService = fakePathService(PathType.unix(), false);

    JimfsPath path1 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("FOO", "foo")));
    JimfsPath path2 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("FOO", "FOO")));
    JimfsPath path3 =
        new JimfsPath(
            pathService, null, ImmutableList.of(Name.create("FOO", "9874238974897189741")));

    assertThat(pathService.hash(path1)).isEqualTo(pathService.hash(path2));
    assertThat(pathService.hash(path2)).isEqualTo(pathService.hash(path3));
  }

  @Test
  public void testHash_usingCanonicalForm() {
    PathService pathService = fakePathService(PathType.unix(), true);

    JimfsPath path1 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("foo", "foo")));
    JimfsPath path2 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("FOO", "foo")));
    JimfsPath path3 =
        new JimfsPath(
            pathService, null, ImmutableList.of(Name.create("28937497189478912374897", "foo")));

    assertThat(pathService.hash(path1)).isEqualTo(pathService.hash(path2));
    assertThat(pathService.hash(path2)).isEqualTo(pathService.hash(path3));
  }

  @Test
  public void testCompareTo_usingDisplayForm() {
    PathService pathService = fakePathService(PathType.unix(), false);

    JimfsPath path1 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("a", "z")));
    JimfsPath path2 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("b", "y")));
    JimfsPath path3 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("c", "x")));

    assertThat(pathService.compare(path1, path2)).isEqualTo(-1);
    assertThat(pathService.compare(path2, path3)).isEqualTo(-1);
  }

  @Test
  public void testCompareTo_usingCanonicalForm() {
    PathService pathService = fakePathService(PathType.unix(), true);

    JimfsPath path1 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("a", "z")));
    JimfsPath path2 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("b", "y")));
    JimfsPath path3 = new JimfsPath(pathService, null, ImmutableList.of(Name.create("c", "x")));

    assertThat(pathService.compare(path1, path2)).isEqualTo(1);
    assertThat(pathService.compare(path2, path3)).isEqualTo(1);
  }

  @Test
  public void testPathMatcher() {
    assertThat(service.createPathMatcher("regex:foo"))
        .isInstanceOf(PathMatchers.RegexPathMatcher.class);
    assertThat(service.createPathMatcher("glob:foo"))
        .isInstanceOf(PathMatchers.RegexPathMatcher.class);
  }

  public static PathService fakeUnixPathService() {
    return fakePathService(PathType.unix(), false);
  }

  public static PathService fakeWindowsPathService() {
    return fakePathService(PathType.windows(), false);
  }

  public static PathService fakePathService(PathType type, boolean equalityUsesCanonicalForm) {
    PathService service =
        new PathService(type, NO_NORMALIZATIONS, NO_NORMALIZATIONS, equalityUsesCanonicalForm);
    service.setFileSystem(FILE_SYSTEM);
    return service;
  }

  private static final FileSystem FILE_SYSTEM;

  static {
    try {
      FILE_SYSTEM =
          JimfsFileSystems.newFileSystem(
              new JimfsFileSystemProvider(), URI.create("jimfs://foo"), Configuration.unix());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
