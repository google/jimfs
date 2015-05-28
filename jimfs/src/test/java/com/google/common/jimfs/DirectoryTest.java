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
import static com.google.common.truth.Truth.assertThat;
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
    assertThat(root.entryCount()).isEqualTo(3); // two for parent/self, one for dir
    assertThat(root.isEmpty()).isFalse();
    assertThat(root.entryInParent()).isEqualTo(entry(root, "/", root));
    assertThat(root.entryInParent().name()).isEqualTo(Name.simple("/"));

    assertParentAndSelf(root, root, root);
  }

  @Test
  public void testEmptyDirectory() {
    assertThat(dir.entryCount()).isEqualTo(2);
    assertThat(dir.isEmpty()).isTrue();

    assertParentAndSelf(dir, root, dir);
  }

  @Test
  public void testGet() {
    assertThat(root.get(Name.simple("foo"))).isEqualTo(entry(root, "foo", dir));
    assertThat(dir.get(Name.simple("foo"))).isNull();
    assertThat(root.get(Name.simple("Foo"))).isNull();
  }

  @Test
  public void testLink() {
    assertThat(dir.get(Name.simple("bar"))).isNull();

    File bar = Directory.create(2);
    dir.link(Name.simple("bar"), bar);

    assertThat(dir.get(Name.simple("bar"))).isEqualTo(entry(dir, "bar", bar));
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
    assertThat(dir.get(caseInsensitive("bar"))).isEqualTo(expected);
    assertThat(dir.get(caseInsensitive("BAR"))).isEqualTo(expected);
    assertThat(dir.get(caseInsensitive("Bar"))).isEqualTo(expected);
    assertThat(dir.get(caseInsensitive("baR"))).isEqualTo(expected);
  }

  @Test
  public void testUnlink() {
    assertThat(root.get(Name.simple("foo"))).isNotNull();

    root.unlink(Name.simple("foo"));

    assertThat(root.get(Name.simple("foo"))).isNull();
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

    assertThat(dir.get(caseInsensitive("bar"))).isNotNull();

    dir.unlink(caseInsensitive("BAR"));

    assertThat(dir.get(caseInsensitive("bar"))).isNull();
  }

  @Test
  public void testLinkDirectory() {
    Directory newDir = Directory.create(10);

    assertThat(newDir.entryInParent()).isNull();
    assertThat(newDir.get(Name.SELF).file()).isEqualTo(newDir);
    assertThat(newDir.get(Name.PARENT)).isNull();
    assertThat(newDir.links()).isEqualTo(1);

    dir.link(Name.simple("foo"), newDir);

    assertThat(newDir.entryInParent()).isEqualTo(entry(dir, "foo", newDir));
    assertThat(newDir.parent()).isEqualTo(dir);
    assertThat(newDir.entryInParent().name()).isEqualTo(Name.simple("foo"));
    assertThat(newDir.get(Name.SELF)).isEqualTo(entry(newDir, ".", newDir));
    assertThat(newDir.get(Name.PARENT)).isEqualTo(entry(newDir, "..", dir));
    assertThat(newDir.links()).isEqualTo(2);
  }

  @Test
  public void testUnlinkDirectory() {
    Directory newDir = Directory.create(10);

    dir.link(Name.simple("foo"), newDir);

    assertThat(dir.links()).isEqualTo(3);

    assertThat(newDir.entryInParent()).isEqualTo(entry(dir, "foo", newDir));
    assertThat(newDir.links()).isEqualTo(2);

    dir.unlink(Name.simple("foo"));

    assertThat(dir.links()).isEqualTo(2);

    assertThat(newDir.entryInParent()).isEqualTo(entry(dir, "foo", newDir));
    assertThat(newDir.get(Name.SELF).file()).isEqualTo(newDir);
    assertThat(newDir.get(Name.PARENT)).isEqualTo(entry(newDir, "..", dir));
    assertThat(newDir.links()).isEqualTo(1);
  }

  @Test
  public void testSnapshot() {
    root.link(Name.simple("bar"), regularFile(10));
    root.link(Name.simple("abc"), regularFile(10));

    // does not include . or .. and is sorted by the name
    assertThat(root.snapshot())
        .containsExactly(Name.simple("abc"), Name.simple("bar"), Name.simple("foo"));
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
    assertThat(strings).containsExactly("FOO", "bar").inOrder();
  }

  // Tests for internal hash table implementation

  private static final Directory A = Directory.create(0);

  @Test
  public void testInitialState() {
    assertThat(dir.entryCount()).isEqualTo(2);
    assertThat(ImmutableSet.copyOf(dir))
        .containsExactly(
            new DirectoryEntry(dir, Name.SELF, dir), new DirectoryEntry(dir, Name.PARENT, root));
    assertThat(dir.get(Name.simple("foo"))).isNull();
  }

  @Test
  public void testPutAndGet() {
    dir.put(entry("foo"));

    assertThat(dir.entryCount()).isEqualTo(3);
    assertThat(ImmutableSet.copyOf(dir)).contains(entry("foo"));
    assertThat(dir.get(Name.simple("foo"))).isEqualTo(entry("foo"));

    dir.put(entry("bar"));

    assertThat(dir.entryCount()).isEqualTo(4);
    assertThat(ImmutableSet.copyOf(dir)).containsAllOf(entry("foo"), entry("bar"));
    assertThat(dir.get(Name.simple("foo"))).isEqualTo(entry("foo"));
    assertThat(dir.get(Name.simple("bar"))).isEqualTo(entry("bar"));
  }

  @Test
  public void testPutEntryForExistingNameIsIllegal() {
    dir.put(entry("foo"));

    try {
      dir.put(entry("foo"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testRemove() {
    dir.put(entry("foo"));
    dir.put(entry("bar"));

    dir.remove(Name.simple("foo"));

    assertThat(dir.entryCount()).isEqualTo(3);
    assertThat(ImmutableSet.copyOf(dir))
        .containsExactly(
            entry("bar"),
            new DirectoryEntry(dir, Name.SELF, dir),
            new DirectoryEntry(dir, Name.PARENT, root));
    assertThat(dir.get(Name.simple("foo"))).isNull();
    assertThat(dir.get(Name.simple("bar"))).isEqualTo(entry("bar"));

    dir.remove(Name.simple("bar"));

    assertThat(dir.entryCount()).isEqualTo(2);

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

      assertThat(ImmutableSet.copyOf(dir)).isEqualTo(entriesInDir);

      for (DirectoryEntry expected : entriesInDir) {
        assertThat(dir.get(expected.name())).isEqualTo(expected);
      }
    }

    // remove 1000 entries
    for (int i = 0; i < 1000; i++) {
      dir.remove(Name.simple(String.valueOf(i)));
      entriesInDir.remove(entry(String.valueOf(i)));

      assertThat(ImmutableSet.copyOf(dir)).isEqualTo(entriesInDir);

      for (DirectoryEntry expected : entriesInDir) {
        assertThat(dir.get(expected.name())).isEqualTo(expected);
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
    assertThat(ImmutableSet.copyOf(dir)).isEqualTo(entriesInDir);

    for (DirectoryEntry expected : entriesInDir) {
      assertThat(dir.get(expected.name())).isEqualTo(expected);
    }
  }

  private static DirectoryEntry entry(String name) {
    return new DirectoryEntry(A, Name.simple(name), A);
  }

  private static DirectoryEntry entry(Directory dir, String name, @Nullable File file) {
    return new DirectoryEntry(dir, Name.simple(name), file);
  }

  private static void assertParentAndSelf(Directory dir, File parent, File self) {
    assertThat(dir).isEqualTo(self);
    assertThat(dir.parent()).isEqualTo(parent);

    assertThat(dir.get(PARENT)).isEqualTo(entry((Directory) self, "..", parent));
    assertThat(dir.get(SELF)).isEqualTo(entry((Directory) self, ".", self));
  }

  private static Name caseInsensitive(String name) {
    return Name.create(name, PathNormalization.CASE_FOLD_UNICODE.apply(name));
  }
}
