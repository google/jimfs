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

package com.google.jimfs.internal.attribute;

import static com.google.jimfs.internal.attribute.UserLookupService.createUserPrincipal;
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
    assertSetOnCreateSucceeds("owner:owner", createUserPrincipal("root"));
    assertSetFails("owner:owner", "root");
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
