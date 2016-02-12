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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * A table of {@linkplain DirectoryEntry directory entries}.
 *
 * @author Colin Decker
 */
final class Directory extends File implements Iterable<DirectoryEntry> {

  /** The entry linking to this directory in its parent directory. */
  private DirectoryEntry entryInParent;

  /**
   * Creates a new normal directory with the given ID.
   */
  public static Directory create(int id) {
    return new Directory(id);
  }

  /**
   * Creates a new root directory with the given ID and name.
   */
  public static Directory createRoot(int id, Name name) {
    return new Directory(id, name);
  }

  private Directory(int id) {
    super(id);
    put(new DirectoryEntry(this, Name.SELF, this));
  }

  private Directory(int id, Name rootName) {
    this(id);
    linked(new DirectoryEntry(this, rootName, this));
  }

  /**
   * Creates a copy of this directory. The copy does <i>not</i> contain a copy of the entries in
   * this directory.
   */
  @Override
  Directory copyWithoutContent(int id) {
    return Directory.create(id);
  }

  /**
   * Returns the entry linking to this directory in its parent. If this directory has been deleted,
   * this returns the entry for it in the directory it was in when it was deleted.
   */
  public DirectoryEntry entryInParent() {
    return entryInParent;
  }

  /**
   * Returns the parent of this directory. If this directory has been deleted, this returns the
   * directory it was in when it was deleted.
   */
  public Directory parent() {
    return entryInParent.directory();
  }

  @Override
  void linked(DirectoryEntry entry) {
    File parent = entry.directory(); // handles null check
    this.entryInParent = entry;
    forcePut(new DirectoryEntry(this, Name.PARENT, parent));
  }

  @Override
  void unlinked() {
    // we don't actually remove the parent link when this directory is unlinked, but the parent's
    // link count should go down all the same
    parent().decrementLinkCount();
  }

  /**
   * Returns the number of entries in this directory.
   */
  @VisibleForTesting
  int entryCount() {
    return entryCount;
  }

  /**
   * Returns true if this directory has no entries other than those to itself and its parent.
   */
  public boolean isEmpty() {
    return entryCount() == 2;
  }

  /**
   * Returns the entry for the given name in this table or null if no such entry exists.
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
   * Links the given name to the given file in this directory.
   *
   * @throws IllegalArgumentException if {@code name} is a reserved name such as "." or if an
   *     entry already exists for the name
   */
  public void link(Name name, File file) {
    DirectoryEntry entry = new DirectoryEntry(this, checkNotReserved(name, "link"), file);
    put(entry);
    file.linked(entry);
  }

  /**
   * Unlinks the given name from the file it is linked to.
   *
   * @throws IllegalArgumentException if {@code name} is a reserved name such as "." or no entry
   *     exists for the name
   */
  public void unlink(Name name) {
    DirectoryEntry entry = remove(checkNotReserved(name, "unlink"));
    entry.file().unlinked();
  }

  /**
   * Creates an immutable sorted snapshot of the names this directory contains, excluding "." and
   * "..".
   */
  public ImmutableSortedSet<Name> snapshot() {
    ImmutableSortedSet.Builder<Name> builder =
        new ImmutableSortedSet.Builder<>(Name.displayOrdering());

    for (DirectoryEntry entry : this) {
      if (!isReserved(entry.name())) {
        builder.add(entry.name());
      }
    }

    return builder.build();
  }

  /**
   * Checks that the given name is not "." or "..". Those names cannot be set/removed by users.
   */
  private static Name checkNotReserved(Name name, String action) {
    if (isReserved(name)) {
      throw new IllegalArgumentException("cannot " + action + ": " + name);
    }
    return name;
  }

  /**
   * Returns true if the given name is "." or "..".
   */
  private static boolean isReserved(Name name) {
    // all "." and ".." names are canonicalized to the same objects, so we can use identity
    return name == Name.SELF || name == Name.PARENT;
  }

  // Simple hash table code to avoid allocation of Map.Entry objects when DirectoryEntry can
  // serve the same purpose.

  private static final int INITIAL_CAPACITY = 16;
  private static final int INITIAL_RESIZE_THRESHOLD = (int) (INITIAL_CAPACITY * 0.75);

  private DirectoryEntry[] table = new DirectoryEntry[INITIAL_CAPACITY];
  private int resizeThreshold = INITIAL_RESIZE_THRESHOLD;

  private int entryCount;

  /**
   * Returns the index of the bucket in the array where an entry for the given name should go.
   */
  private static int bucketIndex(Name name, int tableLength) {
    return name.hashCode() & (tableLength - 1);
  }

  /**
   * Adds the given entry to the directory.
   *
   * @throws IllegalArgumentException if an entry with the given entry's name already exists in the
   *     directory
   */
  @VisibleForTesting
  void put(DirectoryEntry entry) {
    put(entry, false);
  }

  /**
   * Adds the given entry to the directory, overwriting an existing entry with the same name if
   * such an entry exists.
   */
  private void forcePut(DirectoryEntry entry) {
    put(entry, true);
  }

  /**
   * Adds the given entry to the directory. {@code overwriteExisting} determines whether an existing
   * entry with the same name should be overwritten or an exception should be thrown.
   */
  private void put(DirectoryEntry entry, boolean overwriteExisting) {
    int index = bucketIndex(entry.name(), table.length);

    // find the place the new entry should go, ensuring an entry with the same name doesn't already
    // exist along the way
    DirectoryEntry prev = null;
    DirectoryEntry curr = table[index];
    while (curr != null) {
      if (curr.name().equals(entry.name())) {
        if (overwriteExisting) {
          // just replace the existing entry; no need to expand, and entryCount doesn't change
          if (prev != null) {
            prev.next = entry;
          } else {
            table[index] = entry;
          }
          entry.next = curr.next;
          curr.next = null;
          entry.file().incrementLinkCount();
          return;
        } else {
          throw new IllegalArgumentException("entry '" + entry.name() + "' already exists");
        }
      }

      prev = curr;
      curr = curr.next;
    }

    entryCount++;
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
  }

  private boolean expandIfNeeded() {
    if (entryCount <= resizeThreshold) {
      return false;
    }

    DirectoryEntry[] newTable = new DirectoryEntry[table.length << 1];

    // redistribute all current entries in the new table
    for (DirectoryEntry entry : table) {
      while (entry != null) {
        int index = bucketIndex(entry.name(), newTable.length);
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
   * Removes and returns the entry for the given name from the directory.
   *
   * @throws IllegalArgumentException if there is no entry with the given name in the directory
   */
  @VisibleForTesting
  DirectoryEntry remove(Name name) {
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
        entryCount--;
        entry.file().decrementLinkCount();
        return entry;
      }

      prev = entry;
      entry = entry.next;
    }

    throw new IllegalArgumentException("no entry matching '" + name + "' in this directory");
  }

  @Override
  public Iterator<DirectoryEntry> iterator() {
    return new AbstractIterator<DirectoryEntry>() {
      int index;
      @Nullable DirectoryEntry entry;

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
