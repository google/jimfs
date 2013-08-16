package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.jimfs.Name.PARENT;
import static com.google.common.io.jimfs.Name.SELF;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A table of directory entries which link names to {@linkplain File files}.
 *
 * @author Colin Decker
 */
final class DirectoryTable implements FileContent {

  /**
   * Ordering for ordering {@code Name} objects by their actual string values rather than their
   * collation keys (if applicable). Even if names are case insensitive, we want to order them in
   * a case sensitive way.
   */
  private static final Ordering<Object> STRING_ORDERING = Ordering.natural()
      .onResultOf(Functions.toStringFunction());

  private static final ImmutableSet<Name> RESERVED_NAMES =
      ImmutableSet.of(SELF, PARENT);

  private final Map<Name, File> entries = new HashMap<>();

  /**
   * Creates a copy of this table. The copy does <i>not</i> contain a copy of the entries in this
   * table.
   */
  @Override
  public DirectoryTable copy() {
    return new DirectoryTable();
  }

  @Override
  public int size() {
    return 0;
  }

  /**
   * Returns the parent directory.
   */
  public File parent() {
    return get(PARENT);
  }

  /**
   * Returns the directory table for the parent directory.
   */
  public DirectoryTable parentTable() {
    return entries.get(PARENT).content();
  }

  /**
   * Links this directory to its own file.
   */
  public void linkSelf(File self) {
    linkInternal(SELF, checkNotNull(self));
  }

  /**
   * Links this directory to the given parent file.
   */
  public void linkParent(File parent) {
    linkInternal(PARENT, checkNotNull(parent));
  }

  /**
   * Unlinks this directory from its own file.
   */
  public void unlinkSelf() {
    unlinkInternal(SELF);
  }

  /**
   * Unlinks this directory from its parent file.
   */
  public void unlinkParent() {
    unlinkInternal(PARENT);
  }

  /**
   * Returns true if this directory has no entries other than those to itself and its parent.
   */
  public boolean isEmpty() {
    return entries.size() == 2;
  }

  /**
   * Links the given name to the given file in this table.
   *
   * @throws IllegalArgumentException if {@code name} is a reserved name such as "." or if an
   *     entry already exists for the it
   */
  public void link(Name name, File file) {
    linkInternal(checkValidName(name, "link"), file);
  }

  private void linkInternal(Name name, File file) {
    checkArgument(!entries.containsKey(name), "entry '%s' already exists", name);
    entries.put(name, file);
    file.linked();
  }

  /**
   * Unlinks the given name from any key it is linked to in this table. Returns the file key that
   * was linked to the name, or {@code null} if no such mapping was present.
   *
   * @throws IllegalArgumentException if {@code name} is a reserved name such as "."
   */
  public void unlink(Name name) {
    unlinkInternal(checkValidName(name, "unlink"));
  }

  private void unlinkInternal(Name name) {
    entries.remove(name).unlinked();
  }

  /**
   * Returns the file linked by the given name in this directory or {@code null} if no such file
   * exists.
   */
  @Nullable
  public File get(Name name) {
    return entries.get(name);
  }

  /**
   * Returns the canonical form of the given name in this directory.
   *
   * @throws IllegalArgumentException if the table does not contain an entry with the given name
   */
  public Name canonicalize(Name name) {
    for (Map.Entry<Name, File> entry : entries.entrySet()) {
      if (entry.getKey().equals(name)) {
        return entry.getKey();
      }
    }
    throw new IllegalArgumentException("no entry matching '" + name + "' in this directory");
  }

  /**
   * Returns the name that links to the given file key in this directory, throwing an exception if
   * zero names or more than one name links to the key. Should only be used for getting the name of
   * a directory, as directories cannot have more than one link.
   */
  public Name getName(File file) {
    Name result = null;
    for (Map.Entry<Name, File> entry : entries.entrySet()) {
      Name name = entry.getKey();
      File i = entry.getValue();
      if (i.equals(file)) {
        if (result == null) {
          result = name;
        } else {
          throw new IllegalArgumentException("more than one name links to the given file");
        }
      }
    }

    if (result == null) {
      throw new IllegalArgumentException("directory contains no links to the given file");
    }

    return result;
  }

  /**
   * Creates an immutable sorted snapshot of the names this directory contains, excluding
   * "." and "..".
   */
  public ImmutableSortedSet<Name> snapshot() {
    ImmutableSortedSet.Builder<Name> builder = new ImmutableSortedSet.Builder<>(STRING_ORDERING);

    for (Name name : entries.keySet()) {
      if (!RESERVED_NAMES.contains(name)) {
        builder.add(name);
      }
    }
    return builder.build();
  }

  private static Name checkValidName(Name name, String action) {
    checkArgument(!RESERVED_NAMES.contains(name), "cannot %s: %s", action, name);
    return name;
  }
}
