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

import com.google.common.collect.AbstractIterator;

import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Simple hash map of names to directory entries.
 *
 * @author Colin Decker
 */
final class DirectoryEntryMap implements Iterable<DirectoryEntry> {

  private static final int INITIAL_CAPACITY = 16;
  private static final int INITIAL_RESIZE_THRESHOLD = (int) (INITIAL_CAPACITY * 0.75);

  private DirectoryEntry[] table = new DirectoryEntry[INITIAL_CAPACITY];
  private int resizeThreshold = INITIAL_RESIZE_THRESHOLD;

  private int size;

  /**
   * Returns the current size of the map.
   */
  public int size() {
    return size;
  }

  /**
   * Returns the index of the bucket in the array where an entry for the given name should go.
   */
  private static int bucketIndex(Name name, int tableLength) {
    return name.hashCode() & (tableLength - 1);
  }

  /**
   * Returns the entry for the given name in the map.
   */
  @Nullable
  public DirectoryEntry get(Name name) {
    int index = bucketIndex(name, table.length);

    DirectoryEntry entry = table[index];
    while (entry != null) {
      if (name.equals(entry.name())) {
        return entry;
      }

      entry = entry.next;
    }
    return null;
  }

  /**
   * Adds the given entry to the map.
   *
   * @throws IllegalArgumentException if an entry with the given entry's name already exists in the
   *     map
   */
  public DirectoryEntry put(DirectoryEntry entry) {
    int index = bucketIndex(entry.name(), table.length);

    // find the place the new entry should go, ensuring an entry with the same name doesn't already
    // exist along the way
    DirectoryEntry prev = null;
    DirectoryEntry curr = table[index];
    while (curr != null) {
      if (curr.name().equals(entry.name())) {
        throw new IllegalArgumentException("entry '" + entry.name() + "' already exists");
      }

      prev = curr;
      curr = curr.next;
    }

    size++;
    if (expandIfNeeded()) {
      // if the table was expanded, the index/entry we found is no longer applicable, so just add
      // the entry normally
      index = bucketIndex(entry.name(), table.length);
      addToBucket(index, table, entry);
    } else {
      // otherwise, we just can use the index/entry we found
      if (prev != null) {
        prev.next = entry;
      } else {
        table[index] = entry;
      }
    }

    entry.file().incrementLinkCount();
    return entry;
  }

  private boolean expandIfNeeded() {
    if (size <= resizeThreshold) {
      return false;
    }

    DirectoryEntry[] newTable = new DirectoryEntry[table.length << 1];

    // redistribute all current entries in the new table
    for (DirectoryEntry entry : table) {
      while (entry != null) {
        int index = bucketIndex(entry.name(), table.length);
        addToBucket(index, newTable, entry);
        DirectoryEntry next = entry.next;
        // set entry.next to null; it's always the last entry in its bucket after being added
        entry.next = null;
        entry = next;
      }
    }

    this.table = newTable;
    resizeThreshold <<= 1;
    return true;
  }

  private static void addToBucket(
      int bucketIndex, DirectoryEntry[] table, DirectoryEntry entryToAdd) {
    DirectoryEntry prev = null;
    DirectoryEntry existing = table[bucketIndex];
    while (existing != null) {
      prev = existing;
      existing = existing.next;
    }

    if (prev != null) {
      prev.next = entryToAdd;
    } else {
      table[bucketIndex] = entryToAdd;
    }
  }

  /**
   * Removes and returns the entry for the given name from the map.
   *
   * @throws IllegalArgumentException if there is no entry with the given name in the map
   */
  public DirectoryEntry remove(Name name) {
    int index = bucketIndex(name, table.length);

    DirectoryEntry prev = null;
    DirectoryEntry entry = table[index];
    while (entry != null) {
      if (name.equals(entry.name())) {
        if (prev != null) {
          prev.next = entry.next;
        } else {
          table[index] = entry.next;
        }

        entry.next = null;
        size--;
        entry.file().decrementLinkCount();
        return entry;
      }

      prev = entry;
      entry = entry.next;
    }

    throw new IllegalArgumentException("no entry matching '" + name + "' in this directory");
  }

  /**
   * Returns an iterator over the entries in this map.
   */
  @Override
  public Iterator<DirectoryEntry> iterator() {
    return new AbstractIterator<DirectoryEntry>() {
      int index;
      @Nullable
      DirectoryEntry entry;

      @Override
      protected DirectoryEntry computeNext() {
        if (entry != null) {
          entry = entry.next;
        }

        while (entry == null && index < table.length) {
          entry = table[index++];
        }

        return entry != null ? entry : endOfData();
      }
    };
  }
}
