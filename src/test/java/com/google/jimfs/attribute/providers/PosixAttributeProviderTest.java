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

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Tests for {@link PosixAttributeProvider}.
 *
 * @author Colin Decker
 */
public class PosixAttributeProviderTest extends AttributeProviderTest<PosixAttributeProvider> {

  @Override
  protected PosixAttributeProvider createProvider() {
    BasicAttributeProvider basic = new BasicAttributeProvider();
    OwnerAttributeProvider owner = new OwnerAttributeProvider(createUserPrincipal("user"));
    return new PosixAttributeProvider(
        createGroupPrincipal("group"), PosixFilePermissions.fromString("rw-r--r--"), basic, owner);
  }

  @Test
  public void testInitialAttributes() {
    assertContainsAll(store,
        ImmutableMap.of(
            "group", createGroupPrincipal("group"),
            "permissions", PosixFilePermissions.fromString("rw-r--r--")));
  }

  @Test
  public void testSet() {
    assertSetAndGetSucceeds("group", createGroupPrincipal("foo"));
    assertSetAndGetSucceeds("permissions", PosixFilePermissions.fromString("rwxrwxrwx"));
    assertCanSetOnCreate("permissions");
    assertCannotSetOnCreate("group");
    assertSetFails("permissions", ImmutableList.of(PosixFilePermission.GROUP_EXECUTE));
    assertSetFails("permissions", ImmutableSet.of("foo"));
  }

  @Test
  public void testView() throws IOException {
    store.setAttribute("owner:owner", createUserPrincipal("user"));

    PosixFileAttributeView view = provider.getView(attributeStoreSupplier());
    assertNotNull(view);

    ASSERT.that(view.name()).is("posix");
    ASSERT.that(view.getOwner()).is(createUserPrincipal("user"));

    PosixFileAttributes attrs = view.readAttributes();
    ASSERT.that(attrs.fileKey()).is(0L);
    ASSERT.that(attrs.owner()).is(createUserPrincipal("user"));
    ASSERT.that(attrs.group()).is(createGroupPrincipal("group"));
    ASSERT.that(attrs.permissions()).is(PosixFilePermissions.fromString("rw-r--r--"));

    view.setOwner(createUserPrincipal("root"));
    ASSERT.that(view.getOwner()).is(createUserPrincipal("root"));
    ASSERT.that(store.getAttribute("owner:owner")).is(createUserPrincipal("root"));

    view.setGroup(createGroupPrincipal("root"));
    ASSERT.that(view.readAttributes().group()).is(createGroupPrincipal("root"));
    ASSERT.that(store.getAttribute("posix:group")).is(createGroupPrincipal("root"));

    view.setPermissions(PosixFilePermissions.fromString("rwx------"));
    ASSERT.that(view.readAttributes().permissions())
        .is(PosixFilePermissions.fromString("rwx------"));
    ASSERT.that(store.getAttribute("posix:permissions"))
        .is(PosixFilePermissions.fromString("rwx------"));
  }

  @Test
  public void testAttributes() {
    PosixFileAttributes attrs = provider.read(store);
    ASSERT.that(attrs.permissions()).is(PosixFilePermissions.fromString("rw-r--r--"));
    ASSERT.that(attrs.group()).is(createGroupPrincipal("group"));
    ASSERT.that(attrs.fileKey()).is(0L);
  }
}
