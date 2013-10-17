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

package com.google.jimfs.attribute;

import static org.truth0.Truth.ASSERT;

import org.junit.Test;

/**
 * Tests for {@link Inode}.
 *
 * @author Colin Decker
 */
public class InodeTest {

  @Test
  public void testAttributes() {
    // these methods are basically just thin wrappers around a map, so no need to test too
    // thoroughly

    Inode inode = new FakeInode(0, false, true, false, 0);

    ASSERT.that(inode.getAttributeKeys()).isEmpty();
    ASSERT.that(inode.getAttribute("foo:foo")).isNull();

    inode.deleteAttribute("foo:foo"); // doesn't throw

    inode.setAttribute("foo:foo", "foo");

    ASSERT.that(inode.getAttributeKeys()).iteratesAs("foo:foo");
    ASSERT.that(inode.getAttribute("foo:foo")).is("foo");

    inode.deleteAttribute("foo:foo");

    ASSERT.that(inode.getAttributeKeys()).isEmpty();
    ASSERT.that(inode.getAttribute("foo:foo")).isNull();
  }
}
