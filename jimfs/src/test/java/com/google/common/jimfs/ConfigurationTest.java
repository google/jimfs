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
import static com.google.common.jimfs.PathSubject.paths;
import static com.google.common.jimfs.WatchServiceConfiguration.polling;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link Configuration}, {@link Configuration.Builder} and file systems created from
 * them.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class ConfigurationTest {

  private static PathSubject assertThatPath(Path path) {
    return assert_().about(paths()).that(path);
  }

  @Test
  public void testDefaultUnixConfiguration() {
    Configuration config = Configuration.unix();

    assertThat(config.pathType).isEqualTo(PathType.unix());
    assertThat(config.roots).containsExactly("/");
    assertThat(config.workingDirectory).isEqualTo("/work");
    assertThat(config.nameCanonicalNormalization).isEmpty();
    assertThat(config.nameDisplayNormalization).isEmpty();
    assertThat(config.pathEqualityUsesCanonicalForm).isFalse();
    assertThat(config.blockSize).isEqualTo(8192);
    assertThat(config.maxSize).isEqualTo(4L * 1024 * 1024 * 1024);
    assertThat(config.maxCacheSize).isEqualTo(-1);
    assertThat(config.attributeViews).containsExactly("basic");
    assertThat(config.attributeProviders).isEmpty();
    assertThat(config.defaultAttributeValues).isEmpty();
  }

  @Test
  public void testFileSystemForDefaultUnixConfiguration() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

    assertThat(fs.getRootDirectories())
        .containsExactlyElementsIn(ImmutableList.of(fs.getPath("/")))
        .inOrder();
    assertThatPath(fs.getPath("").toRealPath()).isEqualTo(fs.getPath("/work"));
    assertThat(Iterables.getOnlyElement(fs.getFileStores()).getTotalSpace())
        .isEqualTo(4L * 1024 * 1024 * 1024);
    assertThat(fs.supportedFileAttributeViews()).containsExactly("basic");

    Files.createFile(fs.getPath("/foo"));
    Files.createFile(fs.getPath("/FOO"));
  }

  @Test
  public void testDefaultOsXConfiguration() {
    Configuration config = Configuration.osX();

    assertThat(config.pathType).isEqualTo(PathType.unix());
    assertThat(config.roots).containsExactly("/");
    assertThat(config.workingDirectory).isEqualTo("/work");
    assertThat(config.nameCanonicalNormalization).containsExactly(NFD, CASE_FOLD_ASCII);
    assertThat(config.nameDisplayNormalization).containsExactly(NFC);
    assertThat(config.pathEqualityUsesCanonicalForm).isFalse();
    assertThat(config.blockSize).isEqualTo(8192);
    assertThat(config.maxSize).isEqualTo(4L * 1024 * 1024 * 1024);
    assertThat(config.maxCacheSize).isEqualTo(-1);
    assertThat(config.attributeViews).containsExactly("basic");
    assertThat(config.attributeProviders).isEmpty();
    assertThat(config.defaultAttributeValues).isEmpty();
  }

  @Test
  public void testFileSystemForDefaultOsXConfiguration() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.osX());

    assertThat(fs.getRootDirectories())
        .containsExactlyElementsIn(ImmutableList.of(fs.getPath("/")))
        .inOrder();
    assertThatPath(fs.getPath("").toRealPath()).isEqualTo(fs.getPath("/work"));
    assertThat(Iterables.getOnlyElement(fs.getFileStores()).getTotalSpace())
        .isEqualTo(4L * 1024 * 1024 * 1024);
    assertThat(fs.supportedFileAttributeViews()).containsExactly("basic");

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

    assertThat(config.pathType).isEqualTo(PathType.windows());
    assertThat(config.roots).containsExactly("C:\\");
    assertThat(config.workingDirectory).isEqualTo("C:\\work");
    assertThat(config.nameCanonicalNormalization).containsExactly(CASE_FOLD_ASCII);
    assertThat(config.nameDisplayNormalization).isEmpty();
    assertThat(config.pathEqualityUsesCanonicalForm).isTrue();
    assertThat(config.blockSize).isEqualTo(8192);
    assertThat(config.maxSize).isEqualTo(4L * 1024 * 1024 * 1024);
    assertThat(config.maxCacheSize).isEqualTo(-1);
    assertThat(config.attributeViews).containsExactly("basic");
    assertThat(config.attributeProviders).isEmpty();
    assertThat(config.defaultAttributeValues).isEmpty();
  }

  @Test
  public void testFileSystemForDefaultWindowsConfiguration() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.windows());

    assertThat(fs.getRootDirectories())
        .containsExactlyElementsIn(ImmutableList.of(fs.getPath("C:\\")))
        .inOrder();
    assertThatPath(fs.getPath("").toRealPath()).isEqualTo(fs.getPath("C:\\work"));
    assertThat(Iterables.getOnlyElement(fs.getFileStores()).getTotalSpace())
        .isEqualTo(4L * 1024 * 1024 * 1024);
    assertThat(fs.supportedFileAttributeViews()).containsExactly("basic");

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

    Configuration config =
        Configuration.builder(PathType.unix())
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

    assertThat(config.pathType).isEqualTo(PathType.unix());
    assertThat(config.roots).containsExactly("/");
    assertThat(config.workingDirectory).isEqualTo("/hello/world");
    assertThat(config.nameCanonicalNormalization).containsExactly(NFD, CASE_FOLD_UNICODE);
    assertThat(config.nameDisplayNormalization).containsExactly(NFC);
    assertThat(config.pathEqualityUsesCanonicalForm).isTrue();
    assertThat(config.blockSize).isEqualTo(10);
    assertThat(config.maxSize).isEqualTo(100);
    assertThat(config.maxCacheSize).isEqualTo(50);
    assertThat(config.attributeViews).containsExactly("basic", "posix");
    assertThat(config.attributeProviders).containsExactly(unixProvider);
    assertThat(config.defaultAttributeValues)
        .containsEntry("posix:permissions", PosixFilePermissions.fromString("---------"));
  }

  @Test
  public void testFileSystemForCustomConfiguration() throws IOException {
    Configuration config =
        Configuration.builder(PathType.unix())
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

    assertThat(fs.getRootDirectories())
        .containsExactlyElementsIn(ImmutableList.of(fs.getPath("/")))
        .inOrder();
    assertThatPath(fs.getPath("").toRealPath()).isEqualTo(fs.getPath("/hello/world"));
    assertThat(Iterables.getOnlyElement(fs.getFileStores()).getTotalSpace()).isEqualTo(100);
    assertThat(fs.supportedFileAttributeViews()).containsExactly("basic", "owner", "posix", "unix");

    Files.createFile(fs.getPath("/foo"));
    assertThat(Files.getAttribute(fs.getPath("/foo"), "posix:permissions"))
        .isEqualTo(PosixFilePermissions.fromString("---------"));

    try {
      Files.createFile(fs.getPath("/FOO"));
      fail();
    } catch (FileAlreadyExistsException expected) {
    }
  }

  @Test
  public void testToBuilder() {
    Configuration config =
        Configuration.unix()
            .toBuilder()
            .setWorkingDirectory("/hello/world")
            .setAttributeViews("basic", "posix")
            .build();

    assertThat(config.pathType).isEqualTo(PathType.unix());
    assertThat(config.roots).containsExactly("/");
    assertThat(config.workingDirectory).isEqualTo("/hello/world");
    assertThat(config.nameCanonicalNormalization).isEmpty();
    assertThat(config.nameDisplayNormalization).isEmpty();
    assertThat(config.pathEqualityUsesCanonicalForm).isFalse();
    assertThat(config.blockSize).isEqualTo(8192);
    assertThat(config.maxSize).isEqualTo(4L * 1024 * 1024 * 1024);
    assertThat(config.maxCacheSize).isEqualTo(-1);
    assertThat(config.attributeViews).containsExactly("basic", "posix");
    assertThat(config.attributeProviders).isEmpty();
    assertThat(config.defaultAttributeValues).isEmpty();
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

  private static void assertIllegalNormalizations(
      PathNormalization first, PathNormalization... more) {
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
      Jimfs.newFileSystem(
          Configuration.windows()
              .toBuilder()
              .setRoots("C:\\", "D:\\")
              .setWorkingDirectory("E:\\foo")
              .build());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testFileSystemWithDefaultWatchService() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

    WatchService watchService = fs.newWatchService();
    assertThat(watchService).isInstanceOf(PollingWatchService.class);

    PollingWatchService pollingWatchService = (PollingWatchService) watchService;
    assertThat(pollingWatchService.interval).isEqualTo(5);
    assertThat(pollingWatchService.timeUnit).isEqualTo(SECONDS);
  }

  @Test
  public void testFileSystemWithCustomWatchServicePollingInterval() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
        .setWatchServiceConfiguration(polling(10, MILLISECONDS))
        .build());

    WatchService watchService = fs.newWatchService();
    assertThat(watchService).isInstanceOf(PollingWatchService.class);

    PollingWatchService pollingWatchService = (PollingWatchService) watchService;
    assertThat(pollingWatchService.interval).isEqualTo(10);
    assertThat(pollingWatchService.timeUnit).isEqualTo(MILLISECONDS);
  }
}
