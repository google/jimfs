package com.google.common.io.jimfs.attribute;

import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.CREATE;
import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.NORMAL;
import static com.google.common.io.jimfs.attribute.UserLookupService.createUserPrincipal;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.FileOwnerAttributeView;

/**
 * Tests for {@link OwnerAttributeProvider}.
 *
 * @author Colin Decker
 */
public class OwnerAttributeProviderTest extends AttributeProviderTest {

  @Override
  protected Iterable<? extends AttributeProvider> createProviders() {
    return ImmutableList.of(new OwnerAttributeProvider(createUserPrincipal("user")));
  }

  @Test
  public void testInitialAttributes() {
    ASSERT.that(service.getAttribute(file, "owner:owner")).is(createUserPrincipal("user"));
  }

  @Test
  public void testSet() {
    assertSetAndGetSucceeds("owner:owner", createUserPrincipal("root"), CREATE);
    assertSetFails("owner:owner", "root", NORMAL);
  }

  @Test
  public void testView() throws IOException {
    FileOwnerAttributeView view = service.getFileAttributeView(
        fileProvider(), FileOwnerAttributeView.class);
    assert view != null;

    ASSERT.that(view.name()).is("owner");
    ASSERT.that(view.getOwner()).is(createUserPrincipal("user"));

    view.setOwner(createUserPrincipal("root"));
    ASSERT.that(view.getOwner()).is(createUserPrincipal("root"));
    ASSERT.that(file.getAttribute("owner:owner")).is(createUserPrincipal("root"));
  }
}
