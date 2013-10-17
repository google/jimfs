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

import static com.google.jimfs.attribute.UserPrincipals.createGroupPrincipal;
import static com.google.jimfs.attribute.UserPrincipals.createUserPrincipal;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Tests for {@link UnixAttributeProvider}.
 *
 * @author Colin Decker
 */
@SuppressWarnings("OctalInteger")
public class UnixAttributeProviderTest extends AttributeProviderTest<UnixAttributeProvider> {

  @Override
  protected UnixAttributeProvider createProvider() {
    return new UnixAttributeProvider();
  }

  @Override
  protected Set<? extends AttributeProvider<?>> createInheritedProviders() {
    return ImmutableSet.of(
        new BasicAttributeProvider(),
        new OwnerAttributeProvider(),
        new PosixAttributeProvider());
  }

  @Test
  public void testInitialAttributes() {
    // unix provider relies on other providers to set their initial attributes
    inode.setAttribute("owner:owner", createUserPrincipal("foo"));
    inode.setAttribute("posix:group", createGroupPrincipal("bar"));
    inode.setAttribute("posix:permissions",
        ImmutableSet.copyOf(PosixFilePermissions.fromString("rw-r--r--")));

    // these are pretty much meaningless here since they aren't properties this
    // file system actually has, so don't really care about the exact value of these
    ASSERT.that(provider.get(inode, "uid")).isA(Integer.class);
    ASSERT.that(provider.get(inode, "gid")).isA(Integer.class);
    ASSERT.that(provider.get(inode, "rdev")).is(0L);
    ASSERT.that(provider.get(inode, "dev")).is(1L);
    // TODO(cgdecker): File objects are kind of like inodes; should their IDs be inode IDs here?
    // even though we're already using that ID as the fileKey, which unix doesn't do
    ASSERT.that(provider.get(inode, "ino")).isA(Integer.class);

    // these have logical origins in attributes from other views
    ASSERT.that(provider.get(inode, "mode")).is(0644); // rw-r--r--
    ASSERT.that(provider.get(inode, "ctime"))
        .isEqualTo(FileTime.fromMillis(inode.getCreationTime()));

    // this is based on a property this file system does actually have
    ASSERT.that(provider.get(inode, "nlink")).is(0);
    inode.incrementLinkCount();
    inode.incrementLinkCount();
    ASSERT.that(provider.get(inode, "nlink")).is(2);
    inode.decrementLinkCount();
    ASSERT.that(provider.get(inode, "nlink")).is(1);
  }

  @Test
  public void testSet() {
    assertSetFails("unix:uid", 1);
    assertSetFails("unix:gid", 1);
    assertSetFails("unix:rdev", 1L);
    assertSetFails("unix:dev", 1L);
    assertSetFails("unix:ino", 1);
    assertSetFails("unix:mode", 1);
    assertSetFails("unix:ctime", 1L);
    assertSetFails("unix:nlink", 1);
  }
}
