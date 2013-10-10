package com.google.jimfs.path;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Ascii;

import com.ibm.icu.lang.UCharacter;

import java.text.Normalizer;

import javax.annotation.Nullable;

/**
 * Normalizer for performing Unicode normalization and/or case folding on path name.
 *
 * @author Colin Decker
 */
public final class PathNormalizer {

  /**
   * Normalizer for case sensitive path names with no normalization.
   */
  public static PathNormalizer caseSensitive() {
    return new PathNormalizer(null, null);
  }

  /**
   * Normalizer for case insensitive path names. Does not perform any Unicode normalization.
   */
  public static PathNormalizer caseInsensitive() {
    return new PathNormalizer(null, CaseFolding.ICU4J);
  }

  /**
   * Normalizer for path names that are case insensitive for ASCII characters only. Does not
   * perform any Unicode normalization.
   *
   * <p><b>Note:</b> {@link #caseInsensitive()} is preferable as it does proper Unicode case
   * folding. It should be used if there may be path names with non-ASCII characters. However, use
   * of {@link #caseInsensitive()} requires ICU4J on the classpath. This option is provided
   * primarily to allow simple case insensitivity without that dependency.
   */
  public static PathNormalizer caseInsensitiveAscii() {
    return new PathNormalizer(null, CaseFolding.ASCII);
  }

  /**
   * Normalizer for case sensitive path names normalized with the given Unicode normalization form.
   */
  public static PathNormalizer caseSensitive(Normalizer.Form form) {
    return new PathNormalizer(checkNotNull(form), null);
  }

  /**
   * Normalizer for case insensitive path names normalized with the given Unicode normalization
   * form.
   */
  public static PathNormalizer caseInsensitive(Normalizer.Form form) {
    return new PathNormalizer(checkNotNull(form), CaseFolding.ICU4J);
  }

  /**
   * Setting for path names that are case insensitive for ASCII characters only, normalized with
   * the given Unicode normalization form.
   *
   * <p><b>Note:</b> {@link #caseInsensitive(Normalizer.Form)} is preferable as it does proper
   * Unicode case folding. It should be used if there may be path names with non-ASCII characters.
   * However, use of {@link #caseInsensitive(Normalizer.Form)} requires ICU4J on the classpath.
   * This option is provided primarily to allow simple case insensitivity without that dependency.
   */
  public static PathNormalizer caseInsensitiveAscii(Normalizer.Form form) {
    return new PathNormalizer(checkNotNull(form), CaseFolding.ASCII);
  }

  @Nullable
  private final Normalizer.Form form;

  @Nullable
  private final CaseFolding folding;

  private PathNormalizer(@Nullable Normalizer.Form form, @Nullable CaseFolding folding) {
    this.form = form;
    this.folding = folding;
  }

  /**
   * Returns the given name using these normalization settings. If a Unicode normalization form is
   * specified, the name will be normalized using that form. If the name should not be case
   * sensitive, the result is additionally case-folded using ICU4J.
   */
  public String normalize(String name) {
    if (form != null) {
      name = Normalizer.normalize(name, form);
    }

    if (folding == null) {
      return name;
    } else {
      return folding.foldCase(name);
    }
  }

  /**
   * Case folding settings.
   */
  private enum CaseFolding {
    ASCII {
      @Override
      public String foldCase(String string) {
        // this is actually case mapping, but it should be equivalent for ASCII
        return Ascii.toLowerCase(string);
      }
    },

    ICU4J {
      @Override
      public String foldCase(String string) {
        return UCharacter.foldCase(string, true);
      }
    };

    public abstract String foldCase(String string);
  }
}
