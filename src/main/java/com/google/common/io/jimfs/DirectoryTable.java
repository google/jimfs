package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A table of {@linkplain DirEntry directory entries}.
 *
 * @author Colin Decker
 */
final class DirectoryTable implements FileContent {

  static final String THIS = ".";
  static final String PARENT = "..";

  private static final ImmutableSet<String> RESERVED_NAMES = ImmutableSet.of(THIS, PARENT);

  private final FileKey key;

  // TODO(cgdecker): TreeMap, with Collator for case insensitive file systems?
  private final Map<String, FileKey> entries;

  DirectoryTable(FileKey key, boolean caseSensitive) {
    this.key = checkNotNull(key);
    if (caseSensitive) {
      entries = new HashMap<>();
    } else {
      // TODO(cgdecker): should the user be able to specify the Locale for this? meh.
      // TODO(cgdecker): use ICU4J's Collator instead?
      entries = new TreeMap<>(Collator.getInstance());
    }
  }

  /**
   * Creates a copy of this table. The copy does <i>not</i> contain a copy of the entries in this
   * table.
   */
  @Override
  public DirectoryTable copy(FileKey newKey) {
    return new DirectoryTable(newKey, isCaseSensitive());
  }

  private boolean isCaseSensitive() {
    return !(entries instanceof SortedMap);
  }

  @SuppressWarnings("unchecked")
  private Comparator<String> comparator() {
    if (entries instanceof SortedMap) {
      return (Comparator<String>) ((SortedMap<String, FileKey>) entries).comparator();
    }

    return Ordering.natural();
  }

  @Override
  public int size() {
    return 0;
  }

  /**
   * Returns the file key of this directory.
   */
  public FileKey key() {
    return key;
  }

  /**
   * Returns the file key of this directory's parent.
   */
  public FileKey parent() {
    return get(PARENT);
  }

  /**
   * Links this directory to the given parent directory key. Also links it to itself.
   */
  public void linkParent(FileKey parentKey) {
    checkNotNull(parentKey);
    linkInternal(THIS, key);
    linkInternal(PARENT, parentKey);
  }

  /**
   * Unlinks this directory from its parent directory and from itself.
   */
  public void unlinkParent() {
    unlinkInternal(THIS);
    unlinkInternal(PARENT);
  }

  /**
   * Links the given name to the given file key in this table.
   *
   * @throws IllegalArgumentException if {@code name} is {@code .} or {@code ..}, which are
   *     reserved for linking to this file's key and the parent file's key
   */
  public void link(String name, FileKey key) {
    linkInternal(checkValidName(name, "link"), key);
  }

  private void linkInternal(String name, FileKey key) {
    checkArgument(!entries.containsKey(name), "entry '%s' already exists", name);
    entries.put(name, key);
    key.linked();
  }

  /**
   * Unlinks the given name from any key it is linked to in this table. Returns the file key that
   * was linked to the name, or {@code null} if no such mapping was present.
   *
   * @throws IllegalArgumentException if {@code name} is {@code .} or {@code ..}, which are
   *     reserved for linking to this file's key and the parent file's key
   */
  public void unlink(String name) {
    unlinkInternal(checkValidName(name, "unlink"));
  }

  private void unlinkInternal(String name) {
    entries.remove(name).unlinked();
  }

  /**
   * Returns whether or not this table contains an entry for the given name.
   */
  public boolean containsEntry(String name) {
    return entries.containsKey(name);
  }

  /**
   * Returns the entry with the given name in this directory.
   *
   * @throws IllegalArgumentException if the table does not contain an entry with the given name
   */
  public FileKey get(String name) {
    FileKey key = entries.get(name);
    checkArgument(key != null, "no entry named %s", name);
    return key;
  }

  /**
   * Returns the name that links to the given file key in this directory, throwing an exception if
   * zero names or more than one name links to the key. Should only be used for getting the name of
   * a directory, as directories cannot have more than one link.
   */
  public String getName(FileKey fileKey) {
    String result = null;
    for (Map.Entry<String, FileKey> entry : entries.entrySet()) {
      String name = entry.getKey();
      FileKey key = entry.getValue();
      if (key.equals(fileKey)) {
        if (result == null) {
          result = name;
        } else {
          throw new IllegalArgumentException("more than one name links to the given key");
        }
      }
    }

    if (result == null) {
      throw new IllegalArgumentException("directory contains no links to the given key");
    }

    return result;
  }

  /**
   * Returns an unmodifiable map view of this table.
   */
  public Map<String, FileKey> asMap() {
    return Collections.unmodifiableMap(entries);
  }

  /**
   * Creates an immutable sorted snapshot of the names this directory contains, excluding
   * "." and "..".
   */
  public ImmutableSortedSet<String> snapshot() {
    ImmutableSortedSet.Builder<String> builder =
        ImmutableSortedSet.orderedBy(comparator());

    for (String name : entries.keySet()) {
      if (!RESERVED_NAMES.contains(name)) {
        builder.add(name);
      }
    }
    return builder.build();
  }

  private static String checkValidName(String name, String action) {
    checkArgument(!RESERVED_NAMES.contains(name), "cannot %s: %s", action, name);
    return name;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("key", key)
        .toString();
  }
}
