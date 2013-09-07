package com.google.jimfs.path;

import com.ibm.icu.text.Normalizer2;

/**
 * Case sensitivity settings for paths. Note that path case sensitivity only affects the case
 * sensitivity of lookups. Two path objects with the same characters in different cases will
 * always be considered unequal.
 *
 * @author Colin Decker
 */
public enum CaseSensitivity {

  /**
   * Paths are case sensitive.
   */
  CASE_SENSITIVE {
    @Override
    public Name createName(String string) {
      return Name.simple(string);
    }
  },

  /**
   * Paths are case insensitive, but only for ASCII characters. Faster than
   * {@link #CASE_INSENSITIVE_UNICODE} if you only plan on using ASCII file names anyway.
   */
  CASE_INSENSITIVE_ASCII {
    @Override
    public Name createName(String string) {
      return Name.caseInsensitiveAscii(string);
    }
  },

  /**
   * Paths are case sensitive by way of Unicode NFKC Casefolding normalization. Requires ICU4J
   * on your classpath.
   */
  @SuppressWarnings("unused")
  CASE_INSENSITIVE_UNICODE {
    @Override
    public Name createName(String string) {
      return Name.normalizing(string, Normalizer2.getNFKCCasefoldInstance());
    }
  };

  /**
   * Creates a new name with these case sensitivity settings.
   */
  public abstract Name createName(String string);
}
