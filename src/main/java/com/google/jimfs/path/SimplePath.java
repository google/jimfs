package com.google.jimfs.path;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

/**
 * Simple struct representing a path.
 *
 * @author Colin Decker
 */
public class SimplePath {

  @Nullable
  protected final Name root;
  protected final ImmutableList<Name> names;

  /**
   * Creates a new path with the given root and names.
   */
  public SimplePath(@Nullable Name root, Iterable<Name> names) {
    this.root = root;
    this.names = ImmutableList.copyOf(names);
  }

  /**
   * Returns whether or not this path is absolute.
   */
  public boolean isAbsolute() {
    return root != null;
  }

  /**
   * Returns the root of this path.
   */
  @Nullable
  public Name root() {
    return root;
  }

  /**
   * Returns the names for this path.
   */
  public ImmutableList<Name> names() {
    return names;
  }
}
