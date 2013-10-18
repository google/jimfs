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

import java.util.HashMap;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Simple hash table mapping names to directory entries. Mostly equivalent to {@link HashMap}, but
 * avoids allocating separate {@code Map.Entry} objects since {@code DirectoryEntry} objects can
 * fill its role.
 *
 * @author Colin Decker
 * @author Austin Appleby
 */
final class DirectoryTable implements Iterable<DirectoryEntry> {

  private DirectoryEntry[] table = new DirectoryEntry[16];
  private int size;

  private static int indexOf(Name name, int tableLength) {
    return smear(name.hashCode()) & (tableLength - 1);
  }

  private float loadFactor() {
    return ((float) size) / table.length;
  }

  private boolean expandIfNeeded() {
    if (loadFactor() > 0.8) {
      DirectoryEntry[] newTable = new DirectoryEntry[table.length * 2];

      for (DirectoryEntry entry : table) {
        for (; entry != null; entry = entry.next) {
          put(newTable, entry);
        }
      }

      this.table = newTable;
      return true;
    }

    return false;
  }

  /**
   * Returns the number of entries in this table.
   */
  public int size() {
    return size;
  }

  /**
   * Return whether or not this table contains an entry for the given name.
   */
  public boolean containsEntry(Name name) {
    return get(name) != null;
  }

  /**
   * Returns the entry for the given name in this table or {@code null} if no such entry exists.
   */
  @Nullable
  public DirectoryEntry get(Name name) {
    int index = indexOf(name, table.length);
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
   * Adds the given entry to this table.
   */
  public void put(DirectoryEntry entry) {
    size++;
    expandIfNeeded();

    put(table, entry);
  }

  private static void put(DirectoryEntry[] table, DirectoryEntry entry) {
    int index = indexOf(entry.name(), table.length);
    DirectoryEntry prev = null;
    DirectoryEntry existing = table[index];
    while (existing != null) {
      prev = existing;
      existing = existing.next;
    }

    if (prev != null) {
      prev.next = entry;
    } else {
      table[index] = entry;
    }
  }

  /**
   * Removes the entry for the given name from this table, returning that entry or {@code null} if
   * no such entry exists.
   */
  @Nullable
  public DirectoryEntry remove(Name name) {
    int index = indexOf(name, table.length);

    DirectoryEntry prev = null;
    DirectoryEntry entry = table[index];
    while (entry != null) {
      if (name.equals(entry.name())) {
        if (prev == null) {
          table[index] = entry.next;
        } else {
          prev.next = entry.next;
        }

        entry.next = null;
        size--;
        return entry;
      }

      prev = entry;
      entry = entry.next;
    }

    return null;
  }

  /**
   * Returns an iterator over the entries in this table.
   */
  public Iterator<DirectoryEntry> iterator() {
    return new AbstractIterator<DirectoryEntry>() {
      private int index;
      @Nullable
      private DirectoryEntry entry;

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

  private static final int C1 = 0xcc9e2d51;
  private static final int C2 = 0x1b873593;

  /*
   * This method was rewritten in Java from an intermediate step of the Murmur hash function in
   * http://code.google.com/p/smhasher/source/browse/trunk/MurmurHash3.cpp, which contained the
   * following header:
   *
   * MurmurHash3 was written by Austin Appleby, and is placed in the public domain. The author
   * hereby disclaims copyright to this source code.
   */
  private static int smear(int hashCode) {
    return C2 * Integer.rotateLeft(hashCode * C1, 15);
  }
}
