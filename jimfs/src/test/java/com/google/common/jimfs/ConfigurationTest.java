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

import static com.google.common.jimfs.PathNormalization.CASE_FOLD_ASCII;
import static com.google.common.jimfs.PathNormalization.CASE_FOLD_UNICODE;
import static com.google.common.jimfs.PathNormalization.NFC;
import static com.google.common.jimfs.PathNormalization.NFD;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Tests for {@link Configuration}, {@link Configuration.Builder} and file systems created from
 * them.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class ConfigurationTest {

  @Test
  public void testDefaultUnixConfiguration() {
    Configuration config = Configuration.unix();

    ASSERT.that(config.pathType).is(PathType.unix());
    ASSERT.that(config.roots).has().exactly("/");
    ASSERT.that(config.workingDirectory).is("/work");
    ASSERT.that(config.nameCanonicalNormalization).isEmpty();
    ASSERT.that(config.nameDisplayNormalization).isEmpty();
    ASSERT.that(config.pathEqualityUsesCanonicalForm).isFalse();
    ASSERT.that(config.blockSize).is(8192);
    ASSERT.that(config.maxSize).is(4L * 1024 * 1024 * 1024);
    ASSERT.that(config.maxCacheSize).is(-1);
    ASSERT.that(config.attributeViews).has().exactly("basic");
    ASSERT.that(config.attributeProviders).isEmpty();
    ASSERT.that(config.defaultAttributeValues).isEmpty();
  }

  @Test
  public void testFileSystemForDefaultUnixConfiguration() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

    ASSERT.that(fs.getRootDirectories()).iteratesAs(ImmutableList.of(fs.getPath("/")));
    ASSERT.that(fs.getPath("").toRealPath()).is(fs.getPath("/work"));
    ASSERT.that(Iterables.getOnlyElement(fs.getFileStores()).getTotalSpace())
        .is(4L * 1024 * 1024 * 1024);
    ASSERT.that(fs.supportedFileAttributeViews()).has().exactly("basic");

    Files.createFile(fs.getPath("/foo"));
    Files.createFile(fs.getPath("/FOO"));
  }

  @Test
  public void testDefaultOsXConfiguration() {
    Configuration config = Configuration.osX();

    ASSERT.that(config.pathType).is(PathType.unix());
    ASSERT.that(config.roots).has().exactly("/");
    ASSERT.that(config.workingDirectory).is("/work");
    ASSERT.that(config.nameCanonicalNormalization).has().exactly(NFD, CASE_FOLD_ASCII);
    ASSERT.that(config.nameDisplayNormalization).has().exactly(NFC);
    ASSERT.that(config.pathEqualityUsesCanonicalForm).isFalse();
    ASSERT.that(config.blockSize).is(8192);
    ASSERT.that(config.maxSize).is(4L * 1024 * 1024 * 1024);
    ASSERT.that(config.maxCacheSize).is(-1);
    ASSERT.that(config.attributeViews).has().exactly("basic");
    ASSERT.that(config.attributeProviders).isEmpty();
    ASSERT.that(config.defaultAttributeValues).isEmpty();
  }

  @Test
  public void testFileSystemForDefaultOsXConfiguration() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.osX());

    ASSERT.that(fs.getRootDirectories()).iteratesAs(ImmutableList.of(fs.getPath("/")));
    ASSERT.that(fs.getPath("").toRealPath()).is(fs.getPath("/work"));
    ASSERT.that(Iterables.getOnlyElement(fs.getFileStores()).getTotalSpace())
        .is(4L * 1024 * 1024 * 1024);
    ASSERT.that(fs.supportedFileAttributeViews()).has().exactly("basic");

    Files.createFile(fs.getPath("/foo"));

    try {
      Files.createFile(fs.getPath("/FOO"));
      fail();
    } catch (FileAlreadyExistsException expected) {
    }
  }

  @Test
  public void testDefaultWindowsConfiguration() {
    Configuration config = Configuration.windows();

    ASSERT.that(config.pathType).is(PathType.windows());
    ASSERT.that(config.roots).has().exactly("C:\\");
    ASSERT.that(config.workingDirectory).is("C:\\work");
    ASSERT.that(config.nameCanonicalNormalization).has().exactly(CASE_FOLD_ASCII);
    ASSERT.that(config.nameDisplayNormalization).isEmpty();
    ASSERT.that(config.pathEqualityUsesCanonicalForm).isTrue();
    ASSERT.that(config.blockSize).is(8192);
    ASSERT.that(config.maxSize).is(4L * 1024 * 1024 * 1024);
    ASSERT.that(config.maxCacheSize).is(-1);
    ASSERT.that(config.attributeViews).has().exactly("basic");
    ASSERT.that(config.attributeProviders).isEmpty();
    ASSERT.that(config.defaultAttributeValues).isEmpty();
  }

  @Test
  public void testFileSystemForDefaultWindowsConfiguration() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.windows());

    ASSERT.that(fs.getRootDirectories()).iteratesAs(ImmutableList.of(fs.getPath("C:\\")));
    ASSERT.that(fs.getPath("").toRealPath()).is(fs.getPath("C:\\work"));
    ASSERT.that(Iterables.getOnlyElement(fs.getFileStores()).getTotalSpace())
        .is(4L * 1024 * 1024 * 1024);
    ASSERT.that(fs.supportedFileAttributeViews()).has().exactly("basic");

    Files.createFile(fs.getPath("C:\\foo"));

    try {
      Files.createFile(fs.getPath("C:\\FOO"));
      fail();
    } catch (FileAlreadyExistsException expected) {
    }
  }

  @Test
  public void testBuilder() {
    AttributeProvider unixProvider = StandardAttributeProviders.get("unix");

    Configuration config = Configuration.builder(PathType.unix())
        .setRoots("/")
        .setWorkingDirectory("/hello/world")
        .setNameCanonicalNormalization(NFD, CASE_FOLD_UNICODE)
        .setNameDisplayNormalization(NFC)
        .setPathEqualityUsesCanonicalForm(true)
        .setBlockSize(10)
        .setMaxSize(100)
        .setMaxCacheSize(50)
        .setAttributeViews("basic", "posix")
        .addAttributeProvider(unixProvider)
        .setDefaultAttributeValue(
            "posix:permissions", PosixFilePermissions.fromString("---------"))
        .build();

    ASSERT.that(config.pathType).is(PathType.unix());
    ASSERT.that(config.roots).has().exactly("/");
    ASSERT.that(config.workingDirectory).is("/hello/world");
    ASSERT.that(config.nameCanonicalNormalization).has().exactly(NFD, CASE_FOLD_UNICODE);
    ASSERT.that(config.nameDisplayNormalization).has().exactly(NFC);
    ASSERT.that(config.pathEqualityUsesCanonicalForm).isTrue();
    ASSERT.that(config.blockSize).is(10);
    ASSERT.that(config.maxSize).is(100);
    ASSERT.that(config.maxCacheSize).is(50);
    ASSERT.that(config.attributeViews).has().exactly("basic", "posix");
    ASSERT.that(config.attributeProviders).has().exactly(unixProvider);
    ASSERT.that(config.defaultAttributeValues)
        .hasKey("posix:permissions").withValue(PosixFilePermissions.fromString("---------"));
  }

  @Test
  public void testFileSystemForCustomConfiguration() throws IOException {
    Configuration config = Configuration.builder(PathType.unix())
        .setRoots("/")
        .setWorkingDirectory("/hello/world")
        .setNameCanonicalNormalization(NFD, CASE_FOLD_UNICODE)
        .setNameDisplayNormalization(NFC)
        .setPathEqualityUsesCanonicalForm(true)
        .setBlockSize(10)
        .setMaxSize(100)
        .setMaxCacheSize(50)
        .setAttributeViews("unix")
        .setDefaultAttributeValue(
            "posix:permissions", PosixFilePermissions.fromString("---------"))
        .build();

    FileSystem fs = Jimfs.newFileSystem(config);

    ASSERT.that(fs.getRootDirectories()).iteratesAs(ImmutableList.of(fs.getPath("/")));
    ASSERT.that(fs.getPath("").toRealPath()).is(fs.getPath("/hello/world"));
    ASSERT.that(Iterables.getOnlyElement(fs.getFileStores()).getTotalSpace())
        .is(100);
    ASSERT.that(fs.supportedFileAttributeViews()).has().exactly("basic", "owner", "posix", "unix");

    Files.createFile(fs.getPath("/foo"));
    ASSERT.that(Files.getAttribute(fs.getPath("/foo"), "posix:permissions"))
        .is(PosixFilePermissions.fromString("---------"));

    try {
      Files.createFile(fs.getPath("/FOO"));
      fail();
    } catch (FileAlreadyExistsException expected) {
    }
  }

  @Test
  public void testToBuilder() {
    Configuration config = Configuration.unix().toBuilder()
        .setWorkingDirectory("/hello/world")
        .setAttributeViews("basic", "posix")
        .build();

    ASSERT.that(config.pathType).is(PathType.unix());
    ASSERT.that(config.roots).has().exactly("/");
    ASSERT.that(config.workingDirectory).is("/hello/world");
    ASSERT.that(config.nameCanonicalNormalization).isEmpty();
    ASSERT.that(config.nameDisplayNormalization).isEmpty();
    ASSERT.that(config.pathEqualityUsesCanonicalForm).isFalse();
    ASSERT.that(config.blockSize).is(8192);
    ASSERT.that(config.maxSize).is(4L * 1024 * 1024 * 1024);
    ASSERT.that(config.maxCacheSize).is(-1);
    ASSERT.that(config.attributeViews).has().exactly("basic", "posix");
    ASSERT.that(config.attributeProviders).isEmpty();
    ASSERT.that(config.defaultAttributeValues).isEmpty();
  }

  @Test
  public void testSettingRootsUnsupportedByPathType() {
    assertIllegalRoots(PathType.unix(), "\\");
    assertIllegalRoots(PathType.unix(), "/", "\\");
    assertIllegalRoots(PathType.windows(), "/");
    assertIllegalRoots(PathType.windows(), "C:"); // must have a \ (or a /)
  }

  private static void assertIllegalRoots(PathType type, String first, String... more) {
    try {
      Configuration.builder(type).setRoots(first, more); // wrong root
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testSettingWorkingDirectoryWithRelativePath() {
    try {
      Configuration.unix().toBuilder().setWorkingDirectory("foo/bar");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      Configuration.windows().toBuilder().setWorkingDirectory("foo\\bar");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testSettingNormalizationWhenNormalizationAlreadySet() {
    assertIllegalNormalizations(NFC, NFC);
    assertIllegalNormalizations(NFC, NFD);
    assertIllegalNormalizations(CASE_FOLD_ASCII, CASE_FOLD_ASCII);
    assertIllegalNormalizations(CASE_FOLD_ASCII, CASE_FOLD_UNICODE);
  }

  private static void assertIllegalNormalizations(PathNormalization first, PathNormalization... more) {
    try {
      Configuration.builder(PathType.unix()).setNameCanonicalNormalization(first, more);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      Configuration.builder(PathType.unix()).setNameDisplayNormalization(first, more);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testSetDefaultAttributeValue_illegalAttributeFormat() {
    try {
      Configuration.unix().toBuilder().setDefaultAttributeValue("foo", 1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test // how's that for a name?
  public void testCreateFileSystemFromConfigurationWithWorkingDirectoryNotUnderConfiguredRoot() {
    try {
      Jimfs.newFileSystem(Configuration.windows().toBuilder()
          .setRoots("C:\\", "D:\\")
          .setWorkingDirectory("E:\\foo")
          .build());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
