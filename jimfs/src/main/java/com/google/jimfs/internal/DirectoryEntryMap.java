package com.google.jimfs.internal;

import static com.google.jimfs.internal.Util.smearHash;

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
    return smearHash(name.hashCode()) & (tableLength - 1);
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
      put(table, entry);
    } else {
      // otherwise, we just can use the index/entry we found
      if (prev != null) {
        prev.next = entry;
      } else {
        table[index] = entry;
      }
    }

    return entry;
  }

  private boolean expandIfNeeded() {
    if (size > resizeThreshold) {
      DirectoryEntry[] newTable = new DirectoryEntry[table.length << 1];

      // redistribute all current entries in the new table
      for (DirectoryEntry entry : table) {
        while (entry != null) {
          put(newTable, entry);
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

    return false;
  }

  private static void put(DirectoryEntry[] table, DirectoryEntry entry) {
    int index = bucketIndex(entry.name(), table.length);
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
   * Removes and returns the entry for the given name from the map.
   */
  public DirectoryEntry remove(Name name) {
    int index = bucketIndex(name, table.length);

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

    throw new IllegalArgumentException("no entry matching '" + name + "' in this directory");
  }

  /**
   * Returns an iterator over the entries in this map.
   */
  @Override
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
}
