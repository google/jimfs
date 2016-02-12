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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nullable;

/**
 * Abstract base class for tests of {@link PathMatcher} implementations.
 *
 * @author Colin Decker
 */
public abstract class AbstractPathMatcherTest {

  /**
   * Creates a new {@code PathMatcher} using the given pattern in the syntax this test is testing.
   */
  protected abstract PathMatcher matcher(String pattern);

  /**
   * Override to return a real matcher for the given pattern.
   */
  @Nullable
  protected PathMatcher realMatcher(String pattern) {
    return null;
  }

  protected void assertSyntaxError(String pattern) {
    try {
      matcher(pattern);
      fail();
    } catch (PatternSyntaxException expected) {
    }

    try {
      PathMatcher real = realMatcher(pattern);
      if (real != null) {
        fail();
      }
    } catch (PatternSyntaxException expected) {
    }
  }

  protected final PatternAsserter assertThat(String pattern) {
    return new PatternAsserter(pattern);
  }

  protected final class PatternAsserter {

    private final PathMatcher matcher;

    @Nullable private final PathMatcher realMatcher;

    PatternAsserter(String pattern) {
      this.matcher = matcher(pattern);
      this.realMatcher = realMatcher(pattern);
    }

    PatternAsserter matches(String... paths) {
      for (String path : paths) {
        assertTrue(
            "matcher '" + matcher + "' did not match '" + path + "'", matcher.matches(fake(path)));
        if (realMatcher != null) {
          Path realPath = Paths.get(path);
          assertTrue(
              "real matcher '" + realMatcher + "' did not match '" + realPath + "'",
              realMatcher.matches(realPath));
        }
      }
      return this;
    }

    PatternAsserter doesNotMatch(String... paths) {
      for (String path : paths) {
        assertFalse(
            "glob '" + matcher + "' should not have matched '" + path + "'",
            matcher.matches(fake(path)));
        if (realMatcher != null) {
          Path realPath = Paths.get(path);
          assertFalse(
              "real matcher '" + realMatcher + "' matched '" + realPath + "'",
              realMatcher.matches(realPath));
        }
      }
      return this;
    }
  }

  /**
   * Path that only provides toString().
   */
  private static Path fake(final String path) {
    return new Path() {
      @Override
      public FileSystem getFileSystem() {
        return null;
      }

      @Override
      public boolean isAbsolute() {
        return false;
      }

      @Override
      public Path getRoot() {
        return null;
      }

      @Override
      public Path getFileName() {
        return null;
      }

      @Override
      public Path getParent() {
        return null;
      }

      @Override
      public int getNameCount() {
        return 0;
      }

      @Override
      public Path getName(int index) {
        return null;
      }

      @Override
      public Path subpath(int beginIndex, int endIndex) {
        return null;
      }

      @Override
      public boolean startsWith(Path other) {
        return false;
      }

      @Override
      public boolean startsWith(String other) {
        return false;
      }

      @Override
      public boolean endsWith(Path other) {
        return false;
      }

      @Override
      public boolean endsWith(String other) {
        return false;
      }

      @Override
      public Path normalize() {
        return null;
      }

      @Override
      public Path resolve(Path other) {
        return null;
      }

      @Override
      public Path resolve(String other) {
        return null;
      }

      @Override
      public Path resolveSibling(Path other) {
        return null;
      }

      @Override
      public Path resolveSibling(String other) {
        return null;
      }

      @Override
      public Path relativize(Path other) {
        return null;
      }

      @Override
      public URI toUri() {
        return null;
      }

      @Override
      public Path toAbsolutePath() {
        return null;
      }

      @Override
      public Path toRealPath(LinkOption... options) throws IOException {
        return null;
      }

      @Override
      public File toFile() {
        return null;
      }

      @Override
      public WatchKey register(
          WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
          throws IOException {
        return null;
      }

      @Override
      public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events)
          throws IOException {
        return null;
      }

      @Override
      public Iterator<Path> iterator() {
        return null;
      }

      @Override
      public int compareTo(Path other) {
        return 0;
      }

      @Override
      public String toString() {
        return path;
      }
    };
  }
}
