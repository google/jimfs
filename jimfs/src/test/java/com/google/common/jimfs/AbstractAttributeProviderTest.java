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
import static org.junit.Assert.fail;

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
  protected File file;

  /**
   * Create the provider being tested.
   */
  protected abstract P createProvider();

  /**
   * Creates the set of providers the provider being tested depends on.
   */
  protected abstract Set<? extends AttributeProvider> createInheritedProviders();

  protected FileLookup fileLookup() {
    return new FileLookup() {
      @Override
      public File lookup() throws IOException {
        return file;
      }
    };
  }

  @Before
  public void setUp() {
    this.provider = createProvider();
    this.file = Directory.create(0);

    Map<String, ?> defaultValues = createDefaultValues();
    setDefaultValues(file, provider, defaultValues);

    Set<? extends AttributeProvider> inheritedProviders = createInheritedProviders();
    for (AttributeProvider inherited : inheritedProviders) {
      setDefaultValues(file, inherited, defaultValues);
    }
  }

  private static void setDefaultValues(
      File file, AttributeProvider provider, Map<String, ?> defaultValues) {
    Map<String, ?> defaults = provider.defaultValues(defaultValues);
    for (Map.Entry<String, ?> entry : defaults.entrySet()) {
      int separatorIndex = entry.getKey().indexOf(':');
      String view = entry.getKey().substring(0, separatorIndex);
      String attr = entry.getKey().substring(separatorIndex + 1);
      file.setAttribute(view, attr, entry.getValue());
    }
  }

  protected Map<String, ?> createDefaultValues() {
    return ImmutableMap.of();
  }

  // assertions

  protected void assertSupportsAll(String... attributes) {
    for (String attribute : attributes) {
      assertThat(provider.supports(attribute)).isTrue();
    }
  }

  protected void assertContainsAll(File file, ImmutableMap<String, Object> expectedAttributes) {
    for (Map.Entry<String, Object> entry : expectedAttributes.entrySet()) {
      String attribute = entry.getKey();
      Object value = entry.getValue();

      assertThat(provider.get(file, attribute)).isEqualTo(value);
    }
  }

  protected void assertSetAndGetSucceeds(String attribute, Object value) {
    assertSetAndGetSucceeds(attribute, value, false);
  }

  protected void assertSetAndGetSucceedsOnCreate(String attribute, Object value) {
    assertSetAndGetSucceeds(attribute, value, true);
  }

  protected void assertSetAndGetSucceeds(String attribute, Object value, boolean create) {
    provider.set(file, provider.name(), attribute, value, create);
    assertThat(provider.get(file, attribute)).isEqualTo(value);
  }

  @SuppressWarnings("EmptyCatchBlock")
  protected void assertSetFails(String attribute, Object value) {
    try {
      provider.set(file, provider.name(), attribute, value, false);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @SuppressWarnings("EmptyCatchBlock")
  protected void assertSetFailsOnCreate(String attribute, Object value) {
    try {
      provider.set(file, provider.name(), attribute, value, true);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }
}
