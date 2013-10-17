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

import com.ibm.icu.lang.UCharacter;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normalizations that can be applied to names in paths. Includes both Unicode normalizations and
 * case folding normalizations for case insensitive paths.
 *
 * @author Colin Decker
 */
public enum Normalization {

  /**
   * No normalization.
   */
  NONE(0) {
    @Override
    public String normalize(String string) {
      return string;
    }
  },

  /**
   * Unicode composed normalization (form {@linkplain Normalizer.Form#NFC NFC}).
   */
  NORMALIZE_NFC(Pattern.CANON_EQ) {
    @Override
    public String normalize(String string) {
      return Normalizer.normalize(string, Normalizer.Form.NFC);
    }
  },

  /**
   * Unicode decomposed normalization (form {@linkplain Normalizer.Form#NFD NFD}).
   */
  NORMALIZE_NFD(Pattern.CANON_EQ) {
    @Override
    public String normalize(String string) {
      return Normalizer.normalize(string, Normalizer.Form.NFD);
    }
  },

  /**
   * Unicode case folding for case insensitive paths. Requires ICU4J on the classpath.
   */
  CASE_FOLD(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) {
    @Override
    public String normalize(String string) {
      return UCharacter.foldCase(string, true);
    }
  },

  /**
   * ASCII case folding for simple case insensitive paths.
   */
  CASE_FOLD_ASCII(Pattern.CASE_INSENSITIVE) {
    @Override
    public String normalize(String string) {
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
  public abstract String normalize(String string);

  /**
   * Returns the flags that should be used when creating a regex {@link Pattern} in order to
   * approximate this normalization.
   */
  public int getPatternFlags() {
    return patternFlags;
  }
}
