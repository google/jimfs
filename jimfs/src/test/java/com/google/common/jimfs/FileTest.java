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

import static com.google.common.jimfs.FileFactoryTest.fakePath;
import static com.google.common.jimfs.TestUtils.regularFile;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link File}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class FileTest {

  @Test
  public void testAttributes() {
    // these methods are basically just thin wrappers around a map, so no need to test too
    // thoroughly

    File file = RegularFile.create(0, new HeapDisk(10, 10, 10));

    assertThat(file.getAttributeKeys()).isEmpty();
    assertThat(file.getAttribute("foo", "foo")).isNull();

    file.deleteAttribute("foo", "foo"); // doesn't throw

    file.setAttribute("foo", "foo", "foo");

    assertThat(file.getAttributeKeys()).containsExactly("foo:foo");
    assertThat(file.getAttribute("foo", "foo")).isEqualTo("foo");

    file.deleteAttribute("foo", "foo");

    assertThat(file.getAttributeKeys()).isEmpty();
    assertThat(file.getAttribute("foo", "foo")).isNull();
  }

  @Test
  public void testFileBasics() {
    File file = regularFile(0);

    assertThat(file.id()).isEqualTo(0);
    assertThat(file.links()).isEqualTo(0);
  }

  @Test
  public void testDirectory() {
    File file = Directory.create(0);
    assertThat(file.isDirectory()).isTrue();
    assertThat(file.isRegularFile()).isFalse();
    assertThat(file.isSymbolicLink()).isFalse();
  }

  @Test
  public void testRegularFile() {
    File file = regularFile(10);
    assertThat(file.isDirectory()).isFalse();
    assertThat(file.isRegularFile()).isTrue();
    assertThat(file.isSymbolicLink()).isFalse();
  }

  @Test
  public void testSymbolicLink() {
    File file = SymbolicLink.create(0, fakePath());
    assertThat(file.isDirectory()).isFalse();
    assertThat(file.isRegularFile()).isFalse();
    assertThat(file.isSymbolicLink()).isTrue();
  }

  @Test
  public void testRootDirectory() {
    Directory file = Directory.createRoot(0, Name.simple("/"));
    assertThat(file.isRootDirectory()).isTrue();

    Directory otherFile = Directory.createRoot(1, Name.simple("$"));
    assertThat(otherFile.isRootDirectory()).isTrue();
  }

  @Test
  public void testLinkAndUnlink() {
    File file = regularFile(0);
    assertThat(file.links()).isEqualTo(0);

    file.incrementLinkCount();
    assertThat(file.links()).isEqualTo(1);

    file.incrementLinkCount();
    assertThat(file.links()).isEqualTo(2);

    file.decrementLinkCount();
    assertThat(file.links()).isEqualTo(1);

    file.decrementLinkCount();
    assertThat(file.links()).isEqualTo(0);
  }
}
