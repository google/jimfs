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

package com.google.jimfs;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.internal.JimfsFileSystemProvider;

import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.PatternSyntaxException;

/**
 * Tests a Windows-like file system through the public methods in {@link Files}.
 *
 * @author Colin Decker
 */
public class JimfsWindowsLikeIntegrationTest extends AbstractJimfsIntegrationTest {

  @Override
  protected FileSystem createFileSystem() {
    return Jimfs.newWindowsLikeConfiguration()
        .setName("win")
        .addRoots("E:\\")
        .setAttributeViews(AttributeViews.windows())
        .createFileSystem();
  }

  @Test
  public void testFileSystem() {
    ASSERT.that(fs.getSeparator()).is("\\");
    ASSERT.that(fs.getRootDirectories()).iteratesAs(ImmutableSet.of(path("C:\\"), path("E:\\")));
    ASSERT.that(fs.isOpen()).isTrue();
    ASSERT.that(fs.isReadOnly()).isFalse();
    ASSERT.that(fs.supportedFileAttributeViews())
        .has().exactly("basic", "owner", "dos", "acl", "user");
    ASSERT.that(fs.provider()).isA(JimfsFileSystemProvider.class);
  }

  @Test
  public void testPaths() {
    assertThat("C:\\").isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNoNameComponents();
    assertThat("foo").isRelative()
        .and().hasNameComponents("foo");
    assertThat("foo\\bar").isRelative()
        .and().hasNameComponents("foo", "bar");
    assertThat("C:\\foo\\bar\\baz").isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar", "baz");
  }

  @Test
  public void testPaths_withSlash() {
    assertThat("foo/bar").isRelative()
        .and().hasNameComponents("foo", "bar")
        .and().is(path("foo\\bar"));
    assertThat("C:/foo/bar/baz").isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar", "baz")
        .and().is(path("C:\\foo\\bar\\baz"));
    assertThat("C:/foo\\bar/baz").isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar", "baz")
        .and().is(path("C:\\foo\\bar\\baz"));
  }

  @Test
  public void testPaths_resolve() {
    assertThat(path("C:\\").resolve("foo\\bar")).isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar");
    assertThat(path("foo\\bar").resolveSibling("baz")).isRelative()
        .and().hasNameComponents("foo", "baz");
    assertThat(path("foo\\bar").resolve("C:\\one\\two")).isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("one", "two");
  }

  @Test
  public void testPaths_normalize() {
    assertThat(path("foo\\bar\\..").normalize()).isRelative()
        .and().hasNameComponents("foo");
    assertThat(path("foo\\.\\bar\\..\\baz\\test\\.\\..\\stuff").normalize()).isRelative()
        .and().hasNameComponents("foo", "baz", "stuff");
    assertThat(path("..\\..\\foo\\.\\bar").normalize()).isRelative()
        .and().hasNameComponents("..", "..", "foo", "bar");
    assertThat(path("foo\\..\\..\\bar").normalize()).isRelative()
        .and().hasNameComponents("..", "bar");
    assertThat(path("..\\.\\..").normalize()).isRelative()
        .and().hasNameComponents("..", "..");
  }

  @Test
  public void testPaths_relativize() {
    assertThat(path("C:\\foo\\bar").relativize(path("C:\\foo\\bar\\baz"))).isRelative()
        .and().hasNameComponents("baz");
    assertThat(path("C:\\foo\\bar\\baz").relativize(path("C:\\foo\\bar"))).isRelative()
        .and().hasNameComponents("..");
    assertThat(path("C:\\foo\\bar\\baz").relativize(path("C:\\foo\\baz\\bar"))).isRelative()
        .and().hasNameComponents("..", "..", "baz", "bar");
    assertThat(path("foo\\bar").relativize(path("foo"))).isRelative()
        .and().hasNameComponents("..");
    assertThat(path("foo").relativize(path("foo\\bar"))).isRelative()
        .and().hasNameComponents("bar");

    try {
      path("C:\\foo\\bar").relativize(path("bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      path("bar").relativize(path("C:\\foo\\bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testPaths_startsWith_endsWith() {
    ASSERT.that(path("C:\\foo\\bar").startsWith("C:\\")).isTrue();
    ASSERT.that(path("C:\\foo\\bar").startsWith("C:\\foo")).isTrue();
    ASSERT.that(path("C:\\foo\\bar").startsWith("C:\\foo\\bar")).isTrue();
    ASSERT.that(path("C:\\foo\\bar").endsWith("bar")).isTrue();
    ASSERT.that(path("C:\\foo\\bar").endsWith("foo\\bar")).isTrue();
    ASSERT.that(path("C:\\foo\\bar").endsWith("C:\\foo\\bar")).isTrue();
    ASSERT.that(path("C:\\foo\\bar").endsWith("C:\\foo")).isFalse();
    ASSERT.that(path("C:\\foo\\bar").startsWith("foo\\bar")).isFalse();
  }

  @Test
  public void testPaths_toAbsolutePath() {
    assertThat(path("C:\\foo\\bar").toAbsolutePath()).isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar")
        .and().isEqualTo(path("C:\\foo\\bar"));

    assertThat(path("foo\\bar").toAbsolutePath()).isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("work", "foo", "bar")
        .and().isEqualTo(path("C:\\work\\foo\\bar"));
  }

  @Test
  public void testPaths_toRealPath() throws IOException {
    Files.createDirectories(path("C:\\foo\\bar"));
    Files.createSymbolicLink(path("C:\\link"), path("C:\\"));

    ASSERT.that(path("C:\\link\\foo\\bar").toRealPath()).isEqualTo(path("C:\\foo\\bar"));

    ASSERT.that(path("").toRealPath()).isEqualTo(path("C:\\work"));
    ASSERT.that(path(".").toRealPath()).isEqualTo(path("C:\\work"));
    ASSERT.that(path("..").toRealPath()).isEqualTo(path("C:\\"));
    ASSERT.that(path("..\\..").toRealPath()).isEqualTo(path("C:\\"));
    ASSERT.that(path(".\\..\\.\\..").toRealPath()).isEqualTo(path("C:\\"));
    ASSERT.that(path(".\\..\\.\\..\\.").toRealPath()).isEqualTo(path("C:\\"));
  }

  @Test
  public void testPaths_toUri() {
    ASSERT.that(fs.getPath("C:\\").toUri()).is(URI.create("jimfs://win/C:/"));
    ASSERT.that(fs.getPath("C:\\foo").toUri()).is(URI.create("jimfs://win/C:/foo"));
    ASSERT.that(fs.getPath("C:\\foo\\bar").toUri()).is(URI.create("jimfs://win/C:/foo/bar"));
    ASSERT.that(fs.getPath("foo").toUri()).is(URI.create("jimfs://win/C:/work/foo"));
    ASSERT.that(fs.getPath("foo\\bar").toUri()).is(URI.create("jimfs://win/C:/work/foo/bar"));
    ASSERT.that(fs.getPath("").toUri()).is(URI.create("jimfs://win/C:/work"));
    ASSERT.that(fs.getPath(".\\..\\.").toUri()).is(URI.create("jimfs://win/C:/work/./../."));
  }

  @Test
  public void testPaths_toUri_unc() {
    ASSERT.that(fs.getPath("\\\\host\\share\\").toUri())
        .is(URI.create("jimfs://win//host/share/"));
    ASSERT.that(fs.getPath("\\\\host\\share\\foo").toUri())
        .is(URI.create("jimfs://win//host/share/foo"));
    ASSERT.that(fs.getPath("\\\\host\\share\\foo\\bar").toUri())
        .is(URI.create("jimfs://win//host/share/foo/bar"));
  }

  @Test
  public void testPaths_getFromUri() {
    ASSERT.that(Paths.get(URI.create("jimfs://win/C:/")))
        .isEqualTo(fs.getPath("C:\\"));
    ASSERT.that(Paths.get(URI.create("jimfs://win/C:/foo")))
        .isEqualTo(fs.getPath("C:\\foo"));
    ASSERT.that(Paths.get(URI.create("jimfs://win/C:/foo%20bar")))
        .isEqualTo(fs.getPath("C:\\foo bar"));
    ASSERT.that(Paths.get(URI.create("jimfs://win/C:/foo/./bar")))
        .isEqualTo(fs.getPath("C:\\foo\\.\\bar"));
    ASSERT.that(Paths.get(URI.create("jimfs://win/C:/foo/bar/")))
        .isEqualTo(fs.getPath("C:\\foo\\bar"));
  }

  @Test
  public void testPaths_getFromUri_unc() {
    ASSERT.that(Paths.get(URI.create("jimfs://win//host/share/")))
        .isEqualTo(fs.getPath("\\\\host\\share\\"));
    ASSERT.that(Paths.get(URI.create("jimfs://win//host/share/foo")))
        .isEqualTo(fs.getPath("\\\\host\\share\\foo"));
    ASSERT.that(Paths.get(URI.create("jimfs://win//host/share/foo%20bar")))
        .isEqualTo(fs.getPath("\\\\host\\share\\foo bar"));
    ASSERT.that(Paths.get(URI.create("jimfs://win//host/share/foo/./bar")))
        .isEqualTo(fs.getPath("\\\\host\\share\\foo\\.\\bar"));
    ASSERT.that(Paths.get(URI.create("jimfs://win//host/share/foo/bar/")))
        .isEqualTo(fs.getPath("\\\\host\\share\\foo\\bar"));
  }

  @Test
  public void testPathMatchers_glob() {
    assertThat("bar").matches("glob:bar");
    assertThat("bar").matches("glob:*");
    assertThat("C:\\foo").doesNotMatch("glob:*");
    assertThat("C:\\foo\\bar").doesNotMatch("glob:*");
    assertThat("C:\\foo\\bar").matches("glob:**");
    assertThat("C:\\foo\\bar").matches("glob:C:\\\\**");
    assertThat("foo\\bar").doesNotMatch("glob:C:\\\\**");
    assertThat("C:\\foo\\bar\\baz\\stuff").matches("glob:C:\\\\foo\\\\**");
    assertThat("C:\\foo\\bar\\baz\\stuff").matches("glob:C:\\\\**\\\\stuff");
    assertThat("C:\\foo").matches("glob:C:\\\\[a-z]*");
    assertThat("C:\\Foo").doesNotMatch("glob:C:\\\\[a-z]*");
    assertThat("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.java");
    assertThat("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.{java,class}");
    assertThat("C:\\foo\\bar\\baz\\Stuff.class").matches("glob:**\\\\*.{java,class}");
    assertThat("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.*");

    try {
      fs.getPathMatcher("glob:**\\*.{java,class");
      fail();
    } catch (PatternSyntaxException expected) {
    }
  }

  @Test
  public void testPathMatchers_glob_alternateSeparators() {
    // only need to test / in the glob pattern; tests above check that / in a path is changed to
    // \ automatically
    assertThat("C:\\foo").doesNotMatch("glob:*");
    assertThat("C:\\foo\\bar").doesNotMatch("glob:*");
    assertThat("C:\\foo\\bar").matches("glob:**");
    assertThat("C:\\foo\\bar").matches("glob:C:/**");
    assertThat("foo\\bar").doesNotMatch("glob:C:/**");
    assertThat("C:\\foo\\bar\\baz\\stuff").matches("glob:C:/foo/**");
    assertThat("C:\\foo\\bar\\baz\\stuff").matches("glob:C:/**/stuff");
    assertThat("C:\\foo").matches("glob:C:/[a-z]*");
    assertThat("C:\\Foo").doesNotMatch("glob:C:/[a-z]*");
    assertThat("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.java");
    assertThat("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.{java,class}");
    assertThat("C:\\foo\\bar\\baz\\Stuff.class").matches("glob:**/*.{java,class}");
    assertThat("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.*");

    try {
      fs.getPathMatcher("glob:**/*.{java,class");
      fail();
    } catch (PatternSyntaxException expected) {
    }
  }

  @Test
  public void testCreateLink_unsupported() throws IOException {
    // default Windows configuration does not support Feature.LINKS
    Files.createFile(path("foo"));

    try {
      Files.createLink(path("link"), path("foo"));
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testCreateFileOrDirectory_forNonExistentRootPath_fails() throws IOException {
    try {
      Files.createDirectory(path("Z:\\"));
      fail();
    } catch (IOException expected) {
    }

    try {
      Files.createFile(path("Z:\\"));
      fail();
    } catch (IOException expected) {
    }

    try {
      Files.createSymbolicLink(path("Z:\\"), path("foo"));
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testCopyFile_toNonExistentRootPath_fails() throws IOException {
    Files.createFile(path("foo"));
    Files.createDirectory(path("bar"));

    try {
      Files.copy(path("foo"), path("Z:\\"));
      fail();
    } catch (IOException expected) {
    }

    try {
      Files.copy(path("bar"), path("Z:\\"));
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testMoveFile_toNonExistentRootPath_fails() throws IOException {
    Files.createFile(path("foo"));
    Files.createDirectory(path("bar"));

    try {
      Files.move(path("foo"), path("Z:\\"));
      fail();
    } catch (IOException expected) {
    }

    try {
      Files.move(path("bar"), path("Z:\\"));
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testDelete_ofExistingRootDirectory_fails() throws IOException {
    try {
      Files.delete(path("E:\\"));
      fail();
    } catch (FileSystemException expected) {
    }
  }

  @Test
  public void testCreateFileOrDirectory_forExistingRootPath_fails() throws IOException {
    try {
      Files.createDirectory(path("E:\\"));
      fail();
    } catch (IOException expected) {
    }

    try {
      Files.createFile(path("E:\\"));
      fail();
    } catch (IOException expected) {
    }

    try {
      Files.createSymbolicLink(path("E:\\"), path("foo"));
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testCopyFile_toExistingRootPath_fails() throws IOException {
    Files.createFile(path("foo"));
    Files.createDirectory(path("bar"));

    try {
      Files.copy(path("foo"), path("E:\\"), REPLACE_EXISTING);
      fail();
    } catch (IOException expected) {
    }

    try {
      Files.copy(path("bar"), path("E:\\"), REPLACE_EXISTING);
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testMoveFile_toExistingRootPath_fails() throws IOException {
    Files.createFile(path("foo"));
    Files.createDirectory(path("bar"));

    try {
      Files.move(path("foo"), path("E:\\"), REPLACE_EXISTING);
      fail();
    } catch (IOException expected) {
    }

    try {
      Files.move(path("bar"), path("E:\\"), REPLACE_EXISTING);
      fail();
    } catch (IOException expected) {
    }
  }
}
