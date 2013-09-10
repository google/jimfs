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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Ascii;

import com.ibm.icu.text.Normalizer2;

import java.text.CollationKey;
import java.text.Collator;

/**
 * Immutable representation of a file name. Used both for the name components of paths and as the
 * keys for directory entries.
 *
 * <p>A name's string representation (returned by {@code toString()} is always the original string
 * it was created from. A {@linkplain #simple} name just contains the string itself. Other names
 * also have a normalized (in some way) canonical form which is used for determining the equality
 * of two names but never displayed or used for anything else.
 *
 * <p>Different types of names (with different canonical forms) may not equal one another even if
 * they effectively have the same canonical form, as the canonical form is not required to be a
 * string.
 *
 * <p>Note: all factory methods return a constant simple name instance when given the original
 * string "." or "..", ensuring that those names can be accessed statically elsewhere in the code
 * while still being equal to any names created for those values, regardless of case sensitivity
 * settings.
 *
 * @author Colin Decker
 */
abstract class Name {

  /**
   * Returns the name to use for a link to the same directory.
   */
  public static final Name SELF = new SimpleName(".");

  /**
   * Returns the name to use for a link to a parent directory.
   */
  public static final Name PARENT = new SimpleName("..");

  /**
   * Creates a new name with the name itself as the canonical form.
   */
  public static Name simple(String name) {
    switch (name) {
      case ".":
        return SELF;
      case "..":
        return PARENT;
      default:
        return new SimpleName(name);
    }
  }

  /**
   * Creates a new name with a {@link CollationKey} created from it by the given collator as the
   * canonical form.
   */
  public static Name collating(String name, Collator collator) {
    switch (name) {
      case ".":
        return SELF;
      case "..":
        return PARENT;
      default:
        return new CanonicalFormName(name, collator.getCollationKey(name));
    }
  }

  /**
   * Creates a new name with a canonical form created by normalizing it with the given normalizer.
   */
  public static Name normalizing(String name, Normalizer2 normalizer) {
    switch (name) {
      case ".":
        return SELF;
      case "..":
        return PARENT;
      default:
        return new CanonicalFormName(name, normalizer.normalize(name));
    }
  }

  /**
   * Creates a new name with itself with all upper-case ASCII characters normalized to lower-case
   * as the canonical form.
   */
  public static Name caseInsensitiveAscii(String name) {
    switch (name) {
      case ".":
        return SELF;
      case "..":
        return PARENT;
      default:
        return new CanonicalFormName(name, Ascii.toLowerCase(name));
    }
  }

  /**
   * Creates a new name with the given original string value and the given canonical value.
   */
  public static Name create(String original, Object canonical) {
    switch (original) {
      case ".":
        return SELF;
      case "..":
        return PARENT;
      default:
        return new CanonicalFormName(original, canonical);
    }
  }

  protected final String string;

  private Name(String string) {
    this.string = checkNotNull(string);
  }

  /**
   * Returns the original string form of this name.
   */
  @Override
  public final String toString() {
    return string;
  }

  /**
   * Simple name wrapping a string.
   */
  private static final class SimpleName extends Name {

    private SimpleName(String string) {
      super(string);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof SimpleName && ((SimpleName) obj).string.equals(string);
    }

    @Override
    public int hashCode() {
      return string.hashCode();
    }
  }

  /**
   * A name that uses a separate canonical form field for equality.
   */
  private static final class CanonicalFormName extends Name {

    private final Object canonical;

    private CanonicalFormName(String string, Object canonical) {
      super(string);
      this.canonical = canonical;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof CanonicalFormName) {
        CanonicalFormName other = (CanonicalFormName) obj;
        return canonical.equals(other.canonical);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return canonical.hashCode();
    }
  }
}
