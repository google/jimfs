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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Ordering;

import javax.annotation.Nullable;

/**
 * Immutable representation of a file name. Used both for the name components of paths and as the
 * keys for directory entries.
 *
 * <p>A name has both a display string (used in the {@code toString()} form of a {@code Path} as
 * well as for {@code Path} equality and sort ordering) and a canonical string, which is used for
 * determining equality of the name during file lookup.
 *
 * <p>Note: all factory methods return a constant name instance when given the original string "."
 * or "..", ensuring that those names can be accessed statically elsewhere in the code while still
 * being equal to any names created for those values, regardless of normalization settings.
 *
 * @author Colin Decker
 */
final class Name {

  /** The empty name. */
  static final Name EMPTY = new Name("", "");

  /** The name to use for a link from a directory to itself. */
  public static final Name SELF = new Name(".", ".");

  /** The name to use for a link from a directory to its parent directory. */
  public static final Name PARENT = new Name("..", "..");

  /**
   * Creates a new name with no normalization done on the given string.
   */
  @VisibleForTesting
  static Name simple(String name) {
    switch (name) {
      case ".":
        return SELF;
      case "..":
        return PARENT;
      default:
        return new Name(name, name);
    }
  }

  /**
   * Creates a name with the given display representation and the given canonical representation.
   */
  public static Name create(String display, String canonical) {
    return new Name(display, canonical);
  }

  private final String display;
  private final String canonical;

  private Name(String display, String canonical) {
    this.display = checkNotNull(display);
    this.canonical = checkNotNull(canonical);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof Name) {
      Name other = (Name) obj;
      return canonical.equals(other.canonical);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Util.smearHash(canonical.hashCode());
  }

  @Override
  public String toString() {
    return display;
  }

  /**
   * Returns an ordering that orders names by their display representation.
   */
  public static Ordering<Name> displayOrdering() {
    return DISPLAY_ORDERING;
  }

  /**
   * Returns an ordering that orders names by their canonical representation.
   */
  public static Ordering<Name> canonicalOrdering() {
    return CANONICAL_ORDERING;
  }

  private static final Ordering<Name> DISPLAY_ORDERING =
      Ordering.natural()
          .onResultOf(
              new Function<Name, String>() {
                @Override
                public String apply(Name name) {
                  return name.display;
                }
              });

  private static final Ordering<Name> CANONICAL_ORDERING =
      Ordering.natural()
          .onResultOf(
              new Function<Name, String>() {
                @Override
                public String apply(Name name) {
                  return name.canonical;
                }
              });
}
