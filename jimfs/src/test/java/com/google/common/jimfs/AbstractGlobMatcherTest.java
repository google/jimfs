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

package com.google.common.jimfs;

import org.junit.Test;

/**
 * @author Colin Decker
 */
public abstract class AbstractGlobMatcherTest extends AbstractPathMatcherTest {

  @Test
  public void testMatching_literal() {
    assertThat("foo").matches("foo");
    assertThat("/foo").matches("/foo");
    assertThat("/foo/bar/baz").matches("/foo/bar/baz");
  }

  @Test
  public void testMatching_questionMark() {
    assertThat("?")
        .matches("a", "A", "$", "5", "_")
        .doesNotMatch("/", "ab", "");
    assertThat("??").matches("ab");
    assertThat("????").matches("1234");
    assertThat("?oo?")
        .matches("book", "doom")
        .doesNotMatch("/oom");
    assertThat("/?oo/ba?").matches("/foo/bar");
    assertThat("foo.?").matches("foo.h");
    assertThat("foo.??").matches("foo.cc");
  }

  @Test
  public void testMatching_star() {
    assertThat("*")
        .matches("a", "abc", "298347829473928423", "abc12345", "")
        .doesNotMatch("/", "/abc");
    assertThat("/*")
        .matches("/a", "/abcd", "/abc123", "/")
        .doesNotMatch("/foo/bar");
    assertThat("/*/*/*")
        .matches("/a/b/c", "/foo/bar/baz")
        .doesNotMatch("/foo/bar", "/foo/bar/baz/abc");
    assertThat("/*/bar")
        .matches("/foo/bar", "/abc/bar")
        .doesNotMatch("/bar");
    assertThat("/foo/*")
        .matches("/foo/bar", "/foo/baz")
        .doesNotMatch("/foo", "foo/bar", "/foo/bar/baz");
    assertThat("/foo*/ba*")
        .matches("/food/bar", "/fool/bat", "/foo/ba", "/foot/ba", "/foo/bar", "/foods/bartender")
        .doesNotMatch("/food/baz/bar");
    assertThat("*.java")
        .matches("Foo.java", "Bar.java", "GlobPatternTest.java", "Foo.java.java", ".java")
        .doesNotMatch("Foo.jav", "Foo", "java.Foo", "Foo.java.");
    assertThat("Foo.*")
        .matches("Foo.java", "Foo.txt", "Foo.tar.gz", "Foo.Foo.", "Foo.")
        .doesNotMatch("Foo", ".Foo");
    assertThat("*/*.java")
        .matches("foo/Bar.java", "foo/.java");
    assertThat("*/Bar.*")
        .matches("foo/Bar.java");
    assertThat(".*")
        .matches(".bashrc", ".bash_profile");
    assertThat("*.............").matches(
        "............a............a..............a.............a............a.........." +
        ".........................................................a....................");
    assertThat("*.............*..").matches(
        "............a............a..............a.............a............a.........." +
        "..........a...................................................................");
    assertThat(".................*........*.*.....*....................*..............*").matches(
        ".................................abc.........................................." +
        ".............................................................................." +
        ".............................................................................." +
        ".............................................12..............................." +
        ".........................................................................hello" +
        "..............................................................................");
  }

  @Test
  public void testMatching_starStar() {
    assertThat("**")
        .matches("", "a", "abc", "293874982374913794141", "/foo/bar/baz", "foo/bar.txt");
    assertThat("**foo")
        .matches("foo", "barfoo", "/foo", "/a/b/c/foo", "c.foo", "a/b/c.foo")
        .doesNotMatch("foo.bar", "/a/b/food");
    assertThat("/foo/**/bar.txt")
        .matches("/foo/baz/bar.txt", "/foo/bar/asdf/bar.txt")
        .doesNotMatch("/foo/bar.txt", "/foo/baz/bar");
    assertThat("**/*.java")
        .matches("/Foo.java", "foo/Bar.java", "/.java", "foo/.java");
  }

  @Test
  public void testMatching_brackets() {
    assertThat("[ab]")
        .matches("a", "b")
        .doesNotMatch("ab", "ba", "aa", "bb", "c", "", "/");
    assertThat("[a-d]")
        .matches("a", "b", "c", "d")
        .doesNotMatch("e", "f", "z", "aa", "ab", "abcd", "", "/");
    assertThat("[a-dz]")
        .matches("a", "b", "c", "d", "z")
        .doesNotMatch("e", "f", "aa", "ab", "dz", "", "/");
    assertThat("[!b]")
        .matches("a", "c", "d", "0", "!", "$")
        .doesNotMatch("b", "/", "", "ac");
    assertThat("[!b-d3]")
        .matches("a", "e", "f", "0", "1", "2", "4")
        .doesNotMatch("b", "c", "d", "3");
    assertThat("[-]").matches("-");
    assertThat("[-a-c]").matches("-", "a", "b", "c");
    assertThat("[!-a-c]")
        .matches("d", "e", "0")
        .doesNotMatch("a", "b", "c", "-");
    assertThat("[\\d]")
        .matches("\\", "d")
        .doesNotMatch("0", "1");
    assertThat("[\\s]")
        .matches("\\", "s")
        .doesNotMatch(" ");
    assertThat("[\\]")
        .matches("\\")
        .doesNotMatch("]");
  }

  @Test
  public void testMatching_curlyBraces() {
    assertThat("{a,b}")
        .matches("a", "b")
        .doesNotMatch("/", "c", "0", "", ",", "{", "}");
    assertThat("{ab,cd}")
        .matches("ab", "cd")
        .doesNotMatch("bc", "ac", "ad", "ba", "dc", ",");
    assertThat(".{h,cc}")
        .matches(".h", ".cc")
        .doesNotMatch("h", "cc");
    assertThat("{?oo,ba?}")
        .matches("foo", "boo", "moo", "bat", "bar", "baz");
    assertThat("{[Ff]oo*,[Bb]a*,[A-Ca-c]*/[!z]*.txt}")
        .matches("foo", "Foo", "fools", "ba", "Ba", "bar", "Bar", "Bart", "c/y.txt", "Cat/foo.txt")
        .doesNotMatch("Cat", "Cat/foo", "blah", "bAr", "c/z.txt", "c/.txt", "*");
  }

  @Test
  public void testMatching_escapes() {
    assertThat("\\\\").matches("\\");
    assertThat("\\*").matches("*");
    assertThat("\\*\\*").matches("**");
    assertThat("\\[").matches("[");
    assertThat("\\{").matches("{");
    assertThat("\\a").matches("a");
    assertThat("{a,\\}}").matches("a", "}");
    assertThat("{a\\,,b}")
        .matches("a,", "b")
        .doesNotMatch("a", ",");
  }

  @Test
  public void testMatching_various() {
    assertThat("**/[A-Z]*.{[Jj][Aa][Vv][Aa],[Tt][Xx][Tt]}")
        .matches("/foo/bar/Baz.java", "/A.java", "bar/Test.JAVA", "foo/Foo.tXt");
  }

  @Test
  public void testInvalidSyntax() {
    assertSyntaxError("\\");
    assertSyntaxError("[");
    assertSyntaxError("[]");
    assertSyntaxError("{");
    assertSyntaxError("{{}");
    assertSyntaxError("{a,b,a{b,c},d}");
  }
}
