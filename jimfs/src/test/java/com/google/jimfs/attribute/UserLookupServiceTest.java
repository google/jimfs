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

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;

/**
 * @author Colin Decker
 */
public class UserLookupServiceTest {

  @Test
  public void testUserLookupService() throws IOException {
    UserLookupService service = new UserLookupService(true);
    UserPrincipal bob1 = service.lookupPrincipalByName("bob");
    UserPrincipal bob2 = service.lookupPrincipalByName("bob");
    UserPrincipal alice = service.lookupPrincipalByName("alice");

    ASSERT.that(bob1).isEqualTo(bob2);
    ASSERT.that(bob1).isNotEqualTo(alice);

    GroupPrincipal group1 = service.lookupPrincipalByGroupName("group");
    GroupPrincipal group2 = service.lookupPrincipalByGroupName("group");
    GroupPrincipal foo = service.lookupPrincipalByGroupName("foo");

    ASSERT.that(group1).isEqualTo(group2);
    ASSERT.that(group1).isNotEqualTo(foo);
  }

  @Test
  public void testServiceNotSupportingGroups() throws IOException {
    UserLookupService service = new UserLookupService(false);

    try {
      service.lookupPrincipalByGroupName("group");
      fail();
    } catch (UserPrincipalNotFoundException expected) {
      ASSERT.that(expected.getName()).is("group");
    }
  }
}
