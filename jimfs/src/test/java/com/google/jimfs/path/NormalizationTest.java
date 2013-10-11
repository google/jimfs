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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Tests for {@link Normalization}.
 *
 * @author Colin Decker
 */
public class NormalizationTest {

  private Normalization normalizer;

  @Test
  public void testNone() {
    normalizer = Normalization.none();

    assertNormalizedEqual("foo", "foo");
    assertNormalizedUnequal("Foo", "foo");
    assertNormalizedUnequal("\u00c5", "\u212b");
    assertNormalizedUnequal("Am\u00e9lie", "Ame\u0301lie");
  }

  private static final String[][] CASE_INSENSITIVITY_TEST_DATA = {
      {"eﬃcient", "efficient", "eﬃcient", "Eﬃcient", "EFFICIENT"},
      {"ﬂour", "flour", "ﬂour", "Flour", "FLOUR"},
      {"poſt", "post", "poſt", "Poſt", "POST"},
      {"poﬅ", "post", "poﬅ", "Poﬅ", "POST"},
      {"ﬅop", "stop", "ﬅop", "Stop", "STOP"},
      {"tschüß", "tschüss", "tschüß", "Tschüß", "TSCHÜSS"},
      {"weiß", "weiss", "weiß", "Weiß", "WEISS"},
      {"WEIẞ", "weiss", "weiß", "Weiß", "WEIẞ"},
      {"στιγμας", "στιγμασ", "στιγμας", "Στιγμας", "ΣΤΙΓΜΑΣ"},
      {"ᾲ στο διάολο", "ὰι στο διάολο", "ᾲ στο διάολο", "Ὰͅ Στο Διάολο", "ᾺΙ ΣΤΟ ΔΙΆΟΛΟ"},
      {"Henry Ⅷ", "henry ⅷ", "henry ⅷ", "Henry Ⅷ", "HENRY Ⅷ"},
      {"I Work At Ⓚ", "i work at ⓚ", "i work at ⓚ", "I Work At Ⓚ", "I WORK AT Ⓚ"},
      {"ʀᴀʀᴇ", "ʀᴀʀᴇ", "ʀᴀʀᴇ", "Ʀᴀʀᴇ", "ƦᴀƦᴇ"},
      {"Ὰͅ", "ὰι", "ᾲ", "Ὰͅ", "ᾺΙ"}
  };

  @Test
  public void testCaseInsensitive() {
    normalizer = Normalization.caseInsensitive();

    for (String[] row : CASE_INSENSITIVITY_TEST_DATA) {
      for (int i = 0; i < row.length; i++) {
        for (int j = i; j < row.length; j++) {
          assertNormalizedEqual(row[i], row[j]);
        }
      }
    }
  }

  @Test
  public void testCaseInsensitiveAscii() {
    normalizer = Normalization.caseInsensitiveAscii();

    String[] row = {"foo", "FOO", "fOo", "Foo"};
    for (int i = 0; i < row.length; i++) {
      for (int j = i; j < row.length; j++) {
        assertNormalizedEqual(row[i], row[j]);
      }
    }

    assertNormalizedUnequal("weiß", "weiss");
  }

  private static final String[][] NORMALIZED_TEST_DATA = {
      {"\u00c5", "\u212b"}, // two forms of Å (one code point each)
      {"Am\u00e9lie", "Ame\u0301lie"} // two forms of Amélie (one composed, one decomposed)
  };

  @Test
  public void testNormalized() {
    normalizer = Normalization.normalized();

    for (String[] row : NORMALIZED_TEST_DATA) {
      for (int i = 0; i < row.length; i++) {
        for (int j = i; j < row.length; j++) {
          assertNormalizedEqual(row[i], row[j]);
        }
      }
    }
  }

  private static final String[][] NORMALIZED_CASE_INSENSITIVE_TEST_DATA = {
      {"\u00c5", "\u00e5", "\u212b"},
      {"Am\u00e9lie", "Am\u00c9lie", "Ame\u0301lie", "AME\u0301LIE"}
  };

  @Test
  public void testNormalizedCaseInsensitive() {
    normalizer = Normalization.normalizedCaseInsensitive();

    for (String[] row : NORMALIZED_CASE_INSENSITIVE_TEST_DATA) {
      for (int i = 0; i < row.length; i++) {
        for (int j = i; j < row.length; j++) {
          assertNormalizedEqual(row[i], row[j]);
        }
      }
    }
  }

  private static final String[][] NORMALIZED_CASE_INSENSITIVE_ASCII_TEST_DATA = {
      {"\u00e5", "\u212b"},
      {"Am\u00e9lie", "AME\u0301LIE"}
  };

  @Test
  public void testNormalizedCaseInsensitiveAscii() {
    normalizer = Normalization.normalizedCaseInsensitiveAscii();

    for (String[] row : NORMALIZED_CASE_INSENSITIVE_ASCII_TEST_DATA) {
      for (int i = 0; i < row.length; i++) {
        for (int j = i + 1; j < row.length; j++) {
          assertNormalizedUnequal(row[i], row[j]);
        }
      }
    }
  }

  /**
   * Asserts that the given strings normalize to the same string using the current normalizer.
   */
  private void assertNormalizedEqual(String first, String second) {
    assertEquals(normalizer.normalize(first), normalizer.normalize(second));
  }

  /**
   * Asserts that the given strings normalize to different strings using the current normalizer.
   */
  private void assertNormalizedUnequal(String first, String second) {
    assertNotEquals(normalizer.normalize(first), normalizer.normalize(second));
  }
}
