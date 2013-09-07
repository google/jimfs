package com.google.jimfs.path;

import static org.truth0.Truth.ASSERT;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.junit.Test;

/**
 * Tests for {@link PathType}.
 *
 * @author Colin Decker
 */
public class PathTypeTest {

  private final FakePathType type = new FakePathType(CaseSensitivity.CASE_SENSITIVE);

  @Test
  public void testBasicProperties() {
    ASSERT.that(type.getSeparator()).is("/");
    ASSERT.that(type.getOtherSeparators()).is("\\");
    ASSERT.that(type.getCaseSensitivity()).is(CaseSensitivity.CASE_SENSITIVE);
  }

  @Test
  public void testNames() {
    ASSERT.that(type.getName("foo", false)).is(Name.simple("foo"));
    ASSERT.that(type.asNames(ImmutableList.of("foo", "bar")))
        .iteratesAs(Name.simple("foo"), Name.simple("bar"));
  }

  @Test
  public void testNames_caseInsensitiveAscii() {
    FakePathType type2 = new FakePathType(CaseSensitivity.CASE_INSENSITIVE_ASCII);
    ASSERT.that(type2.getName("foo", false)).is(Name.caseInsensitiveAscii("foo"));
    ASSERT.that(type2.getName("foo", false)).isEqualTo(type2.getName("FOO", false));
    ASSERT.that(type2.asNames(ImmutableList.of("foo", "bar")))
        .iteratesAs(type2.asNames(ImmutableList.of("FOO", "bAr")));
  }

  @Test
  public void testParsePath() {
    SimplePath path = type.parsePath("foo", "bar/baz", "one\\two");
    ASSERT.that(path.isAbsolute()).isFalse();
    ASSERT.that(path.names()).iteratesAs(
        Name.simple("foo"), Name.simple("bar"), Name.simple("baz"),
        Name.simple("one"), Name.simple("two"));

    SimplePath path2 = type.parsePath("$one/", "\\two");
    ASSERT.that(path2.isAbsolute()).isTrue();
    ASSERT.that(path2.root()).is(Name.simple("$"));
    ASSERT.that(path2.names())
        .iteratesAs(Name.simple("one"), Name.simple("two"));
  }

  @Test
  public void testToString() {
    SimplePath path = type.parsePath("foo/bar\\baz");
    ASSERT.that(type.toString(path)).is("foo/bar/baz");

    SimplePath path2 = type.parsePath("$", "foo", "bar");
    ASSERT.that(type.toString(path2)).is("$foo/bar");
  }

  @Test
  public void testUnix() {
    PathType unix = PathType.unix();
    ASSERT.that(unix.getSeparator()).is("/");
    ASSERT.that(unix.getOtherSeparators()).is("");
    ASSERT.that(unix.getCaseSensitivity()).is(CaseSensitivity.CASE_SENSITIVE);

    SimplePath path = unix.parsePath("/", "foo", "bar");
    ASSERT.that(path.isAbsolute()).isTrue();
    ASSERT.that(path.root()).is(Name.simple("/"));
    ASSERT.that(path.names()).iteratesAs(Name.simple("foo"), Name.simple("bar"));
    ASSERT.that(unix.toString(path)).is("/foo/bar");

    SimplePath path2 = unix.parsePath("foo/bar/");
    ASSERT.that(path2.isAbsolute()).isFalse();
    ASSERT.that(path2.names()).iteratesAs(Name.simple("foo"), Name.simple("bar"));
    ASSERT.that(unix.toString(path2)).is("foo/bar");
  }

  @Test
  public void testWindows() {
    PathType windows = PathType.windows();
    ASSERT.that(windows.getSeparator()).is("\\");
    ASSERT.that(windows.getOtherSeparators()).is("/");
    ASSERT.that(windows.getCaseSensitivity()).is(CaseSensitivity.CASE_INSENSITIVE_ASCII);

    ASSERT.that(windows.getName("foo", false)).isEqualTo(Name.caseInsensitiveAscii("foo"));
    ASSERT.that(windows.getName("C:", true)).isEqualTo(windows.getName("c:", true));

    SimplePath path = windows.parsePath("C:\\", "foo", "bar");
    ASSERT.that(path.isAbsolute()).isTrue();
    ASSERT.that(String.valueOf(path.root())).is("C:");
    ASSERT.that(path.root()).isEqualTo(windows.getName("C:", true));
    ASSERT.that(path.root()).isEqualTo(windows.getName("c:", true));
    ASSERT.that(path.names())
        .iteratesAs(Name.caseInsensitiveAscii("foo"), Name.caseInsensitiveAscii("bar"));
    ASSERT.that(windows.toString(path)).is("C:\\foo\\bar");

    SimplePath path2 = windows.parsePath("foo/bar/");
    ASSERT.that(path2.isAbsolute()).isFalse();
    ASSERT.that(path2.names())
        .iteratesAs(Name.caseInsensitiveAscii("foo"), Name.caseInsensitiveAscii("bar"));
    ASSERT.that(windows.toString(path2)).is("foo\\bar");
  }

  /**
   * Arbitrary path type with $ as the root, / as the separator and \ as an alternate separator.
   */
  private static final class FakePathType extends PathType {

    protected FakePathType(CaseSensitivity caseSensitivity) {
      super(caseSensitivity, '/', '\\');
    }

    @Override
    public Name getName(String name, boolean root) {
      return getCaseSensitivity().createName(name);
    }

    @Override
    public SimplePath parsePath(String first, String... more) {
      String joined = Joiner.on(getSeparator()).join(Lists.asList(first, more));
      Name root = null;
      if (joined.startsWith("$")) {
        root = getName("$", true);
        joined = joined.substring(1);
      }

      Splitter splitter = Splitter.onPattern("[/\\\\]").omitEmptyStrings();
      return new SimplePath(root, asNames(splitter.split(joined)));
    }

    @Override
    public String toString(SimplePath path) {
      StringBuilder builder = new StringBuilder();
      if (path.isAbsolute()) {
        builder.append(path.root());
      }
      Joiner.on("/").appendTo(builder, path.names());
      return builder.toString();
    }
  }
}
