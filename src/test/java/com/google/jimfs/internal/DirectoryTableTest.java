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

import static com.google.jimfs.internal.DirectoryTable.DirEntry;
import static com.google.jimfs.internal.Name.PARENT;
import static com.google.jimfs.internal.Name.SELF;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import com.ibm.icu.text.Normalizer2;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DirectoryTable}.
 *
 * @author Colin Decker
 */
public class DirectoryTableTest {

  private File rootFile;
  private File dirFile;

  private DirectoryTable root;
  private DirectoryTable table;

  @Before
  public void setUp() {
    rootFile = new File(0L, new DirectoryTable());
    root = rootFile.content();

    // root dir's parent is itself
    root.linkParent(rootFile);
    root.linkSelf(rootFile);

    dirFile = new File(1L, new DirectoryTable());
    table = dirFile.content();

    table.linkParent(rootFile);
    table.linkSelf(dirFile);

    root.link(Name.simple("foo"), dirFile);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testRootDirectory() {
    ASSERT.that(root.size()).is(3); // two for parent/self, one for table
    ASSERT.that(root.isEmpty()).isFalse();

    assertParentAndSelf(root, rootFile, rootFile);
  }

  @Test
  public void testEmptyDirectory() {
    ASSERT.that(table.size()).is(2);
    ASSERT.that(table.isEmpty()).isTrue();

    assertParentAndSelf(table, rootFile, dirFile);
  }

  @Test
  public void testGet() {
    ASSERT.that(root.get(Name.simple("foo"))).is(dirFile);
    ASSERT.that(table.get(Name.simple("foo"))).isNull();
    ASSERT.that(root.get(Name.simple("Foo"))).isNull();
  }

  @Test
  public void testLink() {
    ASSERT.that(table.get(Name.simple("bar"))).isNull();

    File bar = new File(2L, new DirectoryTable());
    table.link(Name.simple("bar"), bar);

    ASSERT.that(table.get(Name.simple("bar"))).is(bar);
  }

  @Test
  public void testLink_existingNameFails() {
    try {
      root.link(Name.simple("foo"), new File(2L, new DirectoryTable()));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testLink_parentAndSelfNameFails() {
    try {
      table.link(Name.simple("."), new File(2L, new DirectoryTable()));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      table.link(Name.simple(".."), new File(2L, new DirectoryTable()));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    // ensure that even if the parent/self entries do not already exist in the table,
    // they aren't allowed when calling link()
    File file = new File(2L, new DirectoryTable());
    DirectoryTable emptyTable = file.content();

    try {
      emptyTable.link(Name.simple("."), new File(2L, new DirectoryTable()));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      emptyTable.link(Name.simple(".."), new File(2L, new DirectoryTable()));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testGet_normalizingCaseInsensitive() {
    File bar = new File(2L, new DirectoryTable());
    Name barName = caseInsensitive("bar");

    table.link(barName, bar);

    ASSERT.that(table.get(caseInsensitive("bar"))).is(bar);
    ASSERT.that(table.get(caseInsensitive("BAR"))).is(bar);
    ASSERT.that(table.get(caseInsensitive("Bar"))).is(bar);
    ASSERT.that(table.get(caseInsensitive("baR"))).is(bar);
  }

  @Test
  public void testUnlink() {
    ASSERT.that(root.get(Name.simple("foo"))).isNotNull();

    root.unlink(Name.simple("foo"));

    ASSERT.that(root.get(Name.simple("foo"))).isNull();
  }

  @Test
  public void testUnlink_nonExistentNameFails() {
    try {
      table.unlink(Name.simple("bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testUnlink_parentAndSelfNameFails() {
    try {
      table.unlink(Name.simple("."));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      table.unlink(Name.simple(".."));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testUnlink_normalizingCaseInsensitive() {
    table.link(caseInsensitive("bar"), new File(2L, new DirectoryTable()));

    ASSERT.that(table.get(caseInsensitive("bar"))).isNotNull();

    table.unlink(caseInsensitive("BAR"));

    ASSERT.that(table.get(caseInsensitive("bar"))).isNull();
  }

  @Test
  public void testGetEntry() {
    table.link(caseInsensitive("bar"), new File(2L, new DirectoryTable()));

    DirEntry entry = table.getEntry(caseInsensitive("BAR"));
    ASSERT.that(entry.file().id()).is(2L);
    ASSERT.that(entry.name().toString()).is("bar");

    ASSERT.that(table.getEntry(caseInsensitive("none"))).isNull();
  }

  @Test
  public void testGetName() {
    File file = new File(2L, new ArrayByteStore());
    table.link(Name.simple("bar"), file);

    ASSERT.that(table.getName(file)).is(Name.simple("bar"));
  }

  @Test
  public void testGetName_failsWithNoLinksToFile() {
    File file = new File(2L, new ArrayByteStore());

    try {
      table.getName(file);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testGetName_failsWithMultipleLinksToSameFile() {
    File file = new File(2L, new ArrayByteStore());
    table.link(Name.simple("bar"), file);
    table.link(Name.simple("bar2"), file); // 2nd hard link to file in table

    try {
      table.getName(file);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testName() {
    ASSERT.that(table.name()).is(Name.simple("foo"));
  }

  @Test
  public void testSnapshot() {
    root.link(Name.simple("bar"), new File(2L, new ArrayByteStore()));
    root.link(Name.simple("abc"), new File(3L, new ArrayByteStore()));

    // does not include . or .. and is sorted by the name
    ASSERT.that(root.snapshot())
        .has().exactly(Name.simple("abc"), Name.simple("bar"), Name.simple("foo"));
  }

  @Test
  public void testSnapshot_sortsUsingStringAndNotCanonicalValueOfNames() {
    table.link(caseInsensitive("FOO"), new File(2L, new ArrayByteStore()));
    table.link(caseInsensitive("bar"), new File(3L, new ArrayByteStore()));

    ImmutableSortedSet<Name> snapshot = table.snapshot();
    Iterable<String> strings = Iterables.transform(snapshot, Functions.toStringFunction());

    // "FOO" comes before "bar"
    // if the order were based on the normalized, canonical form of the names ("foo" and "bar"),
    // "bar" would come first
    ASSERT.that(strings).iteratesAs("FOO", "bar");
  }

  @SuppressWarnings("ConstantConditions")
  private static void assertParentAndSelf(DirectoryTable table, File parent, File self) {
    ASSERT.that(table.get(PARENT)).is(parent);
    ASSERT.that(table.parent()).is(parent);

    ASSERT.that(table.get(SELF)).is(self);
    ASSERT.that(table.self()).is(self);
  }

  private static Name caseInsensitive(String name) {
    return Name.normalizing(name, Normalizer2.getNFKCCasefoldInstance());
  }
}
