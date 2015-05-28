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

import static com.google.common.base.Functions.toStringFunction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @author Colin Decker
 */
public final class PathTester {

  private final PathService pathService;
  private final String string;
  private String root;
  private ImmutableList<String> names = ImmutableList.of();

  public PathTester(PathService pathService, String string) {
    this.pathService = pathService;
    this.string = string;
  }

  public PathTester root(String root) {
    this.root = root;
    return this;
  }

  public PathTester names(Iterable<String> names) {
    this.names = ImmutableList.copyOf(names);
    return this;
  }

  public PathTester names(String... names) {
    return names(Arrays.asList(names));
  }

  public void test(String first, String... more) {
    Path path = pathService.parsePath(first, more);
    test(path);
  }

  public void test(Path path) {
    assertEquals(string, path.toString());

    testRoot(path);
    testNames(path);
    testParents(path);
    testStartsWith(path);
    testEndsWith(path);
    testSubpaths(path);
  }

  private void testRoot(Path path) {
    if (root != null) {
      assertTrue(path + ".isAbsolute() should be true", path.isAbsolute());
      assertNotNull(path + ".getRoot() should not be null", path.getRoot());
      assertEquals(root, path.getRoot().toString());
    } else {
      assertFalse(path + ".isAbsolute() should be false", path.isAbsolute());
      assertNull(path + ".getRoot() should be null", path.getRoot());
    }
  }

  private void testNames(Path path) {
    assertEquals(names.size(), path.getNameCount());
    assertEquals(names, names(path));
    for (int i = 0; i < names.size(); i++) {
      assertEquals(names.get(i), path.getName(i).toString());
      // don't test individual names if this is an individual name
      if (names.size() > 1) {
        new PathTester(pathService, names.get(i))
            .names(names.get(i))
            .test(path.getName(i));
      }
    }
    if (names.size() > 0) {
      String fileName = names.get(names.size() - 1);
      assertEquals(fileName, path.getFileName().toString());
      // don't test individual names if this is an individual name
      if (names.size() > 1) {
        new PathTester(pathService, fileName)
            .names(fileName)
            .test(path.getFileName());
      }
    }
  }

  private void testParents(Path path) {
    Path parent = path.getParent();

    if (root != null && names.size() >= 1 || names.size() > 1) {
      assertNotNull(parent);
    }

    if (parent != null) {
      String parentName = names.size() == 1 ? root : string.substring(0, string.lastIndexOf('/'));
      new PathTester(pathService, parentName)
          .root(root)
          .names(names.subList(0, names.size() - 1))
          .test(parent);
    }
  }

  private void testSubpaths(Path path) {
    if (path.getRoot() == null) {
      assertEquals(path, path.subpath(0, path.getNameCount()));
    }

    if (path.getNameCount() > 1) {
      String stringWithoutRoot = root == null ? string : string.substring(root.length());

      // test start + 1 to end and start to end - 1 subpaths... this recursively tests all subpaths
      // actually tests most possible subpaths multiple times but... eh
      Path startSubpath = path.subpath(1, path.getNameCount());
      List<String> startNames =
          ImmutableList.copyOf(Splitter.on('/').split(stringWithoutRoot))
              .subList(1, path.getNameCount());

      new PathTester(pathService, Joiner.on('/').join(startNames))
          .names(startNames).test(startSubpath);

      Path endSubpath = path.subpath(0, path.getNameCount() - 1);
      List<String> endNames =
          ImmutableList.copyOf(Splitter.on('/').split(stringWithoutRoot))
              .subList(0, path.getNameCount() - 1);

      new PathTester(pathService, Joiner.on('/').join(endNames)).names(endNames).test(endSubpath);
    }
  }

  private void testStartsWith(Path path) {
    // empty path doesn't start with any path
    if (root != null || !names.isEmpty()) {
      Path other = path;
      while (other != null) {
        assertTrue(path + ".startsWith(" + other + ") should be true", path.startsWith(other));
        assertTrue(
            path + ".startsWith(" + other + ") should be true", path.startsWith(other.toString()));
        other = other.getParent();
      }
    }
  }

  private void testEndsWith(Path path) {
    // empty path doesn't start with any path
    if (root != null || !names.isEmpty()) {
      Path other = path;
      while (other != null) {
        assertTrue(path + ".endsWith(" + other + ") should be true", path.endsWith(other));
        assertTrue(
            path + ".endsWith(" + other + ") should be true", path.endsWith(other.toString()));
        if (other.getRoot() != null && other.getNameCount() > 0) {
          other = other.subpath(0, other.getNameCount());
        } else if (other.getNameCount() > 1) {
          other = other.subpath(1, other.getNameCount());
        } else {
          other = null;
        }
      }
    }
  }

  private static List<String> names(Path path) {
    return FluentIterable.from(path).transform(toStringFunction()).toList();
  }
}
