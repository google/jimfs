/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

package com.google.common.io.jimfs;

import static com.google.common.io.jimfs.testing.PathSubject.paths;
import static com.google.common.io.jimfs.testing.TestUtils.preFilledBytes;
import static com.google.common.primitives.Bytes.concat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.DSYNC;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.SPARSE;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.jimfs.testing.PathSubject;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.util.regex.PatternSyntaxException;

/**
 * Tests an in-memory file system through the public APIs in {@link Files}, etc.
 *
 * @author Colin Decker
 */
public class JimfsIntegrationTest {

  private FileSystem fs;

  @Before
  public void setUp() throws IOException {
    fs = Jimfs.newUnixLikeFileSystem();
  }

  @Test
  public void testPaths() {
    assertThat("/").isAbsolute()
        .and().hasRootComponent("/")
        .and().hasNoNameComponents();
    assertThat("foo").isRelative()
        .and().hasNameComponents("foo");
    assertThat("foo/bar").isRelative()
        .and().hasNameComponents("foo", "bar");
    assertThat("/foo/bar/baz").isAbsolute()
        .and().hasRootComponent("/")
        .and().hasNameComponents("foo", "bar", "baz");
  }

  @Test
  public void testPaths_resolve() {
    assertThat(path("/").resolve("foo/bar")).isAbsolute()
        .and().hasRootComponent("/")
        .and().hasNameComponents("foo", "bar");
    assertThat(path("foo/bar").resolveSibling("baz")).isRelative()
        .and().hasNameComponents("foo", "baz");
    assertThat(path("foo/bar").resolve("/one/two")).isAbsolute()
        .and().hasRootComponent("/")
        .and().hasNameComponents("one", "two");
  }

  @Test
  public void testPaths_normalize() {
    assertThat(path("foo/bar/..").normalize()).isRelative()
        .and().hasNameComponents("foo");
    assertThat(path("foo/./bar/../baz/test/./../stuff").normalize()).isRelative()
        .and().hasNameComponents("foo", "baz", "stuff");
    assertThat(path("../../foo/./bar").normalize()).isRelative()
        .and().hasNameComponents("..", "..", "foo", "bar");
    assertThat(path("foo/../../bar").normalize()).isRelative()
        .and().hasNameComponents("..", "bar");
    assertThat(path(".././..").normalize()).isRelative()
        .and().hasNameComponents("..", "..");
  }

  @Test
  public void testPaths_relativize() {
    assertThat(path("/foo/bar").relativize(path("/foo/bar/baz"))).isRelative()
        .and().hasNameComponents("baz");
    assertThat(path("/foo/bar/baz").relativize(path("/foo/bar"))).isRelative()
        .and().hasNameComponents("..");
    assertThat(path("/foo/bar/baz").relativize(path("/foo/baz/bar"))).isRelative()
        .and().hasNameComponents("..", "..", "baz", "bar");
    assertThat(path("foo/bar").relativize(path("foo"))).isRelative()
        .and().hasNameComponents("..");
    assertThat(path("foo").relativize(path("foo/bar"))).isRelative()
        .and().hasNameComponents("bar");

    try {
      path("/foo/bar").relativize(path("bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      path("bar").relativize(path("/foo/bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testPaths_startsWith_endsWith() {
    ASSERT.that(path("/foo/bar").startsWith("/")).isTrue();
    ASSERT.that(path("/foo/bar").startsWith("/foo")).isTrue();
    ASSERT.that(path("/foo/bar").startsWith("/foo/bar")).isTrue();
    ASSERT.that(path("/foo/bar").endsWith("bar")).isTrue();
    ASSERT.that(path("/foo/bar").endsWith("foo/bar")).isTrue();
    ASSERT.that(path("/foo/bar").endsWith("/foo/bar")).isTrue();
    ASSERT.that(path("/foo/bar").endsWith("/foo")).isFalse();
    ASSERT.that(path("/foo/bar").startsWith("foo/bar")).isFalse();
  }

  @Test
  public void testPaths_toAbsolutePath() {
    assertThat(path("/foo/bar").toAbsolutePath()).isAbsolute()
        .and().hasRootComponent("/")
        .and().hasNameComponents("foo", "bar")
        .and().isEqualTo(path("/foo/bar"));

    assertThat(path("foo/bar").toAbsolutePath()).isAbsolute()
        .and().hasRootComponent("/")
        .and().hasNameComponents("work", "foo", "bar")
        .and().isEqualTo(path("/work/foo/bar"));
  }

  @Test
  public void testPaths_toRealPath() throws IOException {
    Files.createDirectories(path("/foo/bar"));
    Files.createSymbolicLink(path("/link"), path("/"));

    ASSERT.that(path("/link/foo/bar").toRealPath()).isEqualTo(path("/foo/bar"));

    // case insensitivity tests: move to another test
    //ASSERT.that(path("/FOO/BAR").toRealPath()).isEqualTo(path("/foo/bar"));
    //ASSERT.that(path("/Link/FOO/bAR").toRealPath()).isEqualTo(path("/foo/bar"));
  }

  @Test
  public void testPathMatchers_regex() {
    assertThat("bar").matches("regex:.*");
    assertThat("bar").matches("regex:bar");
    assertThat("bar").matches("regex:[a-z]+");
    assertThat("/foo/bar").matches("regex:/.*");
    assertThat("/foo/bar").matches("regex:/.*/bar");
  }

  @Test
  public void testPathMatchers_glob() {
    assertThat("bar").matches("glob:bar");
    assertThat("bar").matches("glob:*");
    assertThat("/foo").doesNotMatch("glob:*");
    assertThat("/foo/bar").doesNotMatch("glob:*");
    assertThat("/foo/bar").matches("glob:**");
    assertThat("/foo/bar").matches("glob:/**");
    assertThat("foo/bar").doesNotMatch("glob:/**");
    assertThat("/foo/bar/baz/stuff").matches("glob:/foo/**");
    assertThat("/foo/bar/baz/stuff").matches("glob:/**/stuff");
    assertThat("/foo").matches("glob:/[a-z]*");
    assertThat("/Foo").doesNotMatch("glob:/[a-z]*");
    assertThat("/foo/bar/baz/Stuff.java").matches("glob:**/*.java");
    assertThat("/foo/bar/baz/Stuff.java").matches("glob:**/*.{java,class}");
    assertThat("/foo/bar/baz/Stuff.class").matches("glob:**/*.{java,class}");
    assertThat("/foo/bar/baz/Stuff.java").matches("glob:**/*.*");

    try {
      fs.getPathMatcher("glob:**/*.{java,class");
      fail();
    } catch (PatternSyntaxException expected) {
    }
  }

  @Test
  public void testPathMatchers_invalid() {
    try {
      fs.getPathMatcher("glob");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      fs.getPathMatcher("foo:foo");
      fail();
    } catch (UnsupportedOperationException expected) {
      ASSERT.that(expected.getMessage()).contains("syntax");
    }
  }

  @Test
  public void testNewFileSystem_hasRootAndWorkingDirectory() throws IOException {
    assertThat("/").hasChildren("work");
    assertThat("/work").hasNoChildren();
  }

  @Test
  public void testCreateDirectory_absolute() throws IOException {
    Files.createDirectory(path("/test"));

    assertThat("/test").exists();
    assertThat("/").hasChildren("test", "work");

    Files.createDirectory(path("/foo"));
    Files.createDirectory(path("/foo/bar"));

    assertThat("/foo/bar").exists();
    assertThat("/foo").hasChildren("bar");
  }

  @Test
  public void testCreateFile_absolute() throws IOException {
    Files.createFile(path("/test.txt"));

    assertThat("/test.txt").isRegularFile();
    assertThat("/").hasChildren("test.txt", "work");

    Files.createDirectory(path("/foo"));
    Files.createFile(path("/foo/test.txt"));

    assertThat("/foo/test.txt").isRegularFile();
    assertThat("/foo").hasChildren("test.txt");
  }

  @Test
  public void testCreateSymbolicLink_absolute() throws IOException {
    Files.createSymbolicLink(path("/link.txt"), path("test.txt"));

    assertThat("/link.txt", NOFOLLOW_LINKS)
        .isSymbolicLink().withTarget("test.txt");
    assertThat("/").hasChildren("link.txt", "work");

    Files.createDirectory(path("/foo"));
    Files.createSymbolicLink(path("/foo/link.txt"), path("test.txt"));

    assertThat("/foo/link.txt").noFollowLinks()
        .isSymbolicLink().withTarget("test.txt");
    assertThat("/foo").hasChildren("link.txt");
  }

  @Test
  public void testCreateLink_absolute() throws IOException {
    Files.createFile(path("/test.txt"));
    Files.createLink(path("/link.txt"), path("/test.txt"));

    // don't assert that the link is the same file here, just that it was created
    // later tests check that linking works correctly
    assertThat("/link.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("/").hasChildren("link.txt", "test.txt", "work");

    Files.createDirectory(path("/foo"));
    Files.createLink(path("/foo/link.txt"), path("/test.txt"));

    assertThat("/foo/link.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("/foo").hasChildren("link.txt");
  }

  @Test
  public void testCreateDirectory_relative() throws IOException {
    Files.createDirectory(path("test"));

    assertThat("/work/test", NOFOLLOW_LINKS).isDirectory();
    assertThat("test", NOFOLLOW_LINKS).isDirectory();
    assertThat("/work").hasChildren("test");
    assertThat("test").isSameFileAs("/work/test");

    Files.createDirectory(path("foo"));
    Files.createDirectory(path("foo/bar"));

    assertThat("/work/foo/bar", NOFOLLOW_LINKS).isDirectory();
    assertThat("foo/bar", NOFOLLOW_LINKS).isDirectory();
    assertThat("/work/foo").hasChildren("bar");
    assertThat("foo").hasChildren("bar");
    assertThat("foo/bar").isSameFileAs("/work/foo/bar");
  }

  @Test
  public void testCreateFile_relative() throws IOException {
    Files.createFile(path("test.txt"));

    assertThat("/work/test.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("test.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("/work").hasChildren("test.txt");
    assertThat("test.txt").isSameFileAs("/work/test.txt");

    Files.createDirectory(path("foo"));
    Files.createFile(path("foo/test.txt"));

    assertThat("/work/foo/test.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("foo/test.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("/work/foo").hasChildren("test.txt");
    assertThat("foo").hasChildren("test.txt");
    assertThat("foo/test.txt").isSameFileAs("/work/foo/test.txt");
  }

  @Test
  public void testCreateSymbolicLink_relative() throws IOException {
    Files.createSymbolicLink(path("link.txt"), path("test.txt"));

    assertThat("/work/link.txt", NOFOLLOW_LINKS)
        .isSymbolicLink().withTarget("test.txt");
    assertThat("link.txt", NOFOLLOW_LINKS)
        .isSymbolicLink().withTarget("test.txt");
    assertThat("/work").hasChildren("link.txt");

    Files.createDirectory(path("foo"));
    Files.createSymbolicLink(path("foo/link.txt"), path("test.txt"));

    assertThat("/work/foo/link.txt", NOFOLLOW_LINKS)
        .isSymbolicLink().withTarget("test.txt");
    assertThat("foo/link.txt", NOFOLLOW_LINKS)
        .isSymbolicLink().withTarget("test.txt");
    assertThat("/work/foo").hasChildren("link.txt");
    assertThat("foo").hasChildren("link.txt");
  }

  @Test
  public void testCreateLink_relative() throws IOException {
    Files.createFile(path("test.txt"));
    Files.createLink(path("link.txt"), path("test.txt"));

    // don't assert that the link is the same file here, just that it was created
    // later tests check that linking works correctly
    assertThat("/work/link.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("link.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("/work").hasChildren("link.txt", "test.txt");

    Files.createDirectory(path("foo"));
    Files.createLink(path("foo/link.txt"), path("test.txt"));

    assertThat("/work/foo/link.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("foo/link.txt", NOFOLLOW_LINKS).isRegularFile();
    assertThat("foo").hasChildren("link.txt");
  }

  @Test
  public void testCreateFile_existing() throws IOException {
    Files.createFile(path("/test"));
    try {
      Files.createFile(path("/test"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/test", expected.getMessage());
    }

    try {
      Files.createDirectory(path("/test"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/test", expected.getMessage());
    }

    try {
      Files.createSymbolicLink(path("/test"), path("/foo"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/test", expected.getMessage());
    }

    Files.createFile(path("/foo"));
    try {
      Files.createLink(path("/test"), path("/foo"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/test", expected.getMessage());
    }
  }

  @Test
  public void testCreateFile_parentDoesNotExist() throws IOException {
    try {
      Files.createFile(path("/foo/test"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/foo/test", expected.getMessage());
    }

    try {
      Files.createDirectory(path("/foo/test"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/foo/test", expected.getMessage());
    }

    try {
      Files.createSymbolicLink(path("/foo/test"), path("/bar"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/foo/test", expected.getMessage());
    }

    Files.createFile(path("/bar"));
    try {
      Files.createLink(path("/foo/test"), path("/bar"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/foo/test", expected.getMessage());
    }
  }

  @Test
  public void testCreateFile_parentIsNotDirectory() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createFile(path("/foo/bar"));

    try {
      Files.createFile(path("/foo/bar/baz"));
      fail();
    } catch (NoSuchFileException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo/bar/baz");
    }
  }

  @Test
  public void testCreateFile_nonDirectoryHigherInPath() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createFile(path("/foo/bar"));

    try {
      Files.createFile(path("/foo/bar/baz/stuff"));
      fail();
    } catch (NoSuchFileException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo/bar/baz/stuff");
    }
  }

  @Test
  public void testCreateFile_parentSymlinkDoesNotExist() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createSymbolicLink(path("/foo/bar"), path("/foo/nope"));

    try {
      Files.createFile(path("/foo/bar/baz"));
      fail();
    } catch (NoSuchFileException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo/bar/baz");
    }
  }

  @Test
  public void testCreateFile_symlinkHigherInPathDoesNotExist() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createSymbolicLink(path("/foo/bar"), path("nope"));

    try {
      Files.createFile(path("/foo/bar/baz/stuff"));
      fail();
    } catch (NoSuchFileException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo/bar/baz/stuff");
    }
  }

  @Test
  public void testCreateFile_parentSymlinkDoesPointsToNonDirectory() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createFile(path("/foo/file"));
    Files.createSymbolicLink(path("/foo/bar"), path("/foo/file"));

    try {
      Files.createFile(path("/foo/bar/baz"));
      fail();
    } catch (NoSuchFileException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo/bar/baz");
    }
  }

  @Test
  public void testCreateFile_symlinkHigherInPathPointsToNonDirectory() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createFile(path("/foo/file"));
    Files.createSymbolicLink(path("/foo/bar"), path("file"));

    try {
      Files.createFile(path("/foo/bar/baz/stuff"));
      fail();
    } catch (NoSuchFileException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo/bar/baz/stuff");
    }
  }

  @Test
  public void testCreateDirectories() throws IOException {
    Files.createDirectories(path("/foo/bar/baz"));

    assertThat("/foo").isDirectory();
    assertThat("/foo/bar").isDirectory();
    assertThat("/foo/bar/baz").isDirectory();

    Files.createDirectories(path("/foo/asdf/jkl"));

    assertThat("/foo/asdf").isDirectory();
    assertThat("/foo/asdf/jkl").isDirectory();

    Files.createDirectories(path("bar/baz"));

    assertThat("bar/baz").isDirectory();
    assertThat("/work/bar/baz").isDirectory();
  }

  @Test
  public void testDirectories_newlyCreatedDirectoryHasTwoLinks() throws IOException {
    // one link from its parent to it; one from it to itself

    Files.createDirectory(path("/foo"));

    assertThat("/foo").hasLinkCount(2);
  }

  @Test
  public void testDirectories_creatingDirectoryAddsOneLinkToParent() throws IOException {
    // from the .. direntry

    Files.createDirectory(path("/foo"));
    Files.createDirectory(path("/foo/bar"));

    assertThat("/foo").hasLinkCount(3);

    Files.createDirectory(path("/foo/baz"));

    assertThat("/foo").hasLinkCount(4);
  }

  @Test
  public void testDirectories_creatingNonDirectoryDoesNotAddLinkToParent() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createFile(path("/foo/file"));
    Files.createSymbolicLink(path("/foo/fileSymlink"), path("file"));
    Files.createLink(path("/foo/link"), path("/foo/file"));
    Files.createSymbolicLink(path("/foo/fooSymlink"), path("/foo"));

    assertThat("/foo").hasLinkCount(2);
  }

  @Test
  public void testSize_forNewFile_isZero() throws IOException {
    Files.createFile(path("/test"));

    assertThat("/test").hasSize(0);
  }

  @Test
  public void testRead_forNewFile_isEmpty() throws IOException {
    Files.createFile(path("/test"));

    assertThat("/test").containsNoBytes();
  }

  @Test
  public void testWriteFile_succeeds() throws IOException {
    Files.createFile(path("/test"));
    Files.write(path("/test"), new byte[]{0, 1, 2, 3});
  }

  @Test
  public void testSize_forFileAfterWrite_isNumberOfBytesWritten() throws IOException {
    Files.write(path("/test"), new byte[]{0, 1, 2, 3});

    assertThat("/test").hasSize(4);
  }

  @Test
  public void testRead_forFileAfterWrite_isBytesWritten() throws IOException {
    byte[] bytes = {0, 1, 2, 3};
    Files.write(path("/test"), bytes);

    assertThat("/test").containsBytes(bytes);
  }

  @Test
  public void testWriteFile_withStandardOptions() throws IOException {
    Path test = path("/test");
    byte[] bytes = {0, 1, 2, 3};

    try {
      // CREATE and CREATE_NEW not specified
      Files.write(test, bytes, WRITE);
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals(test.toString(), expected.getMessage());
    }

    Files.write(test, bytes, CREATE_NEW); // succeeds, file does not exist
    assertThat("/test").containsBytes(bytes);

    try {
      Files.write(test, bytes, CREATE_NEW); // CREATE_NEW requires file not exist
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals(test.toString(), expected.getMessage());
    }

    Files.write(test, new byte[]{4, 5}, CREATE); // succeeds, ok for file to already exist
    assertThat("/test").containsBytes(4, 5, 2, 3); // did not truncate or append, so overwrote

    Files.write(test, bytes, WRITE, CREATE, TRUNCATE_EXISTING); // default options
    assertThat("/test").containsBytes(bytes);

    Files.write(test, bytes, WRITE, APPEND);
    assertThat("/test").containsBytes(0, 1, 2, 3, 0, 1, 2, 3);

    Files.write(test, bytes, WRITE, CREATE, TRUNCATE_EXISTING, APPEND, SPARSE, DSYNC, SYNC);
    assertThat("/test").containsBytes(bytes);

    try {
      Files.write(test, bytes, READ, WRITE); // READ not allowed
      fail();
    } catch (UnsupportedOperationException expected) {}
  }

  @Test
  public void testWriteLines_succeeds() throws IOException {
    Files.write(path("/test.txt"), ImmutableList.of("hello", "world"), UTF_8);
  }

  @Test
  public void testRead_forFileAfterWriteLines_isLinesWritten() throws IOException {
    Files.write(path("/test.txt"), ImmutableList.of("hello", "world"), UTF_8);

    assertThat("/test.txt").containsLines("hello", "world");
  }

  @Test
  public void testWriteLines_withStandardOptions() throws IOException {
    Path test = path("/test.txt");
    ImmutableList<String> lines = ImmutableList.of("hello", "world");

    try {
      // CREATE and CREATE_NEW not specified
      Files.write(test, lines, UTF_8, WRITE);
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals(test.toString(), expected.getMessage());
    }

    Files.write(test, lines, UTF_8, CREATE_NEW); // succeeds, file does not exist
    assertThat(test).containsLines(lines);

    try {
      Files.write(test, lines, UTF_8, CREATE_NEW); // CREATE_NEW requires file not exist
      fail();
    } catch (FileAlreadyExistsException expected) {}

    // succeeds, ok for file to already exist
    Files.write(test, ImmutableList.of("foo"), UTF_8, CREATE);
    assertThat(test).containsLines("foo", "o", "world"); // did not truncate or append, so overwrote

    Files.write(test, lines, UTF_8, WRITE, CREATE, TRUNCATE_EXISTING); // default options
    assertThat(test).containsLines(lines);

    Files.write(test, lines, UTF_8, WRITE, APPEND);
    assertThat(test).containsLines("hello", "world", "hello", "world");

    Files.write(test, lines, UTF_8, WRITE, CREATE, TRUNCATE_EXISTING, APPEND, SPARSE, DSYNC, SYNC);
    assertThat(test).containsLines(lines);

    try {
      Files.write(test, lines, UTF_8, READ, WRITE); // READ not allowed
      fail();
    } catch (UnsupportedOperationException expected) {}
  }

  @Test
  public void testWrite_fileExistsButIsNotRegularFile() throws IOException {
    Files.createDirectory(path("/foo"));

    try {
      // non-CREATE mode
      Files.write(path("/foo"), preFilledBytes(10), WRITE);
    } catch (FileSystemException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo");
      ASSERT.that(expected.getMessage()).contains("regular file");
    }

    try {
      // CREATE mode
      Files.write(path("/foo"), preFilledBytes(10));
    } catch (FileSystemException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo");
      ASSERT.that(expected.getMessage()).contains("regular file");
    }
  }

  @Test
  public void testDelete_file() throws IOException {
    try {
      Files.delete(path("/test"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/test", expected.getMessage());
    }

    try {
      Files.delete(path("/foo/bar"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/foo/bar", expected.getMessage());
    }

    assertFalse(Files.deleteIfExists(path("/test")));
    assertFalse(Files.deleteIfExists(path("/foo/bar")));

    Files.createFile(path("/test"));
    assertThat("/test").isRegularFile();

    Files.delete(path("/test"));
    assertThat("/test").doesNotExist();

    Files.createFile(path("/test"));

    assertTrue(Files.deleteIfExists(path("/test")));
    assertThat("/test").doesNotExist();
  }

  @Test
  public void testDelete_directory() throws IOException {
    Files.createDirectories(path("/foo/bar"));
    assertThat("/foo").isDirectory();
    assertThat("/foo/bar").isDirectory();

    Files.delete(path("/foo/bar"));
    assertThat("/foo/bar").doesNotExist();

    assertTrue(Files.deleteIfExists(path("/foo")));
    assertThat("/foo").doesNotExist();
  }

  @Test
  public void testDelete_directory_cantDeleteNonEmptyDirectory() throws IOException {
    Files.createDirectories(path("/foo/bar"));

    try {
      Files.delete(path("/foo"));
      fail();
    } catch (DirectoryNotEmptyException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo");
    }

    try {
      Files.deleteIfExists(path("/foo"));
      fail();
    } catch (DirectoryNotEmptyException expected) {
      ASSERT.that(expected.getFile()).isEqualTo("/foo");
    }
  }

  @Test
  public void testDelete_directory_cantDeleteRoot() throws IOException {
    // delete working directory so that root is empty
    // don't want to just be testing the "can't delete when not empty" logic
    // TODO(cgdecker): it's possible that deleting the working directory should also fail
    // it seems to fail in the default file system, and deleting it does permanently cause all
    // relative path operations to fail even if a new directory is created at the same path
    Files.delete(path("/work"));

    try {
      Files.delete(path("/"));
      fail();
    } catch (IOException expected) {
      ASSERT.that(expected.getMessage()).contains("root");
    }

    Files.createDirectories(path("/foo/bar"));

    try {
      Files.delete(path("/foo/bar/../.."));
      fail();
    } catch (IOException expected) {
      ASSERT.that(expected.getMessage()).contains("root");
    }

    try {
      Files.delete(path("/foo/./../foo/bar/./../bar/.././../../.."));
      fail();
    } catch (IOException expected) {
      ASSERT.that(expected.getMessage()).contains("root");
    }
  }

  @Test
  public void testSymbolicLinks() throws IOException {
    Files.createSymbolicLink(path("/link.txt"), path("/file.txt"));
    assertThat("/link.txt", NOFOLLOW_LINKS)
        .isSymbolicLink().withTarget("/file.txt");
    assertThat("/link.txt").doesNotExist(); // following the link; target doesn't exist

    try {
      Files.createFile(path("/link.txt"));
      fail();
    } catch (FileAlreadyExistsException expected) {}

    try {
      Files.readAllBytes(path("/link.txt"));
      fail();
    } catch (NoSuchFileException expected) {}

    Files.createFile(path("/file.txt"));
    assertThat("/link.txt").isRegularFile(); // following the link; target does exist
    assertThat("/link.txt").containsNoBytes();

    Files.createSymbolicLink(path("/foo"), path("/bar/baz"));
    assertThat("/foo", NOFOLLOW_LINKS)
        .isSymbolicLink().withTarget("/bar/baz");
    assertThat("/foo").doesNotExist(); // following the link; target doesn't exist

    Files.createDirectories(path("/bar/baz"));
    assertThat("/foo").isDirectory(); // following the link; target does exist

    Files.createFile(path("/bar/baz/test.txt"));
    assertThat("/foo/test.txt", NOFOLLOW_LINKS).isRegularFile(); // follow intermediate link

    try {
      Files.readSymbolicLink(path("/none"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/none", expected.getMessage());
    }

    try {
      Files.readSymbolicLink(path("/file.txt"));
      fail();
    } catch (NotLinkException expected) {
      assertEquals("/file.txt", expected.getMessage());
    }
  }

  @Test
  public void testSymbolicLinks_symlinkCycle() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createSymbolicLink(path("/foo/bar"), path("baz"));
    Files.createSymbolicLink(path("/foo/baz"), path("bar"));

    try {
      Files.createFile(path("/foo/bar/file"));
      fail();
    } catch (IOException expected) {
      ASSERT.that(expected.getMessage()).contains("symbolic link");
    }

    try {
      Files.write(path("/foo/bar"), preFilledBytes(10));
      fail();
    } catch (IOException expected) {
      ASSERT.that(expected.getMessage()).contains("symbolic link");
    }
  }

  @Test
  public void testSymbolicLinks_lookupOfAbsoluteSymlinkPathFromRelativePath() throws IOException {
    // relative path lookups are in the FileTree for the working directory
    // this tests that when an absolute path is encountered, the lookup switches to the super root
    // FileTree

    Files.createDirectories(path("/foo/bar/baz"));
    Files.createFile(path("/foo/bar/baz/file"));
    Files.createDirectories(path("one/two/three"));
    Files.createSymbolicLink(path("/work/one/two/three/link"), path("/foo/bar"));

    assertThat("one/two/three/link/baz/file")
        .isSameFileAs("/foo/bar/baz/file");
  }

  @Test
  public void testLink() throws IOException {
    Files.createFile(path("/file.txt"));
    // checking link count requires "unix" attribute support, which we're using here
    assertThat("/file.txt").hasLinkCount(1);

    Files.createLink(path("/link.txt"), path("/file.txt"));

    assertThat("/link.txt").isSameFileAs("/file.txt");

    assertThat("/file.txt").hasLinkCount(2);
    assertThat("/link.txt").hasLinkCount(2);

    assertThat("/file.txt").containsNoBytes();
    assertThat("/link.txt").containsNoBytes();

    byte[] bytes = {0, 1, 2, 3};
    Files.write(path("/file.txt"), bytes);

    assertThat("/file.txt").containsBytes(bytes);
    assertThat("/link.txt").containsBytes(bytes);

    Files.write(path("/link.txt"), bytes, APPEND);

    assertThat("/file.txt").containsBytes(0, 1, 2, 3, 0, 1, 2, 3);
    assertThat("/link.txt").containsBytes(0, 1, 2, 3, 0, 1, 2, 3);

    Files.delete(path("/file.txt"));
    assertThat("/link.txt").hasLinkCount(1);

    assertThat("/link.txt").containsBytes(0, 1, 2, 3, 0, 1, 2, 3);
  }

  @Test
  public void testLink_forSymbolicLink_usesSymbolicLinkTarget() throws IOException {
    Files.createFile(path("/file"));
    Files.createSymbolicLink(path("/symlink"), path("/file"));

    Object key = getFileKey("/file");

    Files.createLink(path("/link"), path("/symlink"));

    assertThat("/link").isRegularFile()
        .and().hasLinkCount(2)
        .and().attribute("fileKey").isEqualTo(key);
  }

  @Test
  public void testLink_failsWhenTargetDoesNotExist() throws IOException {
    try {
      Files.createLink(path("/link"), path("/foo"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/foo", expected.getFile());
    }

    Files.createSymbolicLink(path("/foo"), path("/bar"));

    try {
      Files.createLink(path("/link"), path("/foo"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/foo", expected.getFile());
    }
  }

  @Test
  public void testLink_failsForNonRegularFile() throws IOException {
    Files.createDirectory(path("/dir"));

    try {
      Files.createLink(path("/link"), path("/dir"));
      fail();
    } catch (FileSystemException expected) {
      assertEquals("/link", expected.getFile());
      assertEquals("/dir", expected.getOtherFile());
    }

    assertThat("/link").doesNotExist();
  }

  @Test
  public void testLinks_failsWhenTargetFileAlreadyExists() throws IOException {
    Files.createFile(path("/file"));
    Files.createFile(path("/link"));

    try {
      Files.createLink(path("/link"), path("/file"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/link", expected.getFile());
    }
  }

  @Test
  public void testStreams() throws IOException {
    try (OutputStream out = Files.newOutputStream(path("/test"))) {
      for (int i = 0; i < 100; i++) {
        out.write(i);
      }
    }

    byte[] expected = new byte[100];
    for (byte i = 0; i < 100; i++) {
      expected[i] = i;
    }

    try (InputStream in = Files.newInputStream(path("/test"))) {
      byte[] bytes = new byte[100];
      ByteStreams.readFully(in, bytes);
      assertArrayEquals(expected, bytes);
    }

    try (Writer writer = Files.newBufferedWriter(path("/test.txt"), UTF_8)) {
      writer.write("hello");
    }

    try (Reader reader = Files.newBufferedReader(path("/test.txt"), UTF_8)) {
      assertEquals("hello", CharStreams.toString(reader));
    }

    try (Writer writer = Files.newBufferedWriter(path("/test.txt"), UTF_8, APPEND)) {
      writer.write(" world");
    }

    try (Reader reader = Files.newBufferedReader(path("/test.txt"), UTF_8)) {
      assertEquals("hello world", CharStreams.toString(reader));
    }
  }

  @Test
  public void testChannels() throws IOException {
    try (FileChannel channel = FileChannel.open(path("/test.txt"), CREATE_NEW, WRITE)) {
      ByteBuffer buf1 = UTF_8.encode("hello");
      ByteBuffer buf2 = UTF_8.encode(" world");
      while (buf1.hasRemaining() || buf2.hasRemaining()) {
        channel.write(new ByteBuffer[]{buf1, buf2});
      }
    }

    try (SeekableByteChannel channel = Files.newByteChannel(path("/test.txt"), READ)) {
      ByteBuffer buffer = ByteBuffer.allocate(100);
      while (channel.read(buffer) != -1) {
      }
      buffer.flip();
      assertEquals("hello world", UTF_8.decode(buffer).toString());
    }

    byte[] bytes = preFilledBytes(100);

    Files.write(path("/test"), bytes);

    try (SeekableByteChannel channel = Files.newByteChannel(path("/test"), READ, WRITE)) {
      ByteBuffer buffer = ByteBuffer.wrap(preFilledBytes(50));

      channel.position(50);
      channel.write(buffer);
      buffer.flip();
      channel.write(buffer);

      channel.position(0);
      ByteBuffer readBuffer = ByteBuffer.allocate(150);
      while (readBuffer.hasRemaining()) {
        channel.read(readBuffer);
      }

      byte[] expected = concat(preFilledBytes(50), preFilledBytes(50), preFilledBytes(50));

      assertArrayEquals(expected, readBuffer.array());

    }

    try (FileChannel channel = FileChannel.open(path("/test"), READ, WRITE)) {
      assertEquals(150, channel.size());

      channel.truncate(10);
      assertEquals(10, channel.size());

      ByteBuffer buffer = ByteBuffer.allocate(20);
      assertEquals(10, channel.read(buffer));
      buffer.flip();

      byte[] expected = new byte[20];
      System.arraycopy(preFilledBytes(10), 0, expected, 0, 10);
      assertArrayEquals(expected, buffer.array());
    }
  }

  @Test
  public void testCopy_inputStreamToFile() throws IOException {
    byte[] bytes = preFilledBytes(512);

    Files.copy(new ByteArrayInputStream(bytes), path("/test"));
    assertThat("/test").containsBytes(bytes);

    try {
      Files.copy(new ByteArrayInputStream(bytes), path("/test"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/test", expected.getMessage());
    }

    Files.copy(new ByteArrayInputStream(bytes), path("/test"), REPLACE_EXISTING);
    assertThat("/test").containsBytes(bytes);

    Files.copy(new ByteArrayInputStream(bytes), path("/foo"), REPLACE_EXISTING);
    assertThat("/foo").containsBytes(bytes);
  }

  @Test
  public void testCopy_fileToOutputStream() throws IOException {
    byte[] bytes = preFilledBytes(512);
    Files.write(path("/test"), bytes);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Files.copy(path("/test"), out);
    assertArrayEquals(bytes, out.toByteArray());

    try {
      Files.copy(path("/foo"), path("/bar"));
      fail();
    } catch (NoSuchFileException expected) {
      assertEquals("/foo", expected.getMessage());
    }
  }

  @Test
  public void testCopy_fileToPath() throws IOException {
    byte[] bytes = preFilledBytes(512);
    Files.write(path("/foo"), bytes);

    assertThat("/bar").doesNotExist();
    Files.copy(path("/foo"), path("/bar"));
    assertThat("/bar").containsBytes(bytes);

    byte[] moreBytes = preFilledBytes(2048);
    Files.write(path("/baz"), moreBytes);

    Files.copy(path("/baz"), path("/bar"), REPLACE_EXISTING);
    assertThat("/bar").containsBytes(moreBytes);
  }

  @Test
  public void testCopy_doesNotSupportAtomicMove() throws IOException {
    try {
      Files.copy(path("/foo"), path("/bar"), ATOMIC_MOVE);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testCopy_directoryToPath() throws IOException {
    Files.createDirectory(path("/foo"));

    assertThat("/bar").doesNotExist();
    Files.copy(path("/foo"), path("/bar"));
    assertThat("/bar").isDirectory();
  }

  @Test
  public void testCopy_withoutReplaceExisting_failsWhenTargetExists() throws IOException {
    Files.createFile(path("/bar"));
    Files.createDirectory(path("/foo"));

    // dir -> file
    try {
      Files.copy(path("/foo"), path("/bar"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/bar", expected.getMessage());
    }

    Files.delete(path("/foo"));
    Files.createFile(path("/foo"));

    // file -> file
    try {
      Files.copy(path("/foo"), path("/bar"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/bar", expected.getMessage());
    }

    Files.delete(path("/bar"));
    Files.createDirectory(path("/bar"));

    // file -> dir
    try {
      Files.copy(path("/foo"), path("/bar"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/bar", expected.getMessage());
    }

    Files.delete(path("/foo"));
    Files.createDirectory(path("/foo"));

    // dir -> dir
    try {
      Files.copy(path("/foo"), path("/bar"));
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/bar", expected.getMessage());
    }
  }

  @Test
  public void testCopy_withReplaceExisting() throws IOException {
    Files.createFile(path("/bar"));
    Files.createDirectory(path("/test"));

    assertThat("/bar").isRegularFile();

    // overwrite regular file w/ directory
    Files.copy(path("/test"), path("/bar"), REPLACE_EXISTING);

    assertThat("/bar").isDirectory();

    byte[] bytes = {0, 1, 2, 3};
    Files.write(path("/baz"), bytes);

    // overwrite directory w/ regular file
    Files.copy(path("/baz"), path("/bar"), REPLACE_EXISTING);

    assertThat("/bar").containsSameBytesAs("/baz");
  }

  @Test
  public void testCopy_withReplaceExisting_cantReplaceNonEmptyDirectory() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createDirectory(path("/foo/bar"));
    Files.createFile(path("/foo/baz"));

    Files.createDirectory(path("/test"));

    try {
      Files.copy(path("/test"), path("/foo"), REPLACE_EXISTING);
      fail();
    } catch (DirectoryNotEmptyException expected) {
      assertEquals("/foo", expected.getMessage());
    }

    Files.delete(path("/test"));
    Files.createFile(path("/test"));

    try {
      Files.copy(path("/test"), path("/foo"), REPLACE_EXISTING);
      fail();
    } catch (DirectoryNotEmptyException expected) {
      assertEquals("/foo", expected.getMessage());
    }

    Files.delete(path("/foo/baz"));
    Files.delete(path("/foo/bar"));

    Files.copy(path("/test"), path("/foo"), REPLACE_EXISTING);
    assertThat("/foo").isRegularFile(); // replaced
  }

  @Test
  public void testCopy_directoryToPath_doesNotCopyDirectoryContents() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createDirectory(path("/foo/baz"));
    Files.createFile(path("/foo/test"));

    Files.copy(path("/foo"), path("/bar"));
    assertThat("/bar").hasNoChildren();
  }

  @Test
  public void testCopy_symbolicLinkToPath() throws IOException {
    byte[] bytes = preFilledBytes(128);
    Files.write(path("/test"), bytes);
    Files.createSymbolicLink(path("/link"), path("/test"));

    assertThat("/bar").doesNotExist();
    Files.copy(path("/link"), path("/bar"));
    assertThat("/bar", NOFOLLOW_LINKS).containsBytes(bytes);

    Files.delete(path("/bar"));

    Files.copy(path("/link"), path("/bar"), NOFOLLOW_LINKS);
    assertThat("/bar", NOFOLLOW_LINKS)
        .isSymbolicLink().withTarget("/test");
    assertThat("/bar").isRegularFile();
    assertThat("/bar").containsBytes(bytes);

    Files.delete(path("/test"));
    assertThat("/bar", NOFOLLOW_LINKS).isSymbolicLink();
    assertThat("/bar").doesNotExist();
  }

  @Test
  public void testMove() throws IOException {
    byte[] bytes = preFilledBytes(100);
    Files.write(path("/foo"), bytes);

    Object fooKey = getFileKey("/foo");

    Files.move(path("/foo"), path("/bar"));
    assertThat("/foo").doesNotExist()
        .andThat("/bar")
        .containsBytes(bytes).and()
        .attribute("fileKey").isEqualTo(fooKey);

    Files.createDirectory(path("/foo"));
    Files.move(path("/bar"), path("/foo/bar"));

    assertThat("/bar").doesNotExist()
        .andThat("/foo/bar").isRegularFile();

    Files.move(path("/foo"), path("/baz"));
    assertThat("/foo").doesNotExist()
        .andThat("/baz").isDirectory()
        .andThat("/baz/bar").isRegularFile();
  }

  @Test
  public void testMove_cannotMoveDirIntoOwnSubtree() throws IOException {
    Files.createDirectories(path("/foo"));

    try {
      Files.move(path("/foo"), path("/foo/bar"));
      fail();
    } catch (IOException expected) {
      ASSERT.that(expected.getMessage()).contains("sub");
    }

    Files.createDirectories(path("/foo/bar/baz/stuff"));
    Files.createDirectories(path("/hello/world"));
    Files.createSymbolicLink(path("/hello/world/link"), path("../../foo/bar/baz"));

    try {
      Files.move(path("/foo/bar"), path("/hello/world/link/bar"));
      fail();
    } catch (IOException expected) {
      ASSERT.that(expected.getMessage()).contains("sub");
    }
  }

  @Test
  public void testMove_withoutReplaceExisting_failsWhenTargetExists() throws IOException {
    byte[] bytes = preFilledBytes(50);
    Files.write(path("/test"), bytes);

    Object testKey = getFileKey("/test");

    Files.createFile(path("/bar"));

    try {
      Files.move(path("/test"), path("/bar"), ATOMIC_MOVE);
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/bar", expected.getMessage());
    }

    assertThat("/test")
        .containsBytes(bytes).and()
        .attribute("fileKey").isEqualTo(testKey);

    Files.delete(path("/bar"));
    Files.createDirectory(path("/bar"));

    try {
      Files.move(path("/test"), path("/bar"), ATOMIC_MOVE);
      fail();
    } catch (FileAlreadyExistsException expected) {
      assertEquals("/bar", expected.getMessage());
    }

    assertThat("/test")
        .containsBytes(bytes).and()
        .attribute("fileKey").isEqualTo(testKey);
  }

  @Test
  public void testIsSameFile() throws IOException {
    Files.createDirectory(path("/foo"));
    Files.createSymbolicLink(path("/bar"), path("/foo"));
    Files.createFile(path("/bar/test"));

    assertThat("/foo").isSameFileAs("/foo");
    assertThat("/bar").isSameFileAs("/bar");
    assertThat("/foo/test").isSameFileAs("/foo/test");
    assertThat("/bar/test").isSameFileAs("/bar/test");
    assertThat("/foo").isNotSameFileAs("test");
    assertThat("/bar").isNotSameFileAs("/test");
    assertThat("/foo").isSameFileAs("/bar");
    assertThat("/foo/test").isSameFileAs("/bar/test");

    Files.createSymbolicLink(path("/baz"), path("bar")); // relative path
    assertThat("/baz").isSameFileAs("/foo");
    assertThat("/baz/test").isSameFileAs("/foo/test");
  }

  @Test
  public void testIsSameFile_forPathFromDifferentFileSystemProvider() throws IOException {
    Path defaultFileSystemRoot = FileSystems.getDefault()
        .getRootDirectories().iterator().next();

    ASSERT.that(Files.isSameFile(path("/"), defaultFileSystemRoot)).isFalse();
  }

  @Test
  public void testPathLookups() throws IOException {
    assertThat("/").isSameFileAs("/");
    assertThat("/..").isSameFileAs("/");
    assertThat("/../../..").isSameFileAs("/");
    assertThat("../../../..").isSameFileAs("/");

    //System.err.println(getFileKey(""));

    //assertThat("/work").isSameFileAs("");

    Files.createDirectories(path("/foo/bar/baz"));
    Files.createSymbolicLink(path("/foo/bar/link1"), path("../link2"));
    Files.createSymbolicLink(path("/foo/link2"), path("/"));

    assertThat("/foo/bar/link1/foo/bar/link1/foo").isSameFileAs("/foo");
  }

  // helpers

  private Path path(String first, String... more) {
    return fs.getPath(first, more);
  }

  private Object getFileKey(String path, LinkOption... options) throws IOException {
    return Files.getAttribute(path(path), "fileKey", options);
  }

  private PathSubject assertThat(String path, LinkOption... options) {
    return assertThat(path(path), options);
  }

  private static PathSubject assertThat(Path path, LinkOption... options) {
    PathSubject subject = ASSERT.about(paths()).that(path);
    if (options.length != 0) {
      subject = subject.noFollowLinks();
    }
    return subject;
  }
}
