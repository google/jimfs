package com.google.common.io.jimfs.attribute;

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
