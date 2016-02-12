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
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/**
 * @author Colin Decker
 */
public abstract class AbstractJimfsIntegrationTest {

  protected FileSystem fs;

  @Before
  public void setUp() throws IOException {
    fs = createFileSystem();
  }

  @After
  public void tearDown() throws IOException {
    fs.close();
  }

  /**
   * Creates the file system to use in the tests.
   */
  protected abstract FileSystem createFileSystem();

  // helpers

  protected Path path(String first, String... more) {
    return fs.getPath(first, more);
  }

  protected Object getFileKey(String path, LinkOption... options) throws IOException {
    return Files.getAttribute(path(path), "fileKey", options);
  }

  protected PathSubject assertThatPath(String path, LinkOption... options) {
    return assertThatPath(path(path), options);
  }

  protected static PathSubject assertThatPath(Path path, LinkOption... options) {
    PathSubject subject = assert_().about(paths()).that(path);
    if (options.length != 0) {
      subject = subject.noFollowLinks();
    }
    return subject;
  }

  /**
   * Tester for testing changes in file times.
   */
  protected static final class FileTimeTester {

    private final Path path;

    private FileTime accessTime;
    private FileTime modifiedTime;

    FileTimeTester(Path path) throws IOException {
      this.path = path;

      BasicFileAttributes attrs = attrs();
      accessTime = attrs.lastAccessTime();
      modifiedTime = attrs.lastModifiedTime();
    }

    private BasicFileAttributes attrs() throws IOException {
      return Files.readAttributes(path, BasicFileAttributes.class);
    }

    public void assertAccessTimeChanged() throws IOException {
      FileTime t = attrs().lastAccessTime();
      assertThat(t).isNotEqualTo(accessTime);
      accessTime = t;
    }

    public void assertAccessTimeDidNotChange() throws IOException {
      FileTime t = attrs().lastAccessTime();
      assertThat(t).isEqualTo(accessTime);
    }

    public void assertModifiedTimeChanged() throws IOException {
      FileTime t = attrs().lastModifiedTime();
      assertThat(t).isNotEqualTo(modifiedTime);
      modifiedTime = t;
    }

    public void assertModifiedTimeDidNotChange() throws IOException {
      FileTime t = attrs().lastModifiedTime();
      assertThat(t).isEqualTo(modifiedTime);
    }
  }
}
