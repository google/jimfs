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

package com.google.jimfs.path;

import com.google.common.base.Ascii;
import com.google.common.base.Function;

import com.ibm.icu.lang.UCharacter;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normalizations that can be applied to names in paths. Includes both Unicode normalizations and
 * case folding normalizations for case insensitive paths.
 *
 * @author Colin Decker
 */
public enum Normalization implements Function<String, String> {

  /**
   * No normalization.
   */
  NONE(0) {
    @Override
    public String apply(String string) {
      return string;
    }
  },

  /**
   * Unicode composed normalization (form {@linkplain Normalizer.Form#NFC NFC}).
   */
  NFC(Pattern.CANON_EQ) {
    @Override
    public String apply(String string) {
      return Normalizer.normalize(string, Normalizer.Form.NFC);
    }
  },

  /**
   * Unicode decomposed normalization (form {@linkplain Normalizer.Form#NFD NFD}).
   */
  NFD(Pattern.CANON_EQ) {
    @Override
    public String apply(String string) {
      return Normalizer.normalize(string, Normalizer.Form.NFD);
    }
  },

  /**
   * Unicode case folding for case insensitive paths. Requires ICU4J on the classpath.
   */
  CASE_FOLD_UNICODE(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) {
    // TODO(cgdecker): This does default case folding, e.g. non-Turkish case folding.
    // Should there be an option to use the Turkish-correct case folding instead?
    @Override
    public String apply(String string) {
      return UCharacter.foldCase(string, true);
    }
  },

  /**
   * ASCII case folding for simple case insensitive paths.
   */
  CASE_FOLD_ASCII(Pattern.CASE_INSENSITIVE) {
    @Override
    public String apply(String string) {
      return Ascii.toLowerCase(string);
    }
  };

  private final int patternFlags;

  private Normalization(int patternFlags) {
    this.patternFlags = patternFlags;
  }

  /**
   * Applies this normalization to the given string, returning the normalized result.
   */
  @Override
  public abstract String apply(String string);

  /**
   * Returns the flags that should be used when creating a regex {@link Pattern} in order to
   * approximate this normalization.
   */
  public final int patternFlags() {
    return patternFlags;
  }

  /**
   * Applies the given normalizations to the given string in order, returning the normalized
   * result.
   */
  public static String normalize(String string, Iterable<Normalization> normalizations) {
    String result = string;
    for (Normalization normalization : normalizations) {
      result = normalization.apply(result);
    }
    return result;
  }

  /**
   * Compiles a regex pattern using flags based on the given normalizations.
   */
  @SuppressWarnings("MagicConstant")
  public static Pattern compilePattern(String regex, Iterable<Normalization> normalizations) {
    int flags = 0;
    for (Normalization normalization : normalizations) {
      flags |= normalization.patternFlags();
    }
    return Pattern.compile(regex, flags);
  }
}
