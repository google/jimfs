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

package com.google.jimfs.internal;

import static org.truth0.Truth.ASSERT;

import com.google.common.testing.EqualsTester;
import com.google.jimfs.path.Normalization;

import com.ibm.icu.text.Normalizer2;

import org.junit.Test;

import java.text.Collator;
import java.util.Locale;

/**
 * Tests for {@link Name}.
 *
 * @author Colin Decker
 */
public class NameTest {

  private static final Collator COLLATOR = Collator.getInstance(Locale.US);
  static {
    COLLATOR.setStrength(Collator.SECONDARY);
  }

  private static final Normalizer2 NORMALIZER = Normalizer2.getNFKCCasefoldInstance();

  @Test
  public void testNoNormalization() {
    Name foo = Name.simple("foo");
    Name bar = Name.simple("bar");

    ASSERT.that(foo.toString()).isEqualTo("foo");
    ASSERT.that(bar.toString()).isEqualTo("bar");

    new EqualsTester()
        .addEqualityGroup(foo, Name.simple("foo"))
        .addEqualityGroup(bar, Name.simple("bar"))
        .addEqualityGroup(Name.simple("Foo"))
        .addEqualityGroup(Name.simple("BAR"))
        .testEquals();
  }

  @Test
  public void testNormalizedCanonical() {
    Name first = Name.normalized("Am\u00e9lie", Normalization.none(), Normalization.normalized());
    Name second = Name.normalized("Ame\u0301lie", Normalization.none(), Normalization.normalized());

    ASSERT.that(first).isEqualTo(second);
    ASSERT.that(first.toString()).isNotEqualTo(second.toString());
  }

  @Test
  public void testNormalizedDisplay() {
    Name first = Name.normalized("Am\u00e9lie", Normalization.normalized(), Normalization.none());
    Name second = Name.normalized("Ame\u0301lie", Normalization.normalized(), Normalization.none());

    ASSERT.that(first).isNotEqualTo(second);
    ASSERT.that(first.toString()).isEqualTo(second.toString());
  }

  @Test
  public void testNormalizedBoth() {
    Name first = Name.normalized("Am\u00e9lie",
        Normalization.normalized(), Normalization.normalized());
    Name second = Name.normalized("Ame\u0301lie",
        Normalization.normalized(), Normalization.normalized());

    ASSERT.that(first).isEqualTo(second);
    ASSERT.that(first.toString()).isEqualTo(second.toString());
  }

  @Test
  public void testCaseInsensitiveCanonical() {
    Name first = Name.normalized("Am\u00e9lie",
        Normalization.none(), Normalization.caseInsensitive());
    Name second = Name.normalized("AM\u00c9LIE",
        Normalization.none(), Normalization.caseInsensitive());
    Name third = Name.normalized("Ame\u0301lie",
        Normalization.normalized(), Normalization.caseInsensitive());

    ASSERT.that(first).isEqualTo(second);
    ASSERT.that(first.toString()).isNotEqualTo(second.toString());

    ASSERT.that(third).isNotEqualTo(first);
    ASSERT.that(third).isNotEqualTo(second);
  }

  @Test
  public void testCaseInsensitiveAsciiCanonical() {
    Name first = Name.normalized("Am\u00e9lie",
        Normalization.none(), Normalization.caseInsensitiveAscii());
    Name second = Name.normalized("AM\u00c9LIE",
        Normalization.none(), Normalization.caseInsensitiveAscii());
    Name third = Name.normalized("Amelie",
        Normalization.none(), Normalization.caseInsensitiveAscii());
    Name fourth = Name.normalized("AMELIE",
        Normalization.none(), Normalization.caseInsensitiveAscii());

    ASSERT.that(first).isNotEqualTo(second);
    ASSERT.that(first.toString()).isNotEqualTo(second.toString());

    ASSERT.that(third).isEqualTo(fourth);
    ASSERT.that(third.toString()).isNotEqualTo(fourth.toString());
  }

  @Test
  public void testNormalizedCaseInsensitiveCanonical() {
    Name first = Name.normalized("Am\u00e9lie",
        Normalization.none(), Normalization.normalizedCaseInsensitive());
    Name second = Name.normalized("AM\u00c9LIE",
        Normalization.none(), Normalization.normalizedCaseInsensitive());
    Name third = Name.normalized("Ame\u0301lie",
        Normalization.normalized(), Normalization.normalizedCaseInsensitive());
    Name fourth = Name.normalized("AME\u0301LIE",
        Normalization.normalized(), Normalization.normalizedCaseInsensitive());

    Name[] names = {first, second, third, fourth};
    for (int i = 0; i < names.length; i++) {
      for (int j = i; j < names.length; j++) {
        ASSERT.that(names[i]).isEqualTo(names[j]);
      }
    }
  }
}
