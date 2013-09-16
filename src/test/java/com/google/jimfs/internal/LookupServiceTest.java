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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.jimfs.path.CaseSensitivity;
import com.google.jimfs.path.PathType;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

/**
 * Tests for {@link LookupService}.
 *
 * @author Colin Decker
 */
public class LookupServiceTest {

  /*
   * Directory structure. Each file should have a unique name.
   *
   * /
   *   work/
   *     one/
   *       two/
   *         three/
   *       eleven
   *     four/
   *       five -> /foo
   *       six -> ../one
   *       loop -> ../four/loop
   *   foo/
   *     bar/
   * $
   *   a/
   *     b/
   *       c/
   */

  /**
   * This path service is for unix-like paths, with the exception that it recognizes $ as a root in
   * addition to /, allowing for two roots.
   */
  private final PathService pathService = new TestPathService(
      new PathType(CaseSensitivity.CASE_SENSITIVE, '/') {
        @Override
        public ParseResult parsePath(String path) {
          String root = null;
          if (path.startsWith("/") || path.startsWith("$")) {
            root = path.substring(0, 1);
            path = path.substring(1);
          }
          return new ParseResult(root, Splitter.on('/').omitEmptyStrings().split(path));
        }

        @Override
        public String toString(@Nullable String root, Iterable<String> names) {
          root = Strings.nullToEmpty(root);
          return root + Joiner.on('/').join(names);
        }

        @Override
        public String toUriPath(String root, Iterable<String> names) {
          // need to add extra / to differentiate between paths "/$foo/bar" and "$foo/bar".
          return "/" + toString(root, names);
        }

        @Override
        public ParseResult parseUriPath(String uriPath) {
          checkArgument(uriPath.startsWith("//") || uriPath.startsWith("/$"),
              "uriPath (%s) must start with // or /$");
          return parsePath(uriPath.substring(1)); // skip leading /
        }
      });

  private LookupService lookupService;
  private File workingDirectory;
  private final Map<String, File> files = new HashMap<>();

  @Before
  public void setUp() {
    DirectoryTable superRootTable = new DirectoryTable();
    File superRoot = new File(-1, superRootTable);

    files.put("SUPER_ROOT", superRoot);

    lookupService = new LookupService(superRoot);

    DirectoryTable rootTable = new DirectoryTable();
    File root = new File(0, rootTable);
    rootTable.linkSelf(root);
    rootTable.linkParent(root);
    superRootTable.link(Name.simple("/"), root);

    files.put("/", root);

    DirectoryTable otherRootTable = new DirectoryTable();
    File otherRoot = new File(2, otherRootTable);
    otherRootTable.linkSelf(otherRoot);
    otherRootTable.linkParent(otherRoot);
    superRootTable.link(Name.simple("$"), otherRoot);

    files.put("$", otherRoot);

    workingDirectory = createDirectory("/", "work");

    createDirectory("work", "one");
    createDirectory("one", "two");
    createFile("one", "eleven");
    createDirectory("two", "three");
    createDirectory("work", "four");
    createSymbolicLink("four", "five", "/foo");
    createSymbolicLink("four", "six", "../one");
    createSymbolicLink("four", "loop", "../four/loop");
    createDirectory("/", "foo");
    createDirectory("foo", "bar");
    createDirectory("$", "a");
    createDirectory("a", "b");
    createDirectory("b", "c");
  }

  // absolute lookups

  @Test
  public void testLookup_root() throws IOException {
    assertFound(lookup("/"), "SUPER_ROOT", "/");
    assertFound(lookup("$"), "SUPER_ROOT", "$");
  }

  @Test
  public void testLookup_absolute() throws IOException {
    assertFound(lookup("/work"), "/", "work");
    assertFound(lookup("/work/one/two/three"), "two", "three");
    assertFound(lookup("$a"), "$", "a");
    assertFound(lookup("$a/b/c"), "b", "c");
  }

  @Test
  public void testLookup_absolute_notFound() throws IOException {
    assertNotFound(lookup("/a/b"));
    assertNotFound(lookup("/work/one/foo/bar"));
    assertNotFound(lookup("$c/d"));
    assertNotFound(lookup("$a/b/c/d/e"));
  }

  @Test
  public void testLookup_absolute_parentFound() throws IOException {
    assertParentFound(lookup("/a"), "/");
    assertParentFound(lookup("/foo/baz"), "foo");
    assertParentFound(lookup("$c"), "$");
    assertParentFound(lookup("$a/b/c/d"), "c");
  }

  @Test
  public void testLookup_absolute_nonDirectoryIntermediateFile() throws IOException {
    assertNotFound(lookup("/work/one/eleven/twelve"));
    assertNotFound(lookup("/work/one/eleven/twelve/thirteen/fourteen"));
  }

  @Test
  public void testLookup_absolute_intermediateSymlink() throws IOException {
    assertFound(lookup("/work/four/five/bar"), "foo", "bar");
    assertFound(lookup("/work/four/six/two/three"), "two", "three");

    // NOFOLLOW_LINKS doesn't affect intermediate symlinks
    assertFound(lookup("/work/four/five/bar", NOFOLLOW_LINKS), "foo", "bar");
    assertFound(lookup("/work/four/six/two/three", NOFOLLOW_LINKS), "two", "three");
  }

  @Test
  public void testLookup_absolute_intermediateSymlink_parentFound() throws IOException {
    assertParentFound(lookup("/work/four/five/baz"), "foo");
    assertParentFound(lookup("/work/four/six/baz"), "one");
  }

  @Test
  public void testLookup_absolute_finalSymlink() throws IOException {
    assertFound(lookup("/work/four/five"), "/", "foo");
    assertFound(lookup("/work/four/six"), "work", "one");
  }

  @Test
  public void testLookup_absolute_finalSymlink_nofollowLinks() throws IOException {
    assertFound(lookup("/work/four/five", NOFOLLOW_LINKS), "four", "five");
    assertFound(lookup("/work/four/six", NOFOLLOW_LINKS), "four", "six");
    assertFound(lookup("/work/four/loop", NOFOLLOW_LINKS), "four", "loop");
  }

  @Test
  public void testLookup_absolute_symlinkLoop() {
    try {
      lookup("/work/four/loop");
      fail();
    } catch (IOException expected) {
    }

    try {
      lookup("/work/four/loop/whatever");
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testLookup_absolute_withDotsInPath() throws IOException {
    assertFound(lookup("/."), "SUPER_ROOT", "/");
    assertFound(lookup("/./././."), "SUPER_ROOT", "/");
    assertFound(lookup("/work/./one/./././two/three"), "two", "three");
    assertFound(lookup("/work/./one/./././two/././three"), "two", "three");
    assertFound(lookup("/work/./one/./././two/three/././."), "two", "three");
  }

  @Test
  public void testLookup_absolute_withDotDotsInPath() throws IOException {
    assertFound(lookup("/.."), "SUPER_ROOT", "/");
    assertFound(lookup("/../../.."), "SUPER_ROOT", "/");
    assertFound(lookup("/work/.."), "SUPER_ROOT", "/");
    assertFound(lookup("/work/../work/one/two/../two/three"), "two", "three");
    assertFound(lookup("/work/one/two/../../four/../one/two/three/../three"), "two", "three");
    assertFound(lookup("/work/one/two/three/../../two/three/.."), "one", "two");
    assertFound(lookup("/work/one/two/three/../../two/three/../.."), "work", "one");
  }

  @Test
  public void testLookup_absolute_withDotDotsInPath_afterSymlink() throws IOException {
    assertFound(lookup("/work/four/five/.."), "SUPER_ROOT", "/");
    assertFound(lookup("/work/four/six/.."), "/", "work");
  }

  // relative lookups

  @Test
  public void testLookup_relative() throws IOException {
    assertFound(lookup("one"), "work", "one");
    assertFound(lookup("one/two/three"), "two", "three");
  }

  @Test
  public void testLookup_relative_notFound() throws IOException {
    assertNotFound(lookup("/a/b"));
    assertNotFound(lookup("/work/one/foo/bar"));
  }

  @Test
  public void testLookup_relative_parentFound() throws IOException {
    assertParentFound(lookup("a"), "work");
    assertParentFound(lookup("one/two/four"), "two");
  }

  @Test
  public void testLookup_relative_nonDirectoryIntermediateFile() throws IOException {
    assertNotFound(lookup("one/eleven/twelve"));
    assertNotFound(lookup("one/eleven/twelve/thirteen/fourteen"));
  }

  @Test
  public void testLookup_relative_intermediateSymlink() throws IOException {
    assertFound(lookup("four/five/bar"), "foo", "bar");
    assertFound(lookup("four/six/two/three"), "two", "three");

    // NOFOLLOW_LINKS doesn't affect intermediate symlinks
    assertFound(lookup("four/five/bar", NOFOLLOW_LINKS), "foo", "bar");
    assertFound(lookup("four/six/two/three", NOFOLLOW_LINKS), "two", "three");
  }

  @Test
  public void testLookup_relative_intermediateSymlink_parentFound() throws IOException {
    assertParentFound(lookup("four/five/baz"), "foo");
    assertParentFound(lookup("four/six/baz"), "one");
  }

  @Test
  public void testLookup_relative_finalSymlink() throws IOException {
    assertFound(lookup("four/five"), "/", "foo");
    assertFound(lookup("four/six"), "work", "one");
  }

  @Test
  public void testLookup_relative_finalSymlink_nofollowLinks() throws IOException {
    assertFound(lookup("four/five", NOFOLLOW_LINKS), "four", "five");
    assertFound(lookup("four/six", NOFOLLOW_LINKS), "four", "six");
    assertFound(lookup("four/loop", NOFOLLOW_LINKS), "four", "loop");
  }

  @Test
  public void testLookup_relative_symlinkLoop() {
    try {
      lookup("four/loop");
      fail();
    } catch (IOException expected) {
    }

    try {
      lookup("four/loop/whatever");
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testLookup_relative_emptyPath() throws IOException {
    assertFound(lookup(""), "/", "work");
  }

  @Test
  public void testLookup_relative_withDotsInPath() throws IOException {
    assertFound(lookup("."), "/", "work");
    assertFound(lookup("././."), "/", "work");
    assertFound(lookup("./one/./././two/three"), "two", "three");
    assertFound(lookup("./one/./././two/././three"), "two", "three");
    assertFound(lookup("./one/./././two/three/././."), "two", "three");
  }

  @Test
  public void testLookup_relative_withDotDotsInPath() throws IOException {
    assertFound(lookup(".."), "SUPER_ROOT", "/");
    assertFound(lookup("../../.."), "SUPER_ROOT", "/");
    assertFound(lookup("../work"), "/", "work");
    assertFound(lookup("../../work"), "/", "work");
    assertFound(lookup("../foo"), "/", "foo");
    assertFound(lookup("../work/one/two/../two/three"), "two", "three");
    assertFound(lookup("one/two/../../four/../one/two/three/../three"), "two", "three");
    assertFound(lookup("one/two/three/../../two/three/.."), "one", "two");
    assertFound(lookup("one/two/three/../../two/three/../.."), "work", "one");
  }

  @Test
  public void testLookup_relative_withDotDotsInPath_afterSymlink() throws IOException {
    assertFound(lookup("four/five/.."), "SUPER_ROOT", "/");
    assertFound(lookup("four/six/.."), "/", "work");
  }

  private LookupResult lookup(String path, LinkOption... options) throws IOException {
    JimfsPath pathObj = pathService.parsePath(path);
    return lookupService.lookup(workingDirectory, pathObj, LinkHandling.fromOptions(options));
  }

  private void assertFound(LookupResult result, String parent, String file) {
    ASSERT.that(result.found()).isTrue();
    ASSERT.that(result.parentFound()).isTrue();
    ASSERT.that(result.name()).is(Name.simple(file));
    ASSERT.that(result.parent()).is(files.get(parent));
    ASSERT.that(result.file()).is(files.get(file));
  }

  private void assertParentFound(LookupResult result, String parent) {
    ASSERT.that(result.found()).isFalse();
    ASSERT.that(result.parentFound()).isTrue();
    ASSERT.that(result.parent()).is(files.get(parent));

    try {
      result.name();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      result.file();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  private void assertNotFound(LookupResult result) {
    ASSERT.that(result.found()).isFalse();
    ASSERT.that(result.parentFound()).isFalse();

    try {
      result.name();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      result.file();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      result.parent();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  private File createDirectory(String parent, String name) {
    File dir = files.get(parent);

    DirectoryTable table = new DirectoryTable();
    File newFile = new File(new Random().nextLong(), table);

    DirectoryTable parentTable = dir.content();
    parentTable.link(Name.simple(name), newFile);
    table.linkSelf(newFile);
    table.linkParent(dir);

    files.put(name, newFile);

    return newFile;
  }

  private File createFile(String parent, String name) {
    File dir = files.get(parent);

    File newFile = new File(new Random().nextLong(), new StubByteStore(0));

    DirectoryTable parentTable = dir.content();
    parentTable.link(Name.simple(name), newFile);

    files.put(name, newFile);

    return newFile;
  }

  private File createSymbolicLink(String parent, String name, String target) {
    File dir = files.get(parent);

    File newFile = new File(new Random().nextLong(), pathService.parsePath(target));

    DirectoryTable parentTable = dir.content();
    parentTable.link(Name.simple(name), newFile);

    files.put(name, newFile);

    return newFile;
  }
}
