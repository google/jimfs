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
  public void testFileBasics() {
    FileContent content = new FakeFileContent();
    File file = new File(0, content);

    ASSERT.that(file.id()).is(0);
    ASSERT.that(file.content()).is(content);
    ASSERT.that(file.links()).is(0);
  }

  @Test
  public void testDirectory() {
    File file = new File(0, new DirectoryTable());
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.isRegularFile()).isFalse();
    ASSERT.that(file.isSymbolicLink()).isFalse();
    ASSERT.that(file.content()).isA(DirectoryTable.class);
  }

  @Test
  public void testRegularFile() {
    File file = new File(0, new StubByteStore(10));
    ASSERT.that(file.isDirectory()).isFalse();
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.isSymbolicLink()).isFalse();
    ASSERT.that(file.content()).isA(StubByteStore.class);
  }

  @Test
  public void testSymbolicLink() {
    File file = new File(0, fakePath());
    ASSERT.that(file.isDirectory()).isFalse();
    ASSERT.that(file.isRegularFile()).isFalse();
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.content()).isA(JimfsPath.class);
  }

  @Test
  public void testRootDirectory() {
    DirectoryTable table = new DirectoryTable();
    File file = new File(0, table);
    table.setRoot(file, Name.simple("/"));
    ASSERT.that(file.isRootDirectory()).isTrue();

    DirectoryTable otherTable = new DirectoryTable();
    File otherFile = new File(1, otherTable);
    otherTable.setRoot(otherFile, Name.simple("$"));
    ASSERT.that(otherFile.isRootDirectory()).isTrue();
  }

  @Test
  public void testLinkAndUnlink() {
    File file = new File(0, new FakeFileContent());
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

  @Test
  public void testEquality() {
    File file1 = new File(0, new FakeFileContent());
    File file2 = new File(0, new DirectoryTable());
    File file3 = new File(1, new StubByteStore(10));
    File file4 = new File(1, new FakeFileContent());

    new EqualsTester()
        .addEqualityGroup(file1, file2)
        .addEqualityGroup(file3, file4)
        .testEquals();
  }
}
