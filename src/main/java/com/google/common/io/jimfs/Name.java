package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import java.text.CollationKey;
import java.text.Collator;

/**
 * A file name; may be case-sensitive or insensitive.
 *
 * @author Colin Decker
 */
public abstract class Name {

  /**
   * Creates a new case-sensitive name.
   */
  public static Name caseSensitive(String name) {
    return new CaseSensitiveName(name);
  }

  /**
   * Creates a new case-insensitive name.
   */
  public static Name caseInsensitive(String name, Collator collator) {
    return new CaseInsensitiveName(name, collator);
  }

  /**
   * Returns the string form of this name.
   */
  @Override
  public abstract String toString();

  /**
   * A case-sensitive name that uses normal string comparison.
   */
  private static class CaseSensitiveName extends Name {

    private final String string;

    private CaseSensitiveName(String string) {
      this.string = checkNotNull(string);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof CaseSensitiveName) {
        CaseSensitiveName other = (CaseSensitiveName) obj;
        return string.equals(other.string);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return string.hashCode();
    }

    @Override
    public String toString() {
      return string;
    }
  }

  /**
   * A case-insensitive name that uses a {@link CollationKey} from a {@link Collator} with a
   * strength of {@link Collator#SECONDARY}.
   */
  private static class CaseInsensitiveName extends Name {

    private final CollationKey key;

    private CaseInsensitiveName(String string, Collator collator) {
      this.key = collator.getCollationKey(string);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof CaseInsensitiveName) {
        CaseInsensitiveName other = (CaseInsensitiveName) obj;
        return key.equals(other.key);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return key.hashCode();
    }

    @Override
    public String toString() {
      return key.getSourceString();
    }
  }
}
