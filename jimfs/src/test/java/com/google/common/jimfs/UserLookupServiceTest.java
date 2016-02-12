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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;

/**
 * Tests for {@link UserLookupService}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class UserLookupServiceTest {

  @Test
  public void testUserLookupService() throws IOException {
    UserPrincipalLookupService service = new UserLookupService(true);
    UserPrincipal bob1 = service.lookupPrincipalByName("bob");
    UserPrincipal bob2 = service.lookupPrincipalByName("bob");
    UserPrincipal alice = service.lookupPrincipalByName("alice");

    assertThat(bob1).isEqualTo(bob2);
    assertThat(bob1).isNotEqualTo(alice);

    GroupPrincipal group1 = service.lookupPrincipalByGroupName("group");
    GroupPrincipal group2 = service.lookupPrincipalByGroupName("group");
    GroupPrincipal foo = service.lookupPrincipalByGroupName("foo");

    assertThat(group1).isEqualTo(group2);
    assertThat(group1).isNotEqualTo(foo);
  }

  @Test
  public void testServiceNotSupportingGroups() throws IOException {
    UserPrincipalLookupService service = new UserLookupService(false);

    try {
      service.lookupPrincipalByGroupName("group");
      fail();
    } catch (UserPrincipalNotFoundException expected) {
      assertThat(expected.getName()).isEqualTo("group");
    }
  }
}
