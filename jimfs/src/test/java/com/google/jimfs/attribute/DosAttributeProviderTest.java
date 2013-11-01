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

import static org.junit.Assert.assertNotNull;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Set;

/**
 * Tests for {@link DosAttributeProvider}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class DosAttributeProviderTest extends AbstractAttributeProviderTest<DosAttributeProvider> {

  private static final ImmutableList<String> DOS_ATTRIBUTES =
      ImmutableList.of("hidden", "archive", "readonly", "system");

  @Override
  protected DosAttributeProvider createProvider() {
    return new DosAttributeProvider();
  }

  @Override
  protected Set<? extends AttributeProvider> createInheritedProviders() {
    return ImmutableSet.of(new BasicAttributeProvider(), new OwnerAttributeProvider());
  }

  @Test
  public void testInitialAttributes() {
    for (String attribute : DOS_ATTRIBUTES) {
      ASSERT.that(provider.get(inode, attribute)).is(false);
    }
  }

  @Test
  public void testSet() {
    for (String attribute : DOS_ATTRIBUTES) {
      assertSetAndGetSucceeds(attribute, true);
      assertSetFailsOnCreate(attribute, true);
    }
  }

  @Test
  public void testView() throws IOException {
    DosFileAttributeView view = provider.view(inodeLookup(),
        ImmutableMap.<String, FileAttributeView>of(
            "basic", new BasicAttributeProvider().view(inodeLookup(), NO_INHERITED_VIEWS)));
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
    DosFileAttributes attrs = provider.readAttributes(inode);
    ASSERT.that(attrs.isHidden()).isFalse();
    ASSERT.that(attrs.isArchive()).isFalse();
    ASSERT.that(attrs.isReadOnly()).isFalse();
    ASSERT.that(attrs.isSystem()).isFalse();

    inode.setAttribute("dos:hidden", true);

    attrs = provider.readAttributes(inode);
    ASSERT.that(attrs.isHidden()).isTrue();
    ASSERT.that(attrs.isArchive()).isFalse();
    ASSERT.that(attrs.isReadOnly()).isFalse();
    ASSERT.that(attrs.isSystem()).isFalse();
  }
}
