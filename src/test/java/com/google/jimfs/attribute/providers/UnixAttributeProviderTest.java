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

package com.google.jimfs.attribute.providers;

import static com.google.jimfs.attribute.UserLookupService.createGroupPrincipal;
import static com.google.jimfs.attribute.UserLookupService.createUserPrincipal;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Tests for {@link UnixAttributeProvider}.
 *
 * @author Colin Decker
 */
@SuppressWarnings("OctalInteger")
public class UnixAttributeProviderTest extends AttributeProviderTest<UnixAttributeProvider> {

  @Override
  protected UnixAttributeProvider createProvider() {
    PosixAttributeProvider posixProvider = new PosixAttributeProviderTest().createProvider();
    return new UnixAttributeProvider(posixProvider);
  }

  @Test
  public void testInitialAttributes() {
    // unix provider relies on other providers to set their initial attributes
    BasicAttributeProvider.INSTANCE.setInitial(store);

    OwnerAttributeProvider owner = new OwnerAttributeProvider(createUserPrincipal("foo"));
    owner.setInitial(store);

    PosixAttributeProvider posix = new PosixAttributeProvider(createGroupPrincipal("bar"),
        PosixFilePermissions.fromString("rw-r--r--"), owner);
    posix.setInitial(store);

    // these are pretty much meaningless here since they aren't properties this
    // file system actually has, so don't really care about the exact value of these
    ASSERT.that(provider.get(store, "uid")).isA(Integer.class);
    ASSERT.that(provider.get(store, "gid")).isA(Integer.class);
    ASSERT.that(provider.get(store, "rdev")).is(0L);
    ASSERT.that(provider.get(store, "dev")).is(1L);
    // TODO(cgdecker): File objects are kind of like inodes; should their IDs be inode IDs here?
    // even though we're already using that ID as the fileKey, which unix doesn't do
    ASSERT.that(provider.get(store, "ino")).isA(Integer.class);

    // these have logical origins in attributes from other views
    ASSERT.that(provider.get(store, "mode")).is(0644); // rw-r--r--
    ASSERT.that(provider.get(store, "ctime"))
        .isEqualTo(FileTime.fromMillis(store.getCreationTime()));

    // this is based on a property this file system does actually have
    ASSERT.that(provider.get(store, "nlink")).is(0);
    store.setLinks(2);
    ASSERT.that(provider.get(store, "nlink")).is(2);
    store.setLinks(1);
    ASSERT.that(provider.get(store, "nlink")).is(1);
  }

  @Test
  public void testSet() {
    assertCannotSet("unix:uid");
    assertCannotSet("unix:gid");
    assertCannotSet("unix:rdev");
    assertCannotSet("unix:dev");
    assertCannotSet("unix:ino");
    assertCannotSet("unix:mode");
    assertCannotSet("unix:ctime");
    assertCannotSet("unix:nlink");
  }
}
