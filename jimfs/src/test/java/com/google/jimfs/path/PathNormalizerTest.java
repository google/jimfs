package com.google.jimfs.path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Tests for {@link PathNormalizer}.
 *
 * @author Colin Decker
 */
public class PathNormalizerTest {

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

  private PathNormalizer normalizer;

  @Test
  public void testCaseSensitive() {
    normalizer = PathNormalizer.caseSensitive();

    assertNormalizedEqual("foo", "foo");
    assertNormalizedUnequal("Foo", "foo");
  }

  @Test
  public void testCaseInsensitive() {
    normalizer = PathNormalizer.caseInsensitive();

    for (String[] row : CASE_INSENSITIVITY_TEST_DATA) {
      for (int i = 0; i < row.length; i++) {
        for (int j = i + 1; j < row.length; j++) {
          assertNormalizedEqual(row[i], row[j]);
        }
      }
    }
  }

  @Test
  public void testCaseInsensitiveAscii() {
    normalizer = PathNormalizer.caseInsensitiveAscii();

    String[] row = {"foo", "FOO", "fOo", "Foo"};
    for (int i = 0; i < row.length; i++) {
      for (int j = i + 1; j < row.length; j++) {
        assertNormalizedEqual(row[i], row[j]);
      }
    }

    assertNormalizedUnequal("weiß", "weiss");
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
