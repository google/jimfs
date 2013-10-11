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

package com.google.jimfs;

import com.google.common.base.Ascii;
import com.google.common.base.Objects;

import com.ibm.icu.lang.UCharacter;

import java.text.Normalizer;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Normalization configuration for paths.
 *
 * @author Colin Decker
 */
public final class Normalization {

  /*
   * Note: This class aways does normalization with form NFC because it doesn't really matter
   * whether it uses NFC or NFD. This normalization only applies to internal lookups and doesn't
   * affect anything returned to the user.
   */

  private static final Normalization NONE
      = new Normalization(null, null);
  private static final Normalization CASE_INSENSITIVE
      = new Normalization(null, CaseFolding.ICU4J);
  private static final Normalization CASE_INSENSITIVE_ASCII
      = new Normalization(null, CaseFolding.ASCII);
  private static final Normalization NORMALIZED
      = new Normalization(Normalizer.Form.NFC, null);
  private static final Normalization NORMALIZED_CASE_INSENSITIVE
      = new Normalization(Normalizer.Form.NFC, CaseFolding.ICU4J);
  private static final Normalization NORMALIZED_CASE_INSENSITIVE_ASCII
      = new Normalization(Normalizer.Form.NFC, CaseFolding.ASCII);

  /**
   * No normalization is done on paths. Paths are case sensitive.
   */
  public static Normalization none() {
    return NONE;
  }

  /**
   * Paths are case insensitive.
   *
   * <p>Requires ICU4J for case folding.
   */
  public static Normalization caseInsensitive() {
    return CASE_INSENSITIVE;
  }

  /**
   * Paths are case insensitive for ASCII characters only.
   *
   * <p><b>Note:</b> {@link #caseInsensitive()} is preferable as it does proper Unicode case
   * folding. It should be used if there may be path names with non-ASCII characters. However, use
   * of {@link #caseInsensitive()} requires ICU4J on the classpath. This option is provided
   * primarily to allow simple case insensitivity without that dependency.
   */
  public static Normalization caseInsensitiveAscii() {
    return CASE_INSENSITIVE_ASCII;
  }

  /**
   * Paths are normalized with Unicode NFC normalization and are case sensitive.
   */
  public static Normalization normalized() {
    return NORMALIZED;
  }

  /**
   * Paths are normalized with Unicode NFC normalization and are case insensitive.
   */
  public static Normalization normalizedCaseInsensitive() {
    return NORMALIZED_CASE_INSENSITIVE;
  }

  /**
   * Paths are normalized with Unicode NFC normalization and are case insensitive for ASCII
   * characters only.
   *
   * <p><b>Note:</b> {@link #normalizedCaseInsensitive()} is preferable as it does proper
   * Unicode case folding. It should be used if there may be path names with non-ASCII characters.
   * However, use of {@link #normalizedCaseInsensitive()} requires ICU4J on the classpath.
   * This option is provided primarily to allow simple case insensitivity without that dependency.
   */
  public static Normalization normalizedCaseInsensitiveAscii() {
    return NORMALIZED_CASE_INSENSITIVE_ASCII;
  }

  @Nullable
  private final Normalizer.Form form;

  @Nullable
  private final CaseFolding folding;

  private Normalization(@Nullable Normalizer.Form form, @Nullable CaseFolding folding) {
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
    if (folding != null) {
      name = folding.foldCase(name);
    }
    return name;
  }

  /**
   * Compiles a {@link Pattern} from the given regex. The pattern will use settings based on this
   * normalization configuration to determine whether it does Unicode normalization or is case
   * insensitive when matching.
   *
   * <p>Note that this does not by any means guarantee that the returned pattern will match strings
   * exactly as if both they and the pattern were normalized by this.
   */
  public Pattern compilePattern(String regex) {
    int flags = 0;
    if (form != null) {
      flags |= Pattern.CANON_EQ;
    }
    if (folding != null) {
      flags |= Pattern.CASE_INSENSITIVE;

      if (folding == CaseFolding.ICU4J) {
        flags |= Pattern.UNICODE_CASE;
      }
    }

    return Pattern.compile(regex, flags);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Normalization) {
      Normalization other = (Normalization) obj;
      return Objects.equal(form, other.form) && Objects.equal(folding, other.folding);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(form, folding);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("form", form)
        .add("folding", folding)
        .toString();
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
