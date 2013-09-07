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
import static junit.framework.Assert.assertNotNull;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Tests for {@link PosixAttributeProvider}.
 *
 * @author Colin Decker
 */
public class PosixAttributeProviderTest extends AttributeProviderTest {

  @Override
  protected Iterable<? extends AttributeProvider> createProviders() {
    BasicAttributeProvider basic = new BasicAttributeProvider();
    OwnerAttributeProvider owner = new OwnerAttributeProvider(createUserPrincipal("user"));
    PosixAttributeProvider posix = new PosixAttributeProvider(
        createGroupPrincipal("group"), PosixFilePermissions.fromString("rw-r--r--"), basic, owner);
    return ImmutableList.of(basic, owner, posix);
  }

  @Test
  public void testInitialAttributes() {
    assertContainsAll(file,
        ImmutableMap.of(
            "posix:group", createGroupPrincipal("group"),
            "posix:permissions", PosixFilePermissions.fromString("rw-r--r--"),
            "posix:owner", createUserPrincipal("user"),
            "posix:fileKey", 0L));
  }

  @Test
  public void testSet() {
    assertSetAndGetSucceeds("posix:group", createGroupPrincipal("foo"));
    assertSetAndGetSucceeds("posix:permissions", PosixFilePermissions.fromString("rwxrwxrwx"));
    assertSetOnCreateSucceeds("posix:permissions", PosixFilePermissions.fromString("rwxrwxrwx"));
    assertSetOnCreateSucceeds("posix:permissions", ImmutableSet.of());
    assertSetOnCreateFails("posix:group", createGroupPrincipal("foo"));
    assertSetFails("posix:permissions", ImmutableList.of(PosixFilePermission.GROUP_EXECUTE));
    assertSetFails("posix:permissions", ImmutableSet.of("foo"));
  }

  @Test
  public void testView() throws IOException {
    PosixFileAttributeView view = service.getFileAttributeView(
        fileSupplier(), PosixFileAttributeView.class);
    assertNotNull(view);

    ASSERT.that(view.name()).is("posix");
    ASSERT.that(view.getOwner()).is(createUserPrincipal("user"));

    PosixFileAttributes attrs = view.readAttributes();
    ASSERT.that(attrs.fileKey()).is(0L);
    ASSERT.that(attrs.owner()).is(createUserPrincipal("user"));
    ASSERT.that(attrs.group()).is(createGroupPrincipal("group"));
    ASSERT.that(attrs.permissions()).is(PosixFilePermissions.fromString("rw-r--r--"));

    FileTime time = FileTime.fromMillis(0L);
    view.setTimes(time, time, time);
    assertContainsAll(file, ImmutableMap.<String, Object>of(
        "posix:creationTime", time, "posix:lastAccessTime", time, "posix:lastModifiedTime", time));

    view.setOwner(createUserPrincipal("root"));
    ASSERT.that(view.getOwner()).is(createUserPrincipal("root"));
    ASSERT.that(file.getAttribute("owner:owner")).is(createUserPrincipal("root"));

    view.setGroup(createGroupPrincipal("root"));
    ASSERT.that(view.readAttributes().group()).is(createGroupPrincipal("root"));
    ASSERT.that(file.getAttribute("posix:group")).is(createGroupPrincipal("root"));

    view.setPermissions(PosixFilePermissions.fromString("rwx------"));
    ASSERT.that(view.readAttributes().permissions())
        .is(PosixFilePermissions.fromString("rwx------"));
    ASSERT.that(file.getAttribute("posix:permissions"))
        .is(PosixFilePermissions.fromString("rwx------"));
  }

  @Test
  public void testAttributes() {
    PosixFileAttributes attrs = service.readAttributes(file, PosixFileAttributes.class);
    ASSERT.that(attrs.permissions()).is(PosixFilePermissions.fromString("rw-r--r--"));
    ASSERT.that(attrs.group()).is(createGroupPrincipal("group"));
    ASSERT.that(attrs.fileKey()).is(0L);
  }
}
