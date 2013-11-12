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

import static com.google.jimfs.internal.Name.PARENT;
import static com.google.jimfs.internal.Name.SELF;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;

import javax.annotation.Nullable;

/**
 * A table of {@linkplain DirectoryEntry directory entries}.
 *
 * @author Colin Decker
 */
final class DirectoryTable implements FileContent {

  /**
   * Simple hash table implementation to avoid allocation of redundant Map.Entry objects.
   * DirectoryEntry objects are able to serve the same purpose.
   */
  private final DirectoryEntryMap map = new DirectoryEntryMap();

  /**
   * The entry linking to this directory in its parent directory. Used for accessing the parent
   * directory, the current name of this directory and this directory's corresponding file object.
   */
  private DirectoryEntry entryInParent;

  /**
   * Creates a copy of this table. The copy does <i>not</i> contain a copy of the entries in this
   * table.
   */
  @Override
  public DirectoryTable copy() {
    return new DirectoryTable();
  }

  @Override
  public long size() {
    return 0;
  }

  @Override
  public void delete() {
  }

  /**
   * Sets this directory as a root directory, linking ".." to itself.
   */
  public void setRoot(File self, Name name) {
    linked(new DirectoryEntry(self, name, self));
  }

  /**
   * Returns the entry linking to this directory in its parent.
   */
  public DirectoryEntry entry() {
    return entryInParent;
  }

  /**
   * Returns the file for this directory.
   */
  public File self() {
    return entryInParent.file();
  }

  /**
   * Returns the name of this directory.
   */
  public Name name() {
    return entryInParent.name();
  }

  /**
   * Returns the parent of this directory.
   */
  public File parent() {
    return entryInParent.directory();
  }

  /**
   * Called when this directory is linked in a parent directory. The given entry is the new entry
   * linking to this directory.
   */
  public void linked(DirectoryEntry entry) {
    this.entryInParent = entry;
    map.put(new DirectoryEntry(entry.file(), SELF, entry.file()));
    map.put(new DirectoryEntry(entry.file(), PARENT, entry.directory()));
    entry.file().incrementLinkCount();
    entry.directory().incrementLinkCount();
  }

  /**
   * Called when this directory is unlinked from its parent directory.
   */
  public void unlinked() {
    map.remove(SELF).file().decrementLinkCount();
    map.remove(PARENT).file().decrementLinkCount();
    entryInParent = null;
  }

  /**
   * Returns the number of entries in this directory.
   */
  @VisibleForTesting
  int entryCount() {
    return map.size();
  }

  /**
   * Returns true if this directory has no entries other than those to itself and its parent.
   */
  public boolean isEmpty() {
    return entryCount() <= 2;
  }

  /**
   * Returns the entry for the given name in this table or null if no such entry exists.
   */
  @Nullable
  public DirectoryEntry get(Name name) {
    return map.get(name);
  }

  /**
   * Links the given name to the given file in this table.
   *
   * @throws IllegalArgumentException if {@code name} is a reserved name such as "." or if an
   *     entry already exists for the it
   */
  public void link(Name name, File file) {
    DirectoryEntry entry = map.put(
        new DirectoryEntry(self(), checkNotReserved(name, "link"), file));
    file.incrementLinkCount();
    if (file.isDirectory()) {
      file.directory().linked(entry);
    }
  }

  /**
   * Unlinks the given name from the file it is linked to.
   *
   * @throws IllegalArgumentException if {@code name} is a reserved name such as "."
   */
  public void unlink(Name name) {
    DirectoryEntry entry = map.remove(checkNotReserved(name, "unlink"));
    entry.file().decrementLinkCount();
    File file = entry.file();
    if (file.isDirectory()) {
      file.directory().unlinked();
    }
  }

  /**
   * Creates an immutable sorted snapshot of the names this directory contains, excluding "." and
   * "..".
   */
  public ImmutableSortedSet<Name> snapshot() {
    ImmutableSortedSet.Builder<Name> builder =
        new ImmutableSortedSet.Builder<>(Ordering.usingToString());

    for (DirectoryEntry entry : entries()) {
      if (!isReserved(entry.name())) {
        builder.add(entry.name());
      }
    }

    return builder.build();
  }

  /**
   * Returns the entries in this table.
   */
  public Iterable<DirectoryEntry> entries() {
    return map;
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
}
