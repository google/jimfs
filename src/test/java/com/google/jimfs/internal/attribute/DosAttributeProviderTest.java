package com.google.jimfs.internal.attribute;

import static com.google.jimfs.internal.attribute.UserLookupService.createUserPrincipal;
import static junit.framework.Assert.assertNotNull;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;

/**
 * Tests for {@link DosAttributeProvider}.
 *
 * @author Colin Decker
 */
public class DosAttributeProviderTest extends AttributeProviderTest {

  private static final ImmutableList<String> DOS_ATTRIBUTES =
      ImmutableList.of("hidden", "archive", "readonly", "system");

  @Override
  protected Iterable<? extends AttributeProvider> createProviders() {
    BasicAttributeProvider basic = new BasicAttributeProvider();
    OwnerAttributeProvider owner = new OwnerAttributeProvider(createUserPrincipal("user"));
    DosAttributeProvider dos = new DosAttributeProvider(basic);
    return ImmutableList.of(basic, owner, dos);
  }

  @Test
  public void testInitialAttributes() {
    for (String attribute : DOS_ATTRIBUTES) {
      ASSERT.that(service.getAttribute(file, "dos:" + attribute)).is(false);
    }
  }

  @Test
  public void testSet() {
    for (String attribute : DOS_ATTRIBUTES) {
      assertSetAndGetSucceeds("dos:" + attribute, true);
      assertSetAndGetSucceeds("dos:" + attribute, false);
      assertSetOnCreateFails("dos:" + attribute, true);
    }
  }

  @Test
  public void testView() throws IOException {
    DosFileAttributeView view =
        service.getFileAttributeView(fileProvider(), DosFileAttributeView.class);
    assertNotNull(view);

    ASSERT.that(view.name()).is("dos");

    DosFileAttributes attrs = view.readAttributes();
    ASSERT.that(attrs.isHidden()).isFalse();
    ASSERT.that(attrs.isArchive()).isFalse();
    ASSERT.that(attrs.isReadOnly()).isFalse();
    ASSERT.that(attrs.isSystem()).isFalse();

    view.setArchive(true);
    view.setReadOnly(true);
    view.setHidden(true);
    view.setSystem(false);

    ASSERT.that(attrs.isHidden()).isFalse();
    ASSERT.that(attrs.isArchive()).isFalse();
    ASSERT.that(attrs.isReadOnly()).isFalse();

    attrs = view.readAttributes();
    ASSERT.that(attrs.isHidden()).isTrue();
    ASSERT.that(attrs.isArchive()).isTrue();
    ASSERT.that(attrs.isReadOnly()).isTrue();
    ASSERT.that(attrs.isSystem()).isFalse();

    view.setTimes(FileTime.fromMillis(0L), null, null);
    ASSERT.that(view.readAttributes().lastModifiedTime())
        .is(FileTime.fromMillis(0L));
  }

  @Test
  public void testAttributes() {
    DosFileAttributes attrs = service.readAttributes(file, DosFileAttributes.class);
    ASSERT.that(attrs.isHidden()).isFalse();
    ASSERT.that(attrs.isArchive()).isFalse();
    ASSERT.that(attrs.isReadOnly()).isFalse();
    ASSERT.that(attrs.isSystem()).isFalse();

    service.setAttribute(file, "dos:hidden", true);

    attrs = service.readAttributes(file, DosFileAttributes.class);
    ASSERT.that(attrs.isHidden()).isTrue();
    ASSERT.that(attrs.isArchive()).isFalse();
    ASSERT.that(attrs.isReadOnly()).isFalse();
    ASSERT.that(attrs.isSystem()).isFalse();
  }
}
