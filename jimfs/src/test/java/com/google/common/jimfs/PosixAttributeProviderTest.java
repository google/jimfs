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

import static com.google.common.jimfs.UserLookupService.createGroupPrincipal;
import static com.google.common.jimfs.UserLookupService.createUserPrincipal;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Tests for {@link PosixAttributeProvider}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class PosixAttributeProviderTest
    extends AbstractAttributeProviderTest<PosixAttributeProvider> {

  @Override
  protected PosixAttributeProvider createProvider() {
    return new PosixAttributeProvider();
  }

  @Override
  protected Set<? extends AttributeProvider> createInheritedProviders() {
    return ImmutableSet.of(new BasicAttributeProvider(), new OwnerAttributeProvider());
  }

  @Test
  public void testInitialAttributes() {
    assertContainsAll(
        file,
        ImmutableMap.of(
            "group", createGroupPrincipal("group"),
            "permissions", PosixFilePermissions.fromString("rw-r--r--")));
  }

  @Test
  public void testSet() {
    assertSetAndGetSucceeds("group", createGroupPrincipal("foo"));
    assertSetAndGetSucceeds("permissions", PosixFilePermissions.fromString("rwxrwxrwx"));

    // invalid types
    assertSetFails("permissions", ImmutableList.of(PosixFilePermission.GROUP_EXECUTE));
    assertSetFails("permissions", ImmutableSet.of("foo"));
  }

  @Test
  public void testSetOnCreate() {
    assertSetAndGetSucceedsOnCreate("permissions", PosixFilePermissions.fromString("rwxrwxrwx"));
    assertSetFailsOnCreate("group", createGroupPrincipal("foo"));
  }

  @Test
  public void testView() throws IOException {
    file.setAttribute("owner", "owner", createUserPrincipal("user"));

    PosixFileAttributeView view =
        provider.view(
            fileLookup(),
            ImmutableMap.of(
                "basic", new BasicAttributeProvider().view(fileLookup(), NO_INHERITED_VIEWS),
                "owner", new OwnerAttributeProvider().view(fileLookup(), NO_INHERITED_VIEWS)));
    assertNotNull(view);

    assertThat(view.name()).isEqualTo("posix");
    assertThat(view.getOwner()).isEqualTo(createUserPrincipal("user"));

    PosixFileAttributes attrs = view.readAttributes();
    assertThat(attrs.fileKey()).isEqualTo(0);
    assertThat(attrs.owner()).isEqualTo(createUserPrincipal("user"));
    assertThat(attrs.group()).isEqualTo(createGroupPrincipal("group"));
    assertThat(attrs.permissions()).isEqualTo(PosixFilePermissions.fromString("rw-r--r--"));

    view.setOwner(createUserPrincipal("root"));
    assertThat(view.getOwner()).isEqualTo(createUserPrincipal("root"));
    assertThat(file.getAttribute("owner", "owner")).isEqualTo(createUserPrincipal("root"));

    view.setGroup(createGroupPrincipal("root"));
    assertThat(view.readAttributes().group()).isEqualTo(createGroupPrincipal("root"));
    assertThat(file.getAttribute("posix", "group")).isEqualTo(createGroupPrincipal("root"));

    view.setPermissions(PosixFilePermissions.fromString("rwx------"));
    assertThat(view.readAttributes().permissions())
        .isEqualTo(PosixFilePermissions.fromString("rwx------"));
    assertThat(file.getAttribute("posix", "permissions"))
        .isEqualTo(PosixFilePermissions.fromString("rwx------"));
  }

  @Test
  public void testAttributes() {
    PosixFileAttributes attrs = provider.readAttributes(file);
    assertThat(attrs.permissions()).isEqualTo(PosixFilePermissions.fromString("rw-r--r--"));
    assertThat(attrs.group()).isEqualTo(createGroupPrincipal("group"));
    assertThat(attrs.fileKey()).isEqualTo(0);
  }
}
