package com.google.common.io.jimfs.path;

import static org.truth0.Truth.ASSERT;

import com.google.common.testing.EqualsTester;

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
  public void testSimpleName() {
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
  public void testCollatingName() {
    Name foo = Name.collating("foo", COLLATOR);
    Name bar = Name.collating("bar", COLLATOR);

    ASSERT.that(foo.toString()).isEqualTo("foo");
    ASSERT.that(bar.toString()).isEqualTo("bar");

    ASSERT.that(Name.collating("FOO", COLLATOR).toString()).isEqualTo("FOO");
    ASSERT.that(Name.collating("foO", COLLATOR).toString()).isEqualTo("foO");

    new EqualsTester()
        .addEqualityGroup(foo,
            Name.collating("foo", COLLATOR),
            Name.collating("Foo", COLLATOR),
            Name.collating("FOO", COLLATOR))
        .addEqualityGroup(bar,
            Name.collating("bar", COLLATOR),
            Name.collating("bAr", COLLATOR),
            Name.collating("baR", COLLATOR))
        .addEqualityGroup(
            Name.collating("ÁÁ", COLLATOR),
            Name.collating("áá", COLLATOR),
            Name.collating("áÁ", COLLATOR),
            Name.collating("Áá", COLLATOR))
        .testEquals();
  }

  @Test
  public void testNormalizingName() {
    Name foo = Name.normalizing("foo", NORMALIZER);
    Name bar = Name.normalizing("bar", NORMALIZER);
    Name accented = Name.normalizing("Áá", NORMALIZER);

    ASSERT.that(foo.toString()).isEqualTo("foo");
    ASSERT.that(bar.toString()).isEqualTo("bar");

    ASSERT.that(Name.normalizing("FOO", NORMALIZER).toString()).isEqualTo("FOO");
    ASSERT.that(Name.normalizing("foO", NORMALIZER).toString()).isEqualTo("foO");

    ASSERT.that(Name.normalizing("Áá", NORMALIZER).toString()).isEqualTo("Áá");

    new EqualsTester()
        .addEqualityGroup(foo,
            Name.normalizing("foo", NORMALIZER),
            Name.normalizing("Foo", NORMALIZER),
            Name.normalizing("FOO", NORMALIZER))
        .addEqualityGroup(bar,
            Name.normalizing("bar", NORMALIZER),
            Name.normalizing("bAr", NORMALIZER),
            Name.normalizing("baR", NORMALIZER))
        .addEqualityGroup(accented,
            Name.normalizing("ÁÁ", NORMALIZER),
            Name.normalizing("áá", NORMALIZER),
            Name.normalizing("áÁ", NORMALIZER),
            Name.normalizing("Áá", NORMALIZER))
        .addEqualityGroup(
            Name.normalizing("aa", NORMALIZER),
            Name.normalizing("AA", NORMALIZER)
        )
        .testEquals();
  }

  @Test
  public void testCaseInsensitiveAsciiName() {
    Name foo = Name.caseInsensitiveAscii("foo");
    Name bar = Name.caseInsensitiveAscii("bar");

    ASSERT.that(foo.toString()).isEqualTo("foo");
    ASSERT.that(bar.toString()).isEqualTo("bar");

    ASSERT.that(Name.caseInsensitiveAscii("FOO").toString()).isEqualTo("FOO");
    ASSERT.that(Name.caseInsensitiveAscii("foO").toString()).isEqualTo("foO");

    new EqualsTester()
        .addEqualityGroup(foo,
            Name.caseInsensitiveAscii("foo"),
            Name.caseInsensitiveAscii("Foo"),
            Name.caseInsensitiveAscii("FOO"))
        .addEqualityGroup(bar,
            Name.caseInsensitiveAscii("bar"),
            Name.caseInsensitiveAscii("bAr"),
            Name.caseInsensitiveAscii("baR"))
        .testEquals();
  }

  @Test
  public void testSimpleVsCaseInsensitive() {
    // not equal even if raw string values are the same
    new EqualsTester()
        .addEqualityGroup(Name.simple("foo"))
        .addEqualityGroup(Name.collating("foo", COLLATOR))
        .testEquals();
  }

  @Test
  public void testSpecialNames() {
    // special cases so that "." and ".." are globally unique and the correct Name object can be
    // referenced statically
    new EqualsTester()
        .addEqualityGroup(Name.simple("."),
            Name.collating(".", COLLATOR),
            Name.caseInsensitiveAscii("."))
        .addEqualityGroup(Name.simple(".."),
            Name.collating("..", COLLATOR),
            Name.caseInsensitiveAscii(".."))
        .testEquals();
  }

  @Test
  public void testCanonicalizing() {
    Name canonical = Name.simple("foo");

    Name name = Name.create("foo", canonical);
    Name version1 = Name.create("FOO", canonical);
    Name version2 = Name.create("fOo", canonical);
    Name version3 = Name.create("BAR", canonical);

    new EqualsTester()
        .addEqualityGroup(name, version1, version2, version3)
        .addEqualityGroup(canonical)
        .testEquals();

    ASSERT.that(name.toString()).isEqualTo("foo");
    ASSERT.that(version1.toString()).isEqualTo("FOO");
    ASSERT.that(version2.toString()).isEqualTo("fOo");
    ASSERT.that(version3.toString()).isEqualTo("BAR");

    Name canonical2 = Name.create("C:\\", Name.simple("C:\\"));
    Name alternate = Name.create("c:", Name.simple("C:\\"));

    ASSERT.that(alternate).isEqualTo(canonical2);
  }
}
