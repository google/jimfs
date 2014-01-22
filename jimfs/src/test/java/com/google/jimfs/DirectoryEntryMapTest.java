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

package com.google.jimfs;

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link DirectoryEntryMap}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class DirectoryEntryMapTest {

  private static final File A = new File(0, new FakeFileContent());

  private DirectoryEntryMap map;

  @Before
  public void setUp() {
    map = new DirectoryEntryMap();
  }

  @Test
  public void testInitialState() {
    ASSERT.that(map.size()).is(0);
    ASSERT.that(map).isEmpty();
    ASSERT.that(map.get(Name.simple("foo"))).isNull();
  }

  @Test
  public void testPutAndGet() {
    map.put(entry("foo"));

    ASSERT.that(map.size()).is(1);
    ASSERT.that(map).iteratesAs(entry("foo"));
    ASSERT.that(map.get(Name.simple("foo"))).is(entry("foo"));

    map.put(entry("bar"));

    ASSERT.that(map.size()).is(2);
    ASSERT.that(ImmutableSet.copyOf(map))
        .has().allOf(entry("foo"), entry("bar"));
    ASSERT.that(map.get(Name.simple("foo"))).is(entry("foo"));
    ASSERT.that(map.get(Name.simple("bar"))).is(entry("bar"));
  }

  @Test
  public void testPutEntryForExistingNameIsIllegal() {
    map.put(entry("foo"));

    try {
      map.put(entry("foo"));
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void testRemove() {
    map.put(entry("foo"));
    map.put(entry("bar"));

    map.remove(Name.simple("foo"));

    ASSERT.that(map.size()).is(1);
    ASSERT.that(map).iteratesAs(entry("bar"));
    ASSERT.that(map.get(Name.simple("foo"))).isNull();
    ASSERT.that(map.get(Name.simple("bar"))).is(entry("bar"));

    map.remove(Name.simple("bar"));

    ASSERT.that(map.size()).is(0);
    ASSERT.that(map).isEmpty();

    map.put(entry("bar"));
    map.put(entry("foo")); // these should just succeeded
  }

  @Test
  public void testManyPutsAndRemoves() {
    // test resizing/rehashing

    Set<DirectoryEntry> entriesInMap = new HashSet<>();

    // add 1000 entries
    for (int i = 0; i < 1000; i++) {
      DirectoryEntry entry = entry(String.valueOf(i));
      map.put(entry);
      entriesInMap.add(entry);

      ASSERT.that(ImmutableSet.copyOf(map)).isEqualTo(entriesInMap);

      for (DirectoryEntry expected : entriesInMap) {
        ASSERT.that(map.get(expected.name())).is(expected);
      }
    }

    // remove 1000 entries
    for (int i = 0; i < 1000; i++) {
      map.remove(Name.simple(String.valueOf(i)));
      entriesInMap.remove(entry(String.valueOf(i)));

      ASSERT.that(ImmutableSet.copyOf(map)).isEqualTo(entriesInMap);

      for (DirectoryEntry expected : entriesInMap) {
        ASSERT.that(map.get(expected.name())).is(expected);
      }
    }

    // mixed adds and removes
    for (int i = 0; i < 10000; i++) {
      DirectoryEntry entry = entry(String.valueOf(i));
      map.put(entry);
      entriesInMap.add(entry);

      if (i > 0 && i % 20 == 0) {
        String nameToRemove = String.valueOf(i / 2);
        map.remove(Name.simple(nameToRemove));
        entriesInMap.remove(entry(nameToRemove));
      }
    }

    // for this one, only test that the end result is correct
    // takes too long to test at each iteration
    ASSERT.that(ImmutableSet.copyOf(map)).isEqualTo(entriesInMap);

    for (DirectoryEntry expected : entriesInMap) {
      ASSERT.that(map.get(expected.name())).is(expected);
    }
  }

  private static DirectoryEntry entry(String name) {
    return new DirectoryEntry(A, Name.simple(name), A);
  }
}
