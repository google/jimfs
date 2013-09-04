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

import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.internal.FileProvider;
import com.google.jimfs.internal.file.File;
import com.google.jimfs.testing.FakeFileContent;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests for {@link AbstractAttributeProvider}, as well as for handling views and bulk attributes
 * by having an {@link AttributeProvider} also implement {@link AttributeViewProvider} and
 * {@link AttributeReader}.
 *
 * @author Colin Decker
 */
public class AbstractAttributeProviderTest {

  private static final TestAttributeProvider PROVIDER = new TestAttributeProvider();

  private File file;

  @Before
  public void setUp() {
    file = new File(0, new FakeFileContent());
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
    ASSERT.that(PROVIDER.isGettable(file, "foo")).isTrue();
    ASSERT.that(PROVIDER.isGettable(file, "bar")).isTrue();
    ASSERT.that(PROVIDER.isGettable(file, "baz")).isTrue();

    ASSERT.that(PROVIDER.isSettable(file, "foo")).isFalse();
    ASSERT.that(PROVIDER.isSettable(file, "bar")).isTrue();
    ASSERT.that(PROVIDER.isSettable(file, "baz")).isTrue();

    ASSERT.that(PROVIDER.isSettableOnCreate("foo")).isFalse();
    ASSERT.that(PROVIDER.isSettableOnCreate("bar")).isFalse();
    ASSERT.that(PROVIDER.isSettableOnCreate("baz")).isTrue();

    ASSERT.that(PROVIDER.isGettable(file, "blah")).isFalse();
    ASSERT.that(PROVIDER.isSettable(file, "blah")).isFalse();
    // calling isSettableOnCreate if isSettable is false is illegal and throws; don't test it
    // ASSERT.that(PROVIDER.isSettableOnCreate("blah")).isFalse();
  }

  @Test
  public void testGet() {
    ASSERT.that(PROVIDER.get(file, "foo")).is("hello");
    ASSERT.that(PROVIDER.get(file, "bar")).isNull();
    ASSERT.that(PROVIDER.get(file, "baz")).isNull();
  }

  @Test
  public void testSetInitialAndGet() {
    PROVIDER.setInitial(file);

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
    ASSERT.that(file.getAttribute("test:baz")).is(1);

    ASSERT.that(PROVIDER.get(file, "foo")).is("hello");
    ASSERT.that(PROVIDER.get(file, "bar")).is(0L);
    ASSERT.that(PROVIDER.get(file, "baz")).is(1);
  }

  @Test
  public void testSetAndGet() {
    PROVIDER.set(file, "baz", 100);
    ASSERT.that(file.getAttribute("test:baz")).is(100);
    ASSERT.that(PROVIDER.get(file, "baz")).is(100);

    PROVIDER.set(file, "bar", 10L);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);
    ASSERT.that(PROVIDER.get(file, "bar")).is(10L);
  }

  @Test
  public void testSetAndGet_withDifferentAcceptedTypes() {
    PROVIDER.set(file, "bar", 10);
    ASSERT.that(PROVIDER.get(file, "bar")).is(10L);

    PROVIDER.set(file, "bar", 123.0F);
    ASSERT.that(PROVIDER.get(file, "bar")).is(123L);
  }

  @Test
  public void testReadAll() {
    PROVIDER.setInitial(file);
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    PROVIDER.readAll(file, builder);
    ImmutableMap<String, Object> attributes = builder.build();

    ASSERT.that(attributes).is(
        ImmutableMap.<String, Object>of(
            "foo", "hello",
            "bar", 0L,
            "baz", 1));
  }

  @Test
  public void testView() throws IOException {
    ASSERT.that(PROVIDER.viewType()).is(TestAttributeView.class);

    PROVIDER.setInitial(file);

    TestAttributeView view = PROVIDER.getView(FileProvider.ofFile(file));

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

    PROVIDER.setInitial(file);

    TestAttributes attrs = PROVIDER.read(file);

    ASSERT.that(attrs.foo()).is("hello");
    ASSERT.that(attrs.bar()).is(0L);
    ASSERT.that(attrs.baz()).is(1);

    PROVIDER.set(file, "bar", 100L);

    ASSERT.that(attrs.bar()).is(0L);
    ASSERT.that(PROVIDER.read(file).bar()).is(100L);
  }
}
