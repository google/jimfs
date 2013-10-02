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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.jimfs.internal.Name.PARENT;
import static com.google.jimfs.internal.Name.SELF;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A table of {@linkplain DirectoryEntry directory entries}.
 *
 * @author Colin Decker
 */
final class DirectoryTable implements FileContent {

  private static final ImmutableSet<Name> RESERVED_NAMES = ImmutableSet.of(SELF, PARENT);

  /**
   * Map for looking up an entry by name.
   */
  private final Map<Name, DirectoryEntry> entries = new HashMap<>();

  /**
   * The entry linking to this directory in its parent directory.
   */
  private DirectoryEntry entry;

  /**
   * Creates a copy of this table. The copy does <i>not</i> contain a copy of the entries in this
   * table.
   */
  @Override
  public DirectoryTable copy() {
    return new DirectoryTable();
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
    this.entry = new DirectoryEntry(file, Name.simple(""), file);
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
  public int size() {
    return entries.size();
  }

  /**
   * Returns true if this directory has no entries other than those to itself and its parent.
   */
  public boolean isEmpty() {
    return entries.size() <= 2;
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
      DirectoryTable table = file.content();
      table.linked(entry);
    }
  }

  private DirectoryEntry linkInternal(Name name, File file) {
    checkArgument(!entries.containsKey(name), "entry '%s' already exists", name);
    DirectoryEntry entry = new DirectoryEntry(self(), name, file);
    entries.put(name, entry);
    file.linked();
    return entry;
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
      DirectoryTable table = file.content();
      table.unlinked();
    }
  }

  private DirectoryEntry unlinkInternal(Name name) {
    DirectoryEntry entry = entries.remove(name);
    if (entry == null) {
      throw new IllegalArgumentException("no entry matching '" + name + "' in this directory");
    }
    entry.file().unlinked();
    return entry;
  }

  /**
   * Returns the entry for the file linked by the given name in this directory or {@code null} if
   * no such file exists.
   */
  @Nullable
  public DirectoryEntry getEntry(Name name) {
    return entries.get(name);
  }

  /**
   * Creates an immutable sorted snapshot of the names this directory contains, excluding
   * "." and "..".
   */
  public ImmutableSortedSet<Name> snapshot() {
    return ImmutableSortedSet.copyOf(Ordering.usingToString(), asMap().keySet());
  }

  private Map<Name, DirectoryEntry> asMap() {
    return Maps.filterKeys(entries, Predicates.not(Predicates.in(RESERVED_NAMES)));
  }

  /**
   * Returns a view of the entries in this table, excluding entries for "." and "..".
   */
  public Collection<DirectoryEntry> entries() {
    return asMap().values();
  }

  private static Name checkValidName(Name name, String action) {
    checkArgument(!RESERVED_NAMES.contains(name), "cannot %s: %s", action, name);
    return name;
  }
}
