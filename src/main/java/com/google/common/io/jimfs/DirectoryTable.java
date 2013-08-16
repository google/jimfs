package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

/**
 * A table of directory entries which link names to {@linkplain File files}.
 *
 * @author Colin Decker
 */
final class DirectoryTable implements FileContent {

  static final String SELF = ".";
  static final String PARENT = "..";

  private static final ImmutableSet<String> RESERVED_NAMES = ImmutableSet.of(SELF, PARENT);

  private final Map<String, File> entries;

  DirectoryTable(boolean caseSensitive) {
    if (caseSensitive) {
      entries = new HashMap<>();
    } else {
      // TODO(cgdecker): should the user be able to specify the Locale for this? meh.
      // TODO(cgdecker): use ICU4J's Collator instead?
      // TODO(cgdecker): use a HashMap with CollationKey keys instead?
      entries = new TreeMap<>(collator());
    }
  }

  private static Collator collator() {
    Collator collator = Collator.getInstance();
    // secondary = different case considered same; different accents considered different
    collator.setStrength(Collator.SECONDARY);
    return collator;
  }

  /**
   * Creates a copy of this table. The copy does <i>not</i> contain a copy of the entries in this
   * table.
   */
  @Override
  public DirectoryTable copy() {
    return new DirectoryTable(isCaseSensitive());
  }

  private boolean isCaseSensitive() {
    return !(entries instanceof SortedMap);
  }

  @SuppressWarnings("unchecked")
  private Comparator<String> comparator() {
    if (entries instanceof SortedMap) {
      return (Comparator<String>) ((SortedMap<String, File>) entries).comparator();
    }

    return Ordering.natural();
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
  public void link(String name, File file) {
    linkInternal(checkValidName(name, "link"), file);
  }

  private void linkInternal(String name, File file) {
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
   * Returns the file linked by the given name in this directory.
   */
  @Nullable
  public File get(String name) {
    return entries.get(name);
  }

  /**
   * Returns the canonical form of the given name in this directory.
   *
   * @throws IllegalArgumentException if the table does not contain an entry with the given name
   */
  public String canonicalize(String name) {
    if (entries instanceof SortedMap<?, ?>) {
      SortedMap<String, File> sorted = (SortedMap<String, File>) entries;
      SortedMap<String, File> tailMap = sorted.tailMap(name);
      if (!tailMap.isEmpty()) {
        String possibleMatch = sorted.tailMap(name).firstKey();
        if (sorted.comparator().compare(name, possibleMatch) == 0) {
          return possibleMatch;
        }
      }
    } else {
      if (entries.containsKey(name)) {
        return name;
      }
    }
    throw new IllegalArgumentException("no entry matching '" + name + "' in this directory");
  }

  /**
   * Returns the name that links to the given file key in this directory, throwing an exception if
   * zero names or more than one name links to the key. Should only be used for getting the name of
   * a directory, as directories cannot have more than one link.
   */
  public String getName(File file) {
    String result = null;
    for (Map.Entry<String, File> entry : entries.entrySet()) {
      String name = entry.getKey();
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
}
