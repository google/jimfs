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
 * File content containing a table of {@linkplain DirectoryEntry directory entries}.
 *
 * @author Colin Decker
 */
final class Directory implements FileContent {

  private final DirectoryTable table = new DirectoryTable();

  /**
   * The entry linking to this directory in its parent directory.
   */
  private DirectoryEntry entry;

  /**
   * Creates a copy of this table. The copy does <i>not</i> contain a copy of the entries in this
   * table.
   */
  @Override
  public Directory copy() {
    return new Directory();
  }

  @Override
  public long sizeInBytes() {
    return 0;
  }

  @Override
  public void delete() {
  }

  /**
   * Sets this directory as the super root.
   */
  public void setSuperRoot(File file) {
    // just set this table's entry to include the file
    this.entry = new DirectoryEntry(file, Name.EMPTY, file);
  }

  /**
   * Sets this directory as a root directory, linking ".." to itself.
   */
  public void setRoot() {
    this.entry = new DirectoryEntry(self(), name(), self());
    unlinkInternal(PARENT);
    linkInternal(PARENT, self());
  }

  /**
   * Returns the entry linking to this directory in its parent.
   */
  public DirectoryEntry entry() {
    return entry;
  }

  /**
   * Returns the file for this directory.
   */
  public File self() {
    return entry.file();
  }

  /**
   * Returns the name of this directory.
   */
  public Name name() {
    return entry.name();
  }

  /**
   * Returns the parent of this directory.
   */
  public File parent() {
    return entry.directory();
  }

  /**
   * Called when this directory is linked in a parent directory. The given entry is the new entry
   * linking to this directory.
   */
  public void linked(DirectoryEntry entry) {
    this.entry = entry;
    linkInternal(SELF, entry.file());
    linkInternal(PARENT, entry.directory());
  }

  /**
   * Called when this directory is unlinked from its parent directory.
   */
  public void unlinked() {
    unlinkInternal(SELF);
    unlinkInternal(PARENT);
    entry = null;
  }

  /**
   * Returns the number of entries in this directory.
   */
  @VisibleForTesting
  int entryCount() {
    return table.size();
  }

  /**
   * Returns true if this directory has no entries other than those to itself and its parent.
   */
  public boolean isEmpty() {
    return table.size() <= 2;
  }

  /**
   * Returns the directory entry for the given name or {@code null} if no such entry exists.
   */
  @Nullable
  public DirectoryEntry get(Name name) {
    return table.get(name);
  }

  /**
   * Links the given name to the given file in this table.
   *
   * @throws IllegalArgumentException if {@code name} is a reserved name such as "." or if an
   *     entry already exists for the it
   */
  public void link(Name name, File file) {
    DirectoryEntry entry = linkInternal(checkValidName(name, "link"), file);
    if (file.isDirectory()) {
      file.asDirectory().linked(entry);
    }
  }

  private DirectoryEntry linkInternal(Name name, File file) {
    if (table.containsEntry(name)) {
      throw new IllegalArgumentException("entry '" + name + "' already exists");
    }

    DirectoryEntry newEntry = new DirectoryEntry(self(), name, file);
    table.put(newEntry);
    file.incrementLinkCount();
    return newEntry;
  }

  /**
   * Unlinks the given name from any key it is linked to in this table. Returns the file key that
   * was linked to the name, or {@code null} if no such mapping was present.
   *
   * @throws IllegalArgumentException if {@code name} is a reserved name such as "."
   */
  public void unlink(Name name) {
    DirectoryEntry entry = unlinkInternal(checkValidName(name, "unlink"));
    File file = entry.file();
    if (file.isDirectory()) {
      file.asDirectory().unlinked();
    }
  }

  private DirectoryEntry unlinkInternal(Name name) {
    DirectoryEntry entry = table.remove(name);

    if (entry == null) {
      throw new IllegalArgumentException("no entry matching '" + name + "' in this directory");
    }

    entry.file().decrementLinkCount();
    return entry;
  }

  @SuppressWarnings("unchecked") // safe cast
  private static final Ordering<Name> ORDERING_BY_STRING =
      (Ordering<Name>) (Ordering) Ordering.usingToString();

  /**
   * Creates an immutable sorted snapshot of the names this directory contains, excluding "." and
   * "..".
   */
  @SuppressWarnings("unchecked") // safe cast
  public ImmutableSortedSet<Name> snapshot() {
    ImmutableSortedSet.Builder<Name> builder = ImmutableSortedSet.orderedBy(ORDERING_BY_STRING);

    for (DirectoryEntry entry : entries()) {
      if (!isReserved(entry.name())) {
        builder.add(entry.name());
      }
    }

    return builder.build();
  }

  /**
   * Returns an iterable over the entries in this directory.
   */
  public Iterable<DirectoryEntry> entries() {
    return table;
  }

  private static Name checkValidName(Name name, String action) {
    if (isReserved(name)) {
      throw new IllegalArgumentException("cannot " + action + ": " + name);
    }
    return name;
  }

  private static boolean isReserved(Name name) {
    String string = name.toString();
    int length = string.length();
    if (length == 0 || length > 2) {
      return false;
    }

    for (int i = 0; i < string.length(); i++) {
      if (string.charAt(i) != '.') {
        return false;
      }
    }

    return true;
  }
}
