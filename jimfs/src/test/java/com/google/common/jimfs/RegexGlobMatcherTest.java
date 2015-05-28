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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.regex.Pattern;

/**
 * Tests for {@link PathMatcher} instances created by {@link GlobToRegex}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class RegexGlobMatcherTest extends AbstractGlobMatcherTest {

  @Override
  protected PathMatcher matcher(String pattern) {
    return PathMatchers.getPathMatcher(
        "glob:" + pattern, "/", ImmutableSet.<PathNormalization>of());
  }

  @Override
  protected PathMatcher realMatcher(String pattern) {
    FileSystem defaultFileSystem = FileSystems.getDefault();
    if ("/".equals(defaultFileSystem.getSeparator())) {
      return defaultFileSystem.getPathMatcher("glob:" + pattern);
    }
    return null;
  }

  @Test
  public void testRegexTranslation() {
    assertGlobRegexIs("foo", "foo");
    assertGlobRegexIs("/", "/");
    assertGlobRegexIs("?", "[^/]");
    assertGlobRegexIs("*", "[^/]*");
    assertGlobRegexIs("**", ".*");
    assertGlobRegexIs("/foo", "/foo");
    assertGlobRegexIs("?oo", "[^/]oo");
    assertGlobRegexIs("*oo", "[^/]*oo");
    assertGlobRegexIs("**/*.java", ".*/[^/]*\\.java");
    assertGlobRegexIs("[a-z]", "[[^/]&&[a-z]]");
    assertGlobRegexIs("[!a-z]", "[[^/]&&[^a-z]]");
    assertGlobRegexIs("[-a-z]", "[[^/]&&[-a-z]]");
    assertGlobRegexIs("[!-a-z]", "[[^/]&&[^-a-z]]");
    assertGlobRegexIs("{a,b,c}", "(a|b|c)");
    assertGlobRegexIs("{?oo,[A-Z]*,foo/**}", "([^/]oo|[[^/]&&[A-Z]][^/]*|foo/.*)");
  }

  @Test
  public void testRegexEscaping() {
    assertGlobRegexIs("(", "\\(");
    assertGlobRegexIs(".", "\\.");
    assertGlobRegexIs("^", "\\^");
    assertGlobRegexIs("$", "\\$");
    assertGlobRegexIs("+", "\\+");
    assertGlobRegexIs("\\\\", "\\\\");
    assertGlobRegexIs("]", "\\]");
    assertGlobRegexIs(")", "\\)");
    assertGlobRegexIs("}", "\\}");
  }

  @Test
  public void testRegexTranslationWithMultipleSeparators() {
    assertGlobRegexIs("?", "[^\\\\/]", "\\/");
    assertGlobRegexIs("*", "[^\\\\/]*", "\\/");
    assertGlobRegexIs("/", "[\\\\/]", "\\/");
    assertGlobRegexIs("\\\\", "[\\\\/]", "\\/");
  }

  private static void assertGlobRegexIs(String glob, String regex) {
    assertGlobRegexIs(glob, regex, "/");
  }

  private static void assertGlobRegexIs(String glob, String regex, String separators) {
    assertEquals(regex, GlobToRegex.toRegex(glob, separators));
    Pattern.compile(regex); // ensure the regex syntax is valid
  }
}
