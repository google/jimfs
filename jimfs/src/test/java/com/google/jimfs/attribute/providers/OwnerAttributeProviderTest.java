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

import static com.google.jimfs.attribute.UserLookupService.createUserPrincipal;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.FileOwnerAttributeView;

/**
 * Tests for {@link OwnerAttributeProvider}.
 *
 * @author Colin Decker
 */
public class OwnerAttributeProviderTest extends AttributeProviderTest<OwnerAttributeProvider> {

  @Override
  protected OwnerAttributeProvider createProvider() {
    return new OwnerAttributeProvider("user");
  }

  @Test
  public void testInitialAttributes() {
    ASSERT.that(provider.get(metadata, "owner")).isEqualTo(createUserPrincipal("user"));
  }

  @Test
  public void testSet() {
    assertCanSetOnCreate("owner");
    assertSetFails("owner", "root");
  }

  @Test
  public void testView() throws IOException {
    FileOwnerAttributeView view = provider.getView(metadataSupplier());
    assert view != null;

    ASSERT.that(view.name()).is("owner");
    ASSERT.that(view.getOwner()).isEqualTo(createUserPrincipal("user"));

    view.setOwner(createUserPrincipal("root"));
    ASSERT.that(view.getOwner()).isEqualTo(createUserPrincipal("root"));
    ASSERT.that(metadata.getAttribute("owner:owner")).isEqualTo(createUserPrincipal("root"));
  }
}
