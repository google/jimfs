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
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.AttributeStore;
import com.google.jimfs.attribute.IoSupplier;
import com.google.jimfs.attribute.TestAttributeStore;

import org.junit.Before;

import java.util.Map;

/**
 * Base class for tests of individual {@link AttributeProvider} implementations.
 *
 * @author Colin Decker
 */
public abstract class AttributeProviderTest<P extends AttributeProvider> {

  protected P provider;
  protected TestAttributeStore store;

  /**
   * Create the needed providers, including the provider being tested.
   */
  protected abstract P createProvider();

  @Before
  public void setUp() {
    this.provider = createProvider();
    this.store = new TestAttributeStore(0, TestAttributeStore.Type.DIRECTORY);
    provider.setInitial(store);
  }

  protected IoSupplier<AttributeStore> attributeStoreSupplier() {
    return IoSupplier.<AttributeStore>of(store);
  }

  protected void assertContainsAll(
      AttributeStore store, ImmutableMap<String, Object> expectedAttributes) {
    for (Map.Entry<String, Object> entry : expectedAttributes.entrySet()) {
      String attribute = entry.getKey();
      Object value = entry.getValue();

      ASSERT.that(provider.get(store, attribute)).is(value);
    }
  }

  protected void assertSetAndGetSucceeds(String attribute, Object value) {
    ASSERT.that(provider.isSettable(store, attribute));
    provider.set(store, attribute, value);
    ASSERT.that(provider.get(store, attribute)).is(value);
  }

  protected void assertCannotSet(String attribute) {
    ASSERT.that(provider.isSettable(store, attribute)).isFalse();
  }

  @SuppressWarnings("EmptyCatchBlock")
  protected void assertSetFails(String attribute, Object value) {
    // if the value is not one of the accepted types, we know the set will fail at a higher level
    boolean accepted = false;
    for (Class<?> type : provider.acceptedTypes(attribute)) {
      if (type.isInstance(value)) {
        accepted = true;
      }
    }

    if (accepted) {
      // if the value was one of the accepted types, need to try setting it to see if there are any
      // further checks that cause it to fail
      try {
        provider.set(store, attribute, value);
        fail();
      } catch (Exception expected) {
      }
    }

  }

  protected void assertCanSetOnCreate(String attribute) {
    ASSERT.that(provider.isSettableOnCreate(attribute)).isTrue();
  }

  protected void assertCannotSetOnCreate(String attribute) {
    ASSERT.that(provider.isSettableOnCreate(attribute)).isFalse();
  }
}
