package com.google.common.io.jimfs.path;

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
 * it was created from. All names also have a canonical form which is used for determining the
 * equality of two names but never displayed or used for anything else. A {@linkplain #simple}
 * name has the original name itself as the canonical form. Other implementations do some form of
 * normalization to produce the canonical form.
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
public final class Name {

  /**
   * Returns the name to use for a link to the same directory.
   */
  public static final Name SELF = new Name(".", ".");

  /**
   * Returns the name to use for a link to a parent directory.
   */
  public static final Name PARENT = new Name("..", "..");

  /**
   * Creates a new name with the name itself as the canonical form.
   */
  public static Name simple(String name) {
    switch (name) {
      case ".": return SELF;
      case "..": return PARENT;
      default: return new Name(checkNotNull(name), name);
    }
  }

  /**
   * Creates a new name with a {@link CollationKey} created from it by the given collator as the
   * canonical form.
   */
  public static Name collating(String name, Collator collator) {
    switch (name) {
      case ".": return SELF;
      case "..": return PARENT;
      default: return new Name(name, collator.getCollationKey(name));
    }
  }

  /**
   * Creates a new name with a canonical form created by normalizing it with the given normalizer.
   */
  public static Name normalizing(String name, Normalizer2 normalizer) {
    switch (name) {
      case ".": return SELF;
      case "..": return PARENT;
      default: return new Name(name, normalizer.normalize(name));
    }
  }

  /**
   * Creates a new name with itself with all upper-case ASCII characters normalized to lower-case
   * as the canonical form.
   */
  public static Name caseInsensitiveAscii(String name) {
    switch (name) {
      case ".": return SELF;
      case "..": return PARENT;
      default: return new Name(name, Ascii.toLowerCase(name));
    }
  }

  /**
   * Creates a new name with the given original string value and the given canonical value.
   */
  public static Name create(String original, Object canonical) {
    switch (original) {
      case ".": return SELF;
      case "..": return PARENT;
      default: return new Name(original, canonical);
    }
  }

  private final String string;
  private final Object canonical;

  private Name(String string, Object canonical) {
    this.string = string;
    this.canonical = canonical;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Name) {
      Name other = (Name) obj;
      return canonical.equals(other.canonical);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return canonical.hashCode();
  }

  /**
   * Returns the original string form of this name.
   */
  @Override
  public String toString() {
    return string;
  }
}
