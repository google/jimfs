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

package com.google.jimfs.internal;

import static com.google.jimfs.internal.FileFactoryTest.fakePath;
import static org.truth0.Truth.ASSERT;

import com.google.common.testing.EqualsTester;
import com.google.jimfs.path.PathType;

import org.junit.Test;

/**
 * Tests for {@link File}.
 *
 * @author Colin Decker
 */
public class FileTest {

  @Test
  public void testFileBasics() {
    FileContent content = new FakeFileContent();
    File file = new File(0L, content);

    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.content()).is(content);
    ASSERT.that(file.links()).is(0);
  }

  @Test
  public void testDirectory() {
    File file = new File(0L, new DirectoryTable());
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.isRegularFile()).isFalse();
    ASSERT.that(file.isSymbolicLink()).isFalse();
    ASSERT.that(file.content()).isA(DirectoryTable.class);
  }

  @Test
  public void testRegularFile() {
    File file = new File(0L, new StubByteStore(10));
    ASSERT.that(file.isDirectory()).isFalse();
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.isSymbolicLink()).isFalse();
    ASSERT.that(file.content()).isA(StubByteStore.class);
  }

  @Test
  public void testSymbolicLink() {
    File file = new File(0L, fakePath());
    ASSERT.that(file.isDirectory()).isFalse();
    ASSERT.that(file.isRegularFile()).isFalse();
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.content()).isA(JimfsPath.class);
  }

  @Test
  public void testRootDirectory() {
    DirectoryTable superRootTable = new DirectoryTable();
    File superRoot = new File(-1L, superRootTable);
    superRootTable.setSuperRoot(superRoot);

    DirectoryTable table = new DirectoryTable();
    File file = new File(0L, table);

    superRootTable.link(Name.simple("/"), file);
    table.setRoot();

    ASSERT.that(file.isRootDirectory()).isTrue();

    superRootTable.unlink(Name.simple("/"));

    DirectoryTable otherTable = new DirectoryTable();
    File otherFile = new File(1L, otherTable);
    superRootTable.link(Name.simple("$"), otherFile);
    otherTable.setRoot();

    otherTable.link(Name.simple("foo"), file);

    ASSERT.that(file.isRootDirectory()).isFalse();
  }

  @Test
  public void testLinkAndUnlink() {
    File file = new File(0L, new FakeFileContent());
    ASSERT.that(file.links()).is(0);

    file.linked();
    ASSERT.that(file.links()).is(1);

    file.linked();
    ASSERT.that(file.links()).is(2);

    file.unlinked();
    ASSERT.that(file.links()).is(1);

    file.unlinked();
    ASSERT.that(file.links()).is(0);
  }

  @Test
  public void testAttributes() {
    // these methods are basically just thin wrappers around a map, so no need to test too
    // thoroughly

    File file = new File(0L, new FakeFileContent());

    ASSERT.that(file.getAttributeKeys()).isEmpty();
    ASSERT.that(file.getAttribute("foo:foo")).isNull();

    file.deleteAttribute("foo:foo"); // doesn't throw

    file.setAttribute("foo:foo", "foo");

    ASSERT.that(file.getAttributeKeys()).iteratesAs("foo:foo");
    ASSERT.that(file.getAttribute("foo:foo")).is("foo");

    file.deleteAttribute("foo:foo");

    ASSERT.that(file.getAttributeKeys()).isEmpty();
    ASSERT.that(file.getAttribute("foo:foo")).isNull();
  }

  @Test
  public void testEquality() {
    File file1 = new File(0L, new FakeFileContent());
    File file2 = new File(0L, new DirectoryTable());
    File file3 = new File(1L, new StubByteStore(10));
    File file4 = new File(1L, new FakeFileContent());

    new EqualsTester()
        .addEqualityGroup(file1, file2)
        .addEqualityGroup(file3, file4)
        .testEquals();
  }
}
