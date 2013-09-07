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

import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.common.IoSupplier;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link AbstractAttributeProvider}, as well as for handling views and bulk attributes
 * by having an {@link AttributeProvider} also implement {@link AttributeViewProvider} and
 * {@link AttributeReader}.
 *
 * @author Colin Decker
 */
public class AbstractAttributeProviderTest {

  private static final TestAttributeProvider PROVIDER = new TestAttributeProvider();

  private AttributeStore store;

  @Before
  public void setUp() {
    store = new TestAttributeStore(0, TestAttributeStore.Type.REGULAR_FILE);
  }

  @Test
  public void testSimpleGetters() {
    ASSERT.that(PROVIDER.name()).is("test");
    ASSERT.that(PROVIDER.inherits()).is(ImmutableSet.of("basic"));
    ASSERT.that(PROVIDER.acceptedTypes("foo")).is(ImmutableSet.<Class<?>>of(String.class));
    ASSERT.that(PROVIDER.acceptedTypes("bar")).is(ImmutableSet.<Class<?>>of(Number.class));
    ASSERT.that(PROVIDER.acceptedTypes("baz")).is(ImmutableSet.<Class<?>>of(Integer.class));
  }

  @Test
  public void testIsGettableAndSettable() {
    ASSERT.that(PROVIDER.isGettable(store, "foo")).isTrue();
    ASSERT.that(PROVIDER.isGettable(store, "bar")).isTrue();
    ASSERT.that(PROVIDER.isGettable(store, "baz")).isTrue();

    ASSERT.that(PROVIDER.isSettable(store, "foo")).isFalse();
    ASSERT.that(PROVIDER.isSettable(store, "bar")).isTrue();
    ASSERT.that(PROVIDER.isSettable(store, "baz")).isTrue();

    ASSERT.that(PROVIDER.isSettableOnCreate("foo")).isFalse();
    ASSERT.that(PROVIDER.isSettableOnCreate("bar")).isFalse();
    ASSERT.that(PROVIDER.isSettableOnCreate("baz")).isTrue();

    ASSERT.that(PROVIDER.isGettable(store, "blah")).isFalse();
    ASSERT.that(PROVIDER.isSettable(store, "blah")).isFalse();
    // calling isSettableOnCreate if isSettable is false is illegal and throws; don't test it
    // ASSERT.that(PROVIDER.isSettableOnCreate("blah")).isFalse();
  }

  @Test
  public void testGet() {
    ASSERT.that(PROVIDER.get(store, "foo")).is("hello");
    ASSERT.that(PROVIDER.get(store, "bar")).isNull();
    ASSERT.that(PROVIDER.get(store, "baz")).isNull();
  }

  @Test
  public void testSetInitialAndGet() {
    PROVIDER.setInitial(store);

    ASSERT.that(store.getAttribute("test:bar")).is(0L);
    ASSERT.that(store.getAttribute("test:baz")).is(1);

    ASSERT.that(PROVIDER.get(store, "foo")).is("hello");
    ASSERT.that(PROVIDER.get(store, "bar")).is(0L);
    ASSERT.that(PROVIDER.get(store, "baz")).is(1);
  }

  @Test
  public void testSetAndGet() {
    PROVIDER.set(store, "baz", 100);
    ASSERT.that(store.getAttribute("test:baz")).is(100);
    ASSERT.that(PROVIDER.get(store, "baz")).is(100);

    PROVIDER.set(store, "bar", 10L);
    ASSERT.that(store.getAttribute("test:bar")).is(10L);
    ASSERT.that(PROVIDER.get(store, "bar")).is(10L);
  }

  @Test
  public void testSetAndGet_withDifferentAcceptedTypes() {
    PROVIDER.set(store, "bar", 10);
    ASSERT.that(PROVIDER.get(store, "bar")).is(10L);

    PROVIDER.set(store, "bar", 123.0F);
    ASSERT.that(PROVIDER.get(store, "bar")).is(123L);
  }

  @Test
  public void testReadAll() {
    PROVIDER.setInitial(store);
    Map<String, Object> builder = new HashMap<>();
    PROVIDER.readAll(store, builder);
    ImmutableMap<String, Object> attributes = ImmutableMap.copyOf(builder);

    ASSERT.that(attributes).is(
        ImmutableMap.<String, Object>of(
            "foo", "hello",
            "bar", 0L,
            "baz", 1));
  }

  @Test
  public void testView() throws IOException {
    ASSERT.that(PROVIDER.viewType()).is(TestAttributeView.class);

    PROVIDER.setInitial(store);

    TestAttributeView view = PROVIDER.getView(IoSupplier.of(store));

    ASSERT.that(view.name()).is("test");

    TestAttributes attrs = view.readAttributes();
    ASSERT.that(attrs.foo()).is("hello");
    ASSERT.that(attrs.bar()).is(0L);
    ASSERT.that(attrs.baz()).is(1);

    view.setBar(5L);
    view.setBaz(50);

    attrs = view.readAttributes();
    ASSERT.that(attrs.bar()).is(5L);
    ASSERT.that(attrs.baz()).is(50);
  }

  @Test
  public void testReadAttributes() {
    ASSERT.that(PROVIDER.attributesType()).is(TestAttributes.class);

    PROVIDER.setInitial(store);

    TestAttributes attrs = PROVIDER.read(store);

    ASSERT.that(attrs.foo()).is("hello");
    ASSERT.that(attrs.bar()).is(0L);
    ASSERT.that(attrs.baz()).is(1);

    PROVIDER.set(store, "bar", 100L);

    ASSERT.that(attrs.bar()).is(0L);
    ASSERT.that(PROVIDER.read(store).bar()).is(100L);
  }
}
