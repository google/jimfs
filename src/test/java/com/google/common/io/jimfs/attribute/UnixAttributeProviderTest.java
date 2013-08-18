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

package com.google.common.io.jimfs.attribute;

import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.NORMAL;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.junit.Test;

import java.nio.file.attribute.FileTime;

/**
 * Tests for {@link UnixAttributeProvider}.
 *
 * @author Colin Decker
 */
@SuppressWarnings("OctalInteger")
public class UnixAttributeProviderTest extends AttributeProviderTest {

  @Override
  protected Iterable<? extends AttributeProvider> createProviders() {
    Iterable<? extends AttributeProvider> posixProviders = new PosixAttributeProviderTest()
        .createProviders();
    UnixAttributeProvider unix = new UnixAttributeProvider(
        Iterables.getOnlyElement(Iterables.filter(posixProviders, PosixAttributeProvider.class)));
    return Iterables.concat(posixProviders, ImmutableList.of(unix));
  }

  @Test
  public void testInitialAttributes() {
    // these are pretty much meaningless here since they aren't properties this
    // file system actually has, so don't really care about the exact value of these
    ASSERT.that(service.getAttribute(file, "unix:uid")).isA(Integer.class);
    ASSERT.that(service.getAttribute(file, "unix:gid")).isA(Integer.class);
    ASSERT.that(service.getAttribute(file, "unix:rdev")).is(0L);
    ASSERT.that(service.getAttribute(file, "unix:dev")).is(1L);
    // TODO(cgdecker): File objects are kind of like inodes; should their IDs be inode IDs here?
    // even though we're already using that ID as the fileKey, which unix doesn't do
    ASSERT.that(service.getAttribute(file, "unix:ino")).isA(Integer.class);

    // these have logical origins in attributes from other views
    ASSERT.that(service.getAttribute(file, "unix:mode")).is(0644); // rw-r--r--
    ASSERT.that(service.getAttribute(file, "unix:ctime"))
        .isEqualTo(service.getAttribute(file, "basic:creationTime"));

    // this is based on a property this file system does actually have
    ASSERT.that(service.getAttribute(file, "unix:nlink")).is(0);
    file.linked();
    file.linked();
    ASSERT.that(service.getAttribute(file, "unix:nlink")).is(2);
    file.unlinked();
    ASSERT.that(service.getAttribute(file, "unix:nlink")).is(1);
  }

  @Test
  public void testSet() {
    assertSetFails("unix:uid", 1, NORMAL);
    assertSetFails("unix:gid", 1, NORMAL);
    assertSetFails("unix:rdev", 1L, NORMAL);
    assertSetFails("unix:dev", 2L, NORMAL);
    assertSetFails("unix:ino", 3, NORMAL);
    assertSetFails("unix:mode", 0777, NORMAL);
    assertSetFails("unix:ctime", FileTime.fromMillis(0L), NORMAL);
    assertSetFails("unix:nlink", 2, NORMAL);
  }
}
