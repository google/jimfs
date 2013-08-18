package com.google.common.io.jimfs.attribute;

import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.CREATE;
import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.NORMAL;
import static com.google.common.io.jimfs.attribute.UserLookupService.createGroupPrincipal;
import static com.google.common.io.jimfs.attribute.UserLookupService.createUserPrincipal;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
    assertSetAndGetSucceeds("posix:group", createGroupPrincipal("foo"), NORMAL);
    assertSetAndGetSucceeds(
        "posix:permissions", PosixFilePermissions.fromString("rwxrwxrwx"), NORMAL);
    assertSetAndGetSucceeds(
        "posix:permissions", PosixFilePermissions.fromString("rwxrwxrwx"), CREATE);
    assertSetAndGetSucceeds("posix:permissions", ImmutableSet.of(), NORMAL);
    assertSetFails("posix:group", createGroupPrincipal("foo"), CREATE);
    assertSetFails(
        "posix:permissions", ImmutableList.of(PosixFilePermission.GROUP_EXECUTE), NORMAL);
    assertSetFails("posix:permissions", ImmutableSet.of("foo"), NORMAL);
  }

  @Test
  public void testView() throws IOException {
    PosixFileAttributeView view = service.getFileAttributeView(
        fileProvider(), PosixFileAttributeView.class);
    ASSERT.that(view).isNotNull();

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
