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

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.internal.AttributeService;
import com.google.jimfs.attribute.FakeFileMetadata;
import com.google.jimfs.attribute.FileMetadata;

import org.junit.Before;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;
import java.util.Map;
import java.util.Set;

/**
 * Base class for tests of individual {@link AttributeProvider} implementations.
 *
 * @author Colin Decker
 */
public abstract class AttributeProviderTest<P extends AttributeProvider<?>> {

  protected static final ImmutableMap<String, FileAttributeView> NO_INHERITED_VIEWS =
      ImmutableMap.of();

  protected P provider;
  protected FileMetadata metadata;

  /**
   * Create the provider being tested.
   */
  protected abstract P createProvider();

  /**
   * Creates the set of providers the provider being tested depends on.
   */
  protected abstract Set<? extends AttributeProvider<?>> createInheritedProviders();

  protected FileMetadata.Lookup metadataSupplier() {
    return new FileMetadata.Lookup() {
      @Override
      public FileMetadata lookup() throws IOException {
        return metadata;
      }
    };
  }

  @Before
  public void setUp() {
    this.provider = createProvider();
    this.metadata = new FakeFileMetadata(0);
    AttributeService service = createService();
    service.setInitialAttributes(metadata);
  }

  protected final AttributeService createService() {
    ImmutableSet<AttributeProvider<?>> providers = ImmutableSet.<AttributeProvider<?>>builder()
        .add(provider)
        .addAll(createInheritedProviders())
        .build();
    return new AttributeService(providers, createDefaultValues());
  }

  protected Map<String, ?> createDefaultValues() {
    return ImmutableMap.of();
  }

  // assertions

  protected void assertSupportsAll(String... attributes) {
    for (String attribute : attributes) {
      ASSERT.that(provider.supports(attribute)).isTrue();
    }
  }

  protected void assertContainsAll(
      FileMetadata store, ImmutableMap<String, Object> expectedAttributes) {
    for (Map.Entry<String, Object> entry : expectedAttributes.entrySet()) {
      String attribute = entry.getKey();
      Object value = entry.getValue();

      ASSERT.that(provider.get(store, attribute)).is(value);
    }
  }

  protected void assertSetAndGetSucceeds(String attribute, Object value) {
    assertSetAndGetSucceeds(attribute, value, false);
  }

  protected void assertSetAndGetSucceedsOnCreate(String attribute, Object value) {
    assertSetAndGetSucceeds(attribute, value, true);
  }

  protected void assertSetAndGetSucceeds(String attribute, Object value, boolean create) {
    provider.set(metadata, provider.name(), attribute, value, create);
    ASSERT.that(provider.get(metadata, attribute)).is(value);
  }

  @SuppressWarnings("EmptyCatchBlock")
  protected void assertSetFails(String attribute, Object value) {
    try {
      provider.set(metadata, provider.name(), attribute, value, false);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @SuppressWarnings("EmptyCatchBlock")
  protected void assertSetFailsOnCreate(String attribute, Object value) {
    try {
      provider.set(metadata, provider.name(), attribute, value, true);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }
}
