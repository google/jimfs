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
import static org.junit.Assert.assertNotNull;

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
      assertThat(provider.get(file, attribute)).isEqualTo(false);
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
    DosFileAttributeView view =
        provider.view(
            fileLookup(),
            ImmutableMap.<String, FileAttributeView>of(
                "basic", new BasicAttributeProvider().view(fileLookup(), NO_INHERITED_VIEWS)));
    assertNotNull(view);

    assertThat(view.name()).isEqualTo("dos");

    DosFileAttributes attrs = view.readAttributes();
    assertThat(attrs.isHidden()).isFalse();
    assertThat(attrs.isArchive()).isFalse();
    assertThat(attrs.isReadOnly()).isFalse();
    assertThat(attrs.isSystem()).isFalse();

    view.setArchive(true);
    view.setReadOnly(true);
    view.setHidden(true);
    view.setSystem(false);

    assertThat(attrs.isHidden()).isFalse();
    assertThat(attrs.isArchive()).isFalse();
    assertThat(attrs.isReadOnly()).isFalse();

    attrs = view.readAttributes();
    assertThat(attrs.isHidden()).isTrue();
    assertThat(attrs.isArchive()).isTrue();
    assertThat(attrs.isReadOnly()).isTrue();
    assertThat(attrs.isSystem()).isFalse();

    view.setTimes(FileTime.fromMillis(0L), null, null);
    assertThat(view.readAttributes().lastModifiedTime()).isEqualTo(FileTime.fromMillis(0L));
  }

  @Test
  public void testAttributes() {
    DosFileAttributes attrs = provider.readAttributes(file);
    assertThat(attrs.isHidden()).isFalse();
    assertThat(attrs.isArchive()).isFalse();
    assertThat(attrs.isReadOnly()).isFalse();
    assertThat(attrs.isSystem()).isFalse();

    file.setAttribute("dos", "hidden", true);

    attrs = provider.readAttributes(file);
    assertThat(attrs.isHidden()).isTrue();
    assertThat(attrs.isArchive()).isFalse();
    assertThat(attrs.isReadOnly()).isFalse();
    assertThat(attrs.isSystem()).isFalse();
  }
}
