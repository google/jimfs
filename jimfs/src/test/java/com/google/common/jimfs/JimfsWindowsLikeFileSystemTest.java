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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.PatternSyntaxException;

/**
 * Tests a Windows-like file system through the public methods in {@link Files}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class JimfsWindowsLikeFileSystemTest extends AbstractJimfsIntegrationTest {

  @Override
  protected FileSystem createFileSystem() {
    return Jimfs.newFileSystem(
        "win",
        Configuration.windows()
            .toBuilder()
            .setRoots("C:\\", "E:\\")
            .setAttributeViews("basic", "owner", "dos", "acl", "user")
            .build());
  }

  @Test
  public void testFileSystem() {
    assertThat(fs.getSeparator()).isEqualTo("\\");
    assertThat(fs.getRootDirectories())
        .containsExactlyElementsIn(ImmutableSet.of(path("C:\\"), path("E:\\")))
        .inOrder();
    assertThat(fs.isOpen()).isTrue();
    assertThat(fs.isReadOnly()).isFalse();
    assertThat(fs.supportedFileAttributeViews())
        .containsExactly("basic", "owner", "dos", "acl", "user");
    assertThat(fs.provider()).isInstanceOf(JimfsFileSystemProvider.class);
  }

  @Test
  public void testPaths() {
    assertThatPath("C:\\").isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNoNameComponents();
    assertThatPath("foo").isRelative()
        .and().hasNameComponents("foo");
    assertThatPath("foo\\bar").isRelative()
        .and().hasNameComponents("foo", "bar");
    assertThatPath("C:\\foo\\bar\\baz").isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar", "baz");
  }

  @Test
  public void testPaths_equalityIsCaseInsensitive() {
    assertThatPath("C:\\").isEqualTo(path("c:\\"));
    assertThatPath("foo").isEqualTo(path("FOO"));
  }

  @Test
  public void testPaths_areSortedCaseInsensitive() {
    Path p1 = path("a");
    Path p2 = path("B");
    Path p3 = path("c");
    Path p4 = path("D");

    assertThat(Ordering.natural().immutableSortedCopy(Arrays.asList(p3, p4, p1, p2)))
        .isEqualTo(ImmutableList.of(p1, p2, p3, p4));

    // would be p2, p4, p1, p3 if sorting were case sensitive
  }

  @Test
  public void testPaths_withSlash() {
    assertThatPath("foo/bar").isRelative()
        .and().hasNameComponents("foo", "bar")
        .and().isEqualTo(path("foo\\bar"));
    assertThatPath("C:/foo/bar/baz").isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar", "baz")
        .and().isEqualTo(path("C:\\foo\\bar\\baz"));
    assertThatPath("C:/foo\\bar/baz").isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar", "baz")
        .and().isEqualTo(path("C:\\foo\\bar\\baz"));
  }

  @Test
  public void testPaths_resolve() {
    assertThatPath(path("C:\\").resolve("foo\\bar")).isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar");
    assertThatPath(path("foo\\bar").resolveSibling("baz")).isRelative()
        .and().hasNameComponents("foo", "baz");
    assertThatPath(path("foo\\bar").resolve("C:\\one\\two")).isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("one", "two");
  }

  @Test
  public void testPaths_normalize() {
    assertThatPath(path("foo\\bar\\..").normalize()).isRelative()
        .and().hasNameComponents("foo");
    assertThatPath(path("foo\\.\\bar\\..\\baz\\test\\.\\..\\stuff").normalize()).isRelative()
        .and().hasNameComponents("foo", "baz", "stuff");
    assertThatPath(path("..\\..\\foo\\.\\bar").normalize()).isRelative()
        .and().hasNameComponents("..", "..", "foo", "bar");
    assertThatPath(path("foo\\..\\..\\bar").normalize()).isRelative()
        .and().hasNameComponents("..", "bar");
    assertThatPath(path("..\\.\\..").normalize()).isRelative()
        .and().hasNameComponents("..", "..");
  }

  @Test
  public void testPaths_relativize() {
    assertThatPath(path("C:\\foo\\bar").relativize(path("C:\\foo\\bar\\baz"))).isRelative()
        .and().hasNameComponents("baz");
    assertThatPath(path("C:\\foo\\bar\\baz").relativize(path("C:\\foo\\bar"))).isRelative()
        .and().hasNameComponents("..");
    assertThatPath(path("C:\\foo\\bar\\baz").relativize(path("C:\\foo\\baz\\bar"))).isRelative()
        .and().hasNameComponents("..", "..", "baz", "bar");
    assertThatPath(path("foo\\bar").relativize(path("foo"))).isRelative()
        .and().hasNameComponents("..");
    assertThatPath(path("foo").relativize(path("foo\\bar"))).isRelative()
        .and().hasNameComponents("bar");

    try {
      Path unused = path("C:\\foo\\bar").relativize(path("bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      Path unused = path("bar").relativize(path("C:\\foo\\bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testPaths_startsWith_endsWith() {
    assertThat(path("C:\\foo\\bar").startsWith("C:\\")).isTrue();
    assertThat(path("C:\\foo\\bar").startsWith("C:\\foo")).isTrue();
    assertThat(path("C:\\foo\\bar").startsWith("C:\\foo\\bar")).isTrue();
    assertThat(path("C:\\foo\\bar").endsWith("bar")).isTrue();
    assertThat(path("C:\\foo\\bar").endsWith("foo\\bar")).isTrue();
    assertThat(path("C:\\foo\\bar").endsWith("C:\\foo\\bar")).isTrue();
    assertThat(path("C:\\foo\\bar").endsWith("C:\\foo")).isFalse();
    assertThat(path("C:\\foo\\bar").startsWith("foo\\bar")).isFalse();
  }

  @Test
  public void testPaths_toAbsolutePath() {
    assertThatPath(path("C:\\foo\\bar").toAbsolutePath()).isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("foo", "bar")
        .and().isEqualTo(path("C:\\foo\\bar"));

    assertThatPath(path("foo\\bar").toAbsolutePath()).isAbsolute()
        .and().hasRootComponent("C:\\")
        .and().hasNameComponents("work", "foo", "bar")
        .and().isEqualTo(path("C:\\work\\foo\\bar"));
  }

  @Test
  public void testPaths_toRealPath() throws IOException {
    Files.createDirectories(path("C:\\foo\\bar"));
    Files.createSymbolicLink(path("C:\\link"), path("C:\\"));

    assertThatPath(path("C:\\link\\foo\\bar").toRealPath()).isEqualTo(path("C:\\foo\\bar"));

    assertThatPath(path("").toRealPath()).isEqualTo(path("C:\\work"));
    assertThatPath(path(".").toRealPath()).isEqualTo(path("C:\\work"));
    assertThatPath(path("..").toRealPath()).isEqualTo(path("C:\\"));
    assertThatPath(path("..\\..").toRealPath()).isEqualTo(path("C:\\"));
    assertThatPath(path(".\\..\\.\\..").toRealPath()).isEqualTo(path("C:\\"));
    assertThatPath(path(".\\..\\.\\..\\.").toRealPath()).isEqualTo(path("C:\\"));
  }

  @Test
  public void testPaths_toUri() {
    assertThat(fs.getPath("C:\\").toUri()).isEqualTo(URI.create("jimfs://win/C:/"));
    assertThat(fs.getPath("C:\\foo").toUri()).isEqualTo(URI.create("jimfs://win/C:/foo"));
    assertThat(fs.getPath("C:\\foo\\bar").toUri()).isEqualTo(URI.create("jimfs://win/C:/foo/bar"));
    assertThat(fs.getPath("foo").toUri()).isEqualTo(URI.create("jimfs://win/C:/work/foo"));
    assertThat(fs.getPath("foo\\bar").toUri()).isEqualTo(URI.create("jimfs://win/C:/work/foo/bar"));
    assertThat(fs.getPath("").toUri()).isEqualTo(URI.create("jimfs://win/C:/work/"));
    assertThat(fs.getPath(".\\..\\.").toUri()).isEqualTo(URI.create("jimfs://win/C:/work/./.././"));
  }

  @Test
  public void testPaths_toUri_unc() {
    assertThat(fs.getPath("\\\\host\\share\\").toUri())
        .isEqualTo(URI.create("jimfs://win//host/share/"));
    assertThat(fs.getPath("\\\\host\\share\\foo").toUri())
        .isEqualTo(URI.create("jimfs://win//host/share/foo"));
    assertThat(fs.getPath("\\\\host\\share\\foo\\bar").toUri())
        .isEqualTo(URI.create("jimfs://win//host/share/foo/bar"));
  }

  @Test
  public void testPaths_getFromUri() {
    assertThatPath(Paths.get(URI.create("jimfs://win/C:/"))).isEqualTo(fs.getPath("C:\\"));
    assertThatPath(Paths.get(URI.create("jimfs://win/C:/foo"))).isEqualTo(fs.getPath("C:\\foo"));
    assertThatPath(Paths.get(URI.create("jimfs://win/C:/foo%20bar")))
        .isEqualTo(fs.getPath("C:\\foo bar"));
    assertThatPath(Paths.get(URI.create("jimfs://win/C:/foo/./bar")))
        .isEqualTo(fs.getPath("C:\\foo\\.\\bar"));
    assertThatPath(Paths.get(URI.create("jimfs://win/C:/foo/bar/")))
        .isEqualTo(fs.getPath("C:\\foo\\bar"));
  }

  @Test
  public void testPaths_getFromUri_unc() {
    assertThatPath(Paths.get(URI.create("jimfs://win//host/share/")))
        .isEqualTo(fs.getPath("\\\\host\\share\\"));
    assertThatPath(Paths.get(URI.create("jimfs://win//host/share/foo")))
        .isEqualTo(fs.getPath("\\\\host\\share\\foo"));
    assertThatPath(Paths.get(URI.create("jimfs://win//host/share/foo%20bar")))
        .isEqualTo(fs.getPath("\\\\host\\share\\foo bar"));
    assertThatPath(Paths.get(URI.create("jimfs://win//host/share/foo/./bar")))
        .isEqualTo(fs.getPath("\\\\host\\share\\foo\\.\\bar"));
    assertThatPath(Paths.get(URI.create("jimfs://win//host/share/foo/bar/")))
        .isEqualTo(fs.getPath("\\\\host\\share\\foo\\bar"));
  }

  @Test
  public void testPathMatchers_glob() {
    assertThatPath("bar").matches("glob:bar");
    assertThatPath("bar").matches("glob:*");
    assertThatPath("C:\\foo").doesNotMatch("glob:*");
    assertThatPath("C:\\foo\\bar").doesNotMatch("glob:*");
    assertThatPath("C:\\foo\\bar").matches("glob:**");
    assertThatPath("C:\\foo\\bar").matches("glob:C:\\\\**");
    assertThatPath("foo\\bar").doesNotMatch("glob:C:\\\\**");
    assertThatPath("C:\\foo\\bar\\baz\\stuff").matches("glob:C:\\\\foo\\\\**");
    assertThatPath("C:\\foo\\bar\\baz\\stuff").matches("glob:C:\\\\**\\\\stuff");
    assertThatPath("C:\\foo").matches("glob:C:\\\\[a-z]*");
    assertThatPath("C:\\Foo").doesNotMatch("glob:C:\\\\[a-z]*");
    assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.java");
    assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.{java,class}");
    assertThatPath("C:\\foo\\bar\\baz\\Stuff.class").matches("glob:**\\\\*.{java,class}");
    assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.*");

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
    assertThatPath("C:\\foo").doesNotMatch("glob:*");
    assertThatPath("C:\\foo\\bar").doesNotMatch("glob:*");
    assertThatPath("C:\\foo\\bar").matches("glob:**");
    assertThatPath("C:\\foo\\bar").matches("glob:C:/**");
    assertThatPath("foo\\bar").doesNotMatch("glob:C:/**");
    assertThatPath("C:\\foo\\bar\\baz\\stuff").matches("glob:C:/foo/**");
    assertThatPath("C:\\foo\\bar\\baz\\stuff").matches("glob:C:/**/stuff");
    assertThatPath("C:\\foo").matches("glob:C:/[a-z]*");
    assertThatPath("C:\\Foo").doesNotMatch("glob:C:/[a-z]*");
    assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.java");
    assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.{java,class}");
    assertThatPath("C:\\foo\\bar\\baz\\Stuff.class").matches("glob:**/*.{java,class}");
    assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.*");

    try {
      fs.getPathMatcher("glob:**/*.{java,class");
      fail();
    } catch (PatternSyntaxException expected) {
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
  public void testDelete_directory_cantDeleteRoot() throws IOException {
    // test with E:\ because it is empty
    try {
      Files.delete(path("E:\\"));
      fail();
    } catch (FileSystemException expected) {
      assertThat(expected.getFile()).isEqualTo("E:\\");
      assertThat(expected.getMessage()).contains("root");
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

  @Test
  public void testMove_rootDirectory_fails() throws IOException {
    try {
      Files.move(path("E:\\"), path("Z:\\"));
      fail();
    } catch (FileSystemException expected) {
    }

    try {
      Files.move(path("E:\\"), path("C:\\bar"));
      fail();
    } catch (FileSystemException expected) {
    }
  }
}
