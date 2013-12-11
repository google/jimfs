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

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base class for tests of individual {@link AttributeProvider} implementations.
 *
 * @author Colin Decker
 */
public abstract class AbstractAttributeProviderTest<P extends AttributeProvider> {

  protected static final ImmutableMap<String, FileAttributeView> NO_INHERITED_VIEWS =
      ImmutableMap.of();

  protected P provider;
  protected Inode inode;

  /**
   * Create the provider being tested.
   */
  protected abstract P createProvider();

  /**
   * Creates the set of providers the provider being tested depends on.
   */
  protected abstract Set<? extends AttributeProvider> createInheritedProviders();

  protected Inode.Lookup inodeLookup() {
    return new Inode.Lookup() {
      @Override
      public Inode lookup() throws IOException {
        return inode;
      }
    };
  }

  @Before
  public void setUp() {
    this.provider = createProvider();
    this.inode = new FakeInode(0);

    Map<String, ?> defaultValues = createDefaultValues();
    setDefaultValues(inode, provider, defaultValues);

    Set<? extends AttributeProvider> inheritedProviders = createInheritedProviders();
    for (AttributeProvider inherited : inheritedProviders) {
      setDefaultValues(inode, inherited, defaultValues);
    }
  }

  private static void setDefaultValues(
      Inode inode, AttributeProvider provider, Map<String, ?> defaultValues) {
    Map<String, ?> defaults = provider.defaultValues(defaultValues);
    for (Map.Entry<String, ?> entry : defaults.entrySet()) {
      int separatorIndex = entry.getKey().indexOf(':');
      String view = entry.getKey().substring(0, separatorIndex);
      String attr = entry.getKey().substring(separatorIndex + 1);
      inode.setAttribute(view, attr, entry.getValue());
    }
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
      Inode store, ImmutableMap<String, Object> expectedAttributes) {
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
    provider.set(inode, provider.name(), attribute, value, create);
    ASSERT.that(provider.get(inode, attribute)).is(value);
  }

  @SuppressWarnings("EmptyCatchBlock")
  protected void assertSetFails(String attribute, Object value) {
    try {
      provider.set(inode, provider.name(), attribute, value, false);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @SuppressWarnings("EmptyCatchBlock")
  protected void assertSetFailsOnCreate(String attribute, Object value) {
    try {
      provider.set(inode, provider.name(), attribute, value, true);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }
}
