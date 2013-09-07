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
