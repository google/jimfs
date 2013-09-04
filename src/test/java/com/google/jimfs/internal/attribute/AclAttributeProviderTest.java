package com.google.jimfs.internal.attribute;

import static com.google.jimfs.internal.attribute.UserLookupService.createUserPrincipal;
import static java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT;
import static java.nio.file.attribute.AclEntryPermission.APPEND_DATA;
import static java.nio.file.attribute.AclEntryPermission.DELETE;
import static java.nio.file.attribute.AclEntryType.ALLOW;
import static junit.framework.Assert.assertNotNull;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;

/**
 * Tests for {@link AclAttributeProvider}.
 *
 * @author Colin Decker
 */
public class AclAttributeProviderTest extends AttributeProviderTest {

  private static final UserPrincipal USER = createUserPrincipal("user");
  private static final UserPrincipal FOO = createUserPrincipal("foo");

  private ImmutableList<AclEntry> defaultAcl;

  @Override
  protected Iterable<? extends AttributeProvider> createProviders() {
    AclEntry entry1 = AclEntry.newBuilder()
        .setType(ALLOW)
        .setFlags(DIRECTORY_INHERIT)
        .setPermissions(DELETE, APPEND_DATA)
        .setPrincipal(USER)
        .build();
    AclEntry entry2 = AclEntry.newBuilder(entry1)
        .setPrincipal(FOO)
        .build();

    defaultAcl = ImmutableList.of(entry1, entry2);

    OwnerAttributeProvider owner = new OwnerAttributeProvider(USER);
    AclAttributeProvider acl = new AclAttributeProvider(owner, defaultAcl);
    return ImmutableList.of(owner, acl);
  }

  @Test
  public void testInitialAttributes() {
    ASSERT.that(service.getAttribute(file, "acl", "acl")).is(defaultAcl);
    ASSERT.that(service.getAttribute(file, "acl:owner")).is(USER);
  }

  @Test
  public void testSet() {
    assertSetAndGetSucceeds("acl:acl", ImmutableList.of());
    assertSetOnCreateFails("acl:acl", defaultAcl);
    assertSetFails("acl:acl", ImmutableSet.of());
    assertSetFails("acl:acl", ImmutableList.of("hello"));
  }

  @Test
  public void testView() throws IOException {
    AclFileAttributeView view =
        service.getFileAttributeView(fileProvider(), AclFileAttributeView.class);
    assertNotNull(view);

    ASSERT.that(view.name()).is("acl");

    ASSERT.that(view.getAcl()).is(defaultAcl);
    ASSERT.that(view.getOwner()).is(USER);

    view.setAcl(ImmutableList.<AclEntry>of());
    view.setOwner(FOO);

    ASSERT.that(view.getAcl()).is(ImmutableList.<AclEntry>of());
    ASSERT.that(view.getOwner()).is(FOO);

    ASSERT.that(file.getAttribute("acl:acl")).is(ImmutableList.<AclEntry>of());
  }
}
