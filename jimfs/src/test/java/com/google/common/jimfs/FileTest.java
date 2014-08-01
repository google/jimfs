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
import static com.google.common.truth.Truth.ASSERT;

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

    ASSERT.that(file.getAttributeKeys()).isEmpty();
    ASSERT.that(file.getAttribute("foo", "foo")).isNull();

    file.deleteAttribute("foo", "foo"); // doesn't throw

    file.setAttribute("foo", "foo", "foo");

    ASSERT.that(file.getAttributeKeys()).iteratesAs("foo:foo");
    ASSERT.that(file.getAttribute("foo", "foo")).isEqualTo("foo");

    file.deleteAttribute("foo", "foo");

    ASSERT.that(file.getAttributeKeys()).isEmpty();
    ASSERT.that(file.getAttribute("foo", "foo")).isNull();
  }

  @Test
  public void testFileBasics() {
    File file = regularFile(0);

    ASSERT.that(file.id()).is(0);
    ASSERT.that(file.links()).is(0);
  }

  @Test
  public void testDirectory() {
    File file = Directory.create(0);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.isRegularFile()).isFalse();
    ASSERT.that(file.isSymbolicLink()).isFalse();
  }

  @Test
  public void testRegularFile() {
    File file = regularFile(10);
    ASSERT.that(file.isDirectory()).isFalse();
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.isSymbolicLink()).isFalse();
  }

  @Test
  public void testSymbolicLink() {
    File file = SymbolicLink.create(0, fakePath());
    ASSERT.that(file.isDirectory()).isFalse();
    ASSERT.that(file.isRegularFile()).isFalse();
    ASSERT.that(file.isSymbolicLink()).isTrue();
  }

  @Test
  public void testRootDirectory() {
    Directory file = Directory.createRoot(0, Name.simple("/"));
    ASSERT.that(file.isRootDirectory()).isTrue();

    Directory otherFile = Directory.createRoot(1, Name.simple("$"));
    ASSERT.that(otherFile.isRootDirectory()).isTrue();
  }

  @Test
  public void testLinkAndUnlink() {
    File file = regularFile(0);
    ASSERT.that(file.links()).is(0);

    file.incrementLinkCount();
    ASSERT.that(file.links()).is(1);

    file.incrementLinkCount();
    ASSERT.that(file.links()).is(2);

    file.decrementLinkCount();
    ASSERT.that(file.links()).is(1);

    file.decrementLinkCount();
    ASSERT.that(file.links()).is(0);
  }
}
