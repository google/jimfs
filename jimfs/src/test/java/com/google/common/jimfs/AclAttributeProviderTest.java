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

import static com.google.common.jimfs.UserLookupService.createUserPrincipal;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT;
import static java.nio.file.attribute.AclEntryPermission.APPEND_DATA;
import static java.nio.file.attribute.AclEntryPermission.DELETE;
import static java.nio.file.attribute.AclEntryType.ALLOW;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link AclAttributeProvider}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class AclAttributeProviderTest extends AbstractAttributeProviderTest<AclAttributeProvider> {

  private static final UserPrincipal USER = createUserPrincipal("user");
  private static final UserPrincipal FOO = createUserPrincipal("foo");

  private static final ImmutableList<AclEntry> defaultAcl =
      new ImmutableList.Builder<AclEntry>()
          .add(
              AclEntry.newBuilder()
                  .setType(ALLOW)
                  .setFlags(DIRECTORY_INHERIT)
                  .setPermissions(DELETE, APPEND_DATA)
                  .setPrincipal(USER)
                  .build())
          .add(
              AclEntry.newBuilder()
                  .setType(ALLOW)
                  .setFlags(DIRECTORY_INHERIT)
                  .setPermissions(DELETE, APPEND_DATA)
                  .setPrincipal(FOO)
                  .build())
          .build();

  @Override
  protected AclAttributeProvider createProvider() {
    return new AclAttributeProvider();
  }

  @Override
  protected Set<? extends AttributeProvider> createInheritedProviders() {
    return ImmutableSet.of(new BasicAttributeProvider(), new OwnerAttributeProvider());
  }

  @Override
  protected Map<String, ?> createDefaultValues() {
    return ImmutableMap.of("acl:acl", defaultAcl);
  }

  @Test
  public void testInitialAttributes() {
    assertThat(provider.get(file, "acl")).isEqualTo(defaultAcl);
  }

  @Test
  public void testSet() {
    assertSetAndGetSucceeds("acl", ImmutableList.of());
    assertSetFailsOnCreate("acl", ImmutableList.of());
    assertSetFails("acl", ImmutableSet.of());
    assertSetFails("acl", ImmutableList.of("hello"));
  }

  @Test
  public void testView() throws IOException {
    AclFileAttributeView view =
        provider.view(
            fileLookup(),
            ImmutableMap.<String, FileAttributeView>of(
                "owner", new OwnerAttributeProvider().view(fileLookup(), NO_INHERITED_VIEWS)));
    assertNotNull(view);

    assertThat(view.name()).isEqualTo("acl");

    assertThat(view.getAcl()).isEqualTo(defaultAcl);

    view.setAcl(ImmutableList.<AclEntry>of());
    view.setOwner(FOO);

    assertThat(view.getAcl()).isEqualTo(ImmutableList.<AclEntry>of());
    assertThat(view.getOwner()).isEqualTo(FOO);

    assertThat(file.getAttribute("acl", "acl")).isEqualTo(ImmutableList.<AclEntry>of());
  }
}
