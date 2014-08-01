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

import static com.google.common.jimfs.Name.PARENT;
import static com.google.common.jimfs.Name.SELF;
import static com.google.common.jimfs.TestUtils.regularFile;
import static com.google.common.truth.Truth.ASSERT;
import static org.junit.Assert.fail;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Tests for {@link Directory}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class DirectoryTest {

  private Directory root;
  private Directory dir;

  @Before
  public void setUp() {
    root = Directory.createRoot(0, Name.simple("/"));

    dir = Directory.create(1);
    root.link(Name.simple("foo"), dir);
  }

  @Test
  public void testRootDirectory() {
    ASSERT.that(root.entryCount()).is(3); // two for parent/self, one for dir
    ASSERT.that(root.isEmpty()).isFalse();
    ASSERT.that(root.entryInParent()).isEqualTo(entry(root, "/", root));
    ASSERT.that(root.entryInParent().name()).isEqualTo(Name.simple("/"));

    assertParentAndSelf(root, root, root);
  }

  @Test
  public void testEmptyDirectory() {
    ASSERT.that(dir.entryCount()).is(2);
    ASSERT.that(dir.isEmpty()).isTrue();

    assertParentAndSelf(dir, root, dir);
  }

  @Test
  public void testGet() {
    ASSERT.that(root.get(Name.simple("foo"))).isEqualTo(entry(root, "foo", dir));
    ASSERT.that(dir.get(Name.simple("foo"))).isNull();
    ASSERT.that(root.get(Name.simple("Foo"))).isNull();
  }

  @Test
  public void testLink() {
    ASSERT.that(dir.get(Name.simple("bar"))).isNull();

    File bar = Directory.create(2);
    dir.link(Name.simple("bar"), bar);

    ASSERT.that(dir.get(Name.simple("bar"))).isEqualTo(entry(dir, "bar", bar));
  }

  @Test
  public void testLink_existingNameFails() {
    try {
      root.link(Name.simple("foo"), Directory.create(2));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testLink_parentAndSelfNameFails() {
    try {
      dir.link(Name.simple("."), Directory.create(2));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      dir.link(Name.simple(".."), Directory.create(2));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testGet_normalizingCaseInsensitive() {
    File bar = Directory.create(2);
    Name barName = caseInsensitive("bar");

    dir.link(barName, bar);

    DirectoryEntry expected = new DirectoryEntry(dir, barName, bar);
    ASSERT.that(dir.get(caseInsensitive("bar"))).isEqualTo(expected);
    ASSERT.that(dir.get(caseInsensitive("BAR"))).isEqualTo(expected);
    ASSERT.that(dir.get(caseInsensitive("Bar"))).isEqualTo(expected);
    ASSERT.that(dir.get(caseInsensitive("baR"))).isEqualTo(expected);
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
      dir.unlink(Name.simple("bar"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testUnlink_parentAndSelfNameFails() {
    try {
      dir.unlink(Name.simple("."));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      dir.unlink(Name.simple(".."));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testUnlink_normalizingCaseInsensitive() {
    dir.link(caseInsensitive("bar"), Directory.create(2));

    ASSERT.that(dir.get(caseInsensitive("bar"))).isNotNull();

    dir.unlink(caseInsensitive("BAR"));

    ASSERT.that(dir.get(caseInsensitive("bar"))).isNull();
  }

  @Test
  public void testLinkDirectory() {
    Directory newDir = Directory.create(10);

    ASSERT.that(newDir.entryInParent()).isNull();
    ASSERT.that(newDir.get(Name.SELF).file()).isEqualTo(newDir);
    ASSERT.that(newDir.get(Name.PARENT)).isNull();
    ASSERT.that(newDir.links()).is(1);

    dir.link(Name.simple("foo"), newDir);

    ASSERT.that(newDir.entryInParent()).isEqualTo(entry(dir, "foo", newDir));
    ASSERT.that(newDir.parent()).isEqualTo(dir);
    ASSERT.that(newDir.entryInParent().name()).isEqualTo(Name.simple("foo"));
    ASSERT.that(newDir.get(Name.SELF)).isEqualTo(entry(newDir, ".", newDir));
    ASSERT.that(newDir.get(Name.PARENT)).isEqualTo(entry(newDir, "..", dir));
    ASSERT.that(newDir.links()).is(2);
  }

  @Test
  public void testUnlinkDirectory() {
    Directory newDir = Directory.create(10);

    dir.link(Name.simple("foo"), newDir);

    ASSERT.that(dir.links()).is(3);

    ASSERT.that(newDir.entryInParent()).isEqualTo(entry(dir, "foo", newDir));
    ASSERT.that(newDir.links()).is(2);

    dir.unlink(Name.simple("foo"));

    ASSERT.that(dir.links()).is(2);

    ASSERT.that(newDir.entryInParent()).isEqualTo(entry(dir, "foo", newDir));
    ASSERT.that(newDir.get(Name.SELF).file()).isEqualTo(newDir);
    ASSERT.that(newDir.get(Name.PARENT)).isEqualTo(entry(newDir, "..", dir));
    ASSERT.that(newDir.links()).is(1);
  }

  @Test
  public void testSnapshot() {
    root.link(Name.simple("bar"), regularFile(10));
    root.link(Name.simple("abc"), regularFile(10));

    // does not include . or .. and is sorted by the name
    ASSERT.that(root.snapshot())
        .has().exactly(Name.simple("abc"), Name.simple("bar"), Name.simple("foo"));
  }

  @Test
  public void testSnapshot_sortsUsingStringAndNotCanonicalValueOfNames() {
    dir.link(caseInsensitive("FOO"), regularFile(10));
    dir.link(caseInsensitive("bar"), regularFile(10));

    ImmutableSortedSet<Name> snapshot = dir.snapshot();
    Iterable<String> strings = Iterables.transform(snapshot, Functions.toStringFunction());

    // "FOO" comes before "bar"
    // if the order were based on the normalized, canonical form of the names ("foo" and "bar"),
    // "bar" would come first
    ASSERT.that(strings).iteratesAs("FOO", "bar");
  }

  // Tests for internal hash table implementation

  private static final Directory A = Directory.create(0);

  @Test
  public void testInitialState() {
    ASSERT.that(dir.entryCount()).is(2);
    ASSERT.that(ImmutableSet.copyOf(dir)).has().exactly(
        new DirectoryEntry(dir, Name.SELF, dir),
        new DirectoryEntry(dir, Name.PARENT, root));
    ASSERT.that(dir.get(Name.simple("foo"))).isNull();
  }

  @Test
  public void testPutAndGet() {
    dir.put(entry("foo"));

    ASSERT.that(dir.entryCount()).is(3);
    ASSERT.that(ImmutableSet.copyOf(dir)).has().item(entry("foo"));
    ASSERT.that(dir.get(Name.simple("foo"))).isEqualTo(entry("foo"));

    dir.put(entry("bar"));

    ASSERT.that(dir.entryCount()).is(4);
    ASSERT.that(ImmutableSet.copyOf(dir))
        .has().allOf(entry("foo"), entry("bar"));
    ASSERT.that(dir.get(Name.simple("foo"))).isEqualTo(entry("foo"));
    ASSERT.that(dir.get(Name.simple("bar"))).isEqualTo(entry("bar"));
  }

  @Test
  public void testPutEntryForExistingNameIsIllegal() {
    dir.put(entry("foo"));

    try {
      dir.put(entry("foo"));
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void testRemove() {
    dir.put(entry("foo"));
    dir.put(entry("bar"));

    dir.remove(Name.simple("foo"));

    ASSERT.that(dir.entryCount()).is(3);
    ASSERT.that(ImmutableSet.copyOf(dir)).has().exactly(
        entry("bar"),
        new DirectoryEntry(dir, Name.SELF, dir),
        new DirectoryEntry(dir, Name.PARENT, root));
    ASSERT.that(dir.get(Name.simple("foo"))).isNull();
    ASSERT.that(dir.get(Name.simple("bar"))).isEqualTo(entry("bar"));

    dir.remove(Name.simple("bar"));

    ASSERT.that(dir.entryCount()).is(2);

    dir.put(entry("bar"));
    dir.put(entry("foo")); // these should just succeeded
  }

  @Test
  public void testManyPutsAndRemoves() {
    // test resizing/rehashing

    Set<DirectoryEntry> entriesInDir = new HashSet<>();
    entriesInDir.add(new DirectoryEntry(dir, Name.SELF, dir));
    entriesInDir.add(new DirectoryEntry(dir, Name.PARENT, root));

    // add 1000 entries
    for (int i = 0; i < 1000; i++) {
      DirectoryEntry entry = entry(String.valueOf(i));
      dir.put(entry);
      entriesInDir.add(entry);

      ASSERT.that(ImmutableSet.copyOf(dir)).isEqualTo(entriesInDir);

      for (DirectoryEntry expected : entriesInDir) {
        ASSERT.that(dir.get(expected.name())).isEqualTo(expected);
      }
    }

    // remove 1000 entries
    for (int i = 0; i < 1000; i++) {
      dir.remove(Name.simple(String.valueOf(i)));
      entriesInDir.remove(entry(String.valueOf(i)));

      ASSERT.that(ImmutableSet.copyOf(dir)).isEqualTo(entriesInDir);

      for (DirectoryEntry expected : entriesInDir) {
        ASSERT.that(dir.get(expected.name())).isEqualTo(expected);
      }
    }

    // mixed adds and removes
    for (int i = 0; i < 10000; i++) {
      DirectoryEntry entry = entry(String.valueOf(i));
      dir.put(entry);
      entriesInDir.add(entry);

      if (i > 0 && i % 20 == 0) {
        String nameToRemove = String.valueOf(i / 2);
        dir.remove(Name.simple(nameToRemove));
        entriesInDir.remove(entry(nameToRemove));
      }
    }

    // for this one, only test that the end result is correct
    // takes too long to test at each iteration
    ASSERT.that(ImmutableSet.copyOf(dir)).isEqualTo(entriesInDir);

    for (DirectoryEntry expected : entriesInDir) {
      ASSERT.that(dir.get(expected.name())).isEqualTo(expected);
    }
  }

  private static DirectoryEntry entry(String name) {
    return new DirectoryEntry(A, Name.simple(name), A);
  }

  private static DirectoryEntry entry(Directory dir, String name, @Nullable File file) {
    return new DirectoryEntry(dir, Name.simple(name), file);
  }

  private static void assertParentAndSelf(Directory dir, File parent, File self) {
    ASSERT.that(dir).isEqualTo(self);
    ASSERT.that(dir.parent()).isEqualTo(parent);

    ASSERT.that(dir.get(PARENT)).isEqualTo(entry((Directory) self, "..", parent));
    ASSERT.that(dir.get(SELF)).isEqualTo(entry((Directory) self, ".", self));
  }

  private static Name caseInsensitive(String name) {
    return Name.create(name, PathNormalization.CASE_FOLD_UNICODE.apply(name));
  }
}
