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

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;
import com.google.jimfs.common.IoSupplier;
import com.google.jimfs.internal.JimfsFileStore;
import com.google.jimfs.internal.file.File;
import com.google.jimfs.testing.BasicFileAttribute;

import org.junit.Before;

import java.util.Map;

/**
 * Base class for tests of individual {@link AttributeProvider} implementations. Implementations
 * are tested through an {@link JimfsFileStore}, since some of the functionality of the
 * behavior specified by the provider is only exposed through the service methods.
 *
 * @author Colin Decker
 */
public abstract class AttributeProviderTest {

  protected JimfsFileStore service;
  protected File file;
  protected File dir;

  /**
   * Create the needed providers, including the provider being tested.
   */
  protected abstract Iterable<? extends AttributeProvider> createProviders();

  @Before
  public void setUp() {
    this.service = new JimfsFileStore("foo", createProviders());
    this.file = service.createRegularFile();
    this.dir = service.createDirectory();
  }

  protected IoSupplier<File> fileSupplier() {
    return IoSupplier.of(file);
  }

  protected void assertContainsAll(File file, ImmutableMap<String, Object> expectedAttributes) {
    for (Map.Entry<String, Object> entry : expectedAttributes.entrySet()) {
      String attribute = entry.getKey();
      Object value = entry.getValue();

      ASSERT.that(service.getAttribute(file, attribute)).is(value);
    }
  }

  protected void assertSetAndGetSucceeds(String attribute, Object value) {
    service.setAttribute(file, attribute, value);
    ASSERT.that(service.getAttribute(file, attribute)).is(value);
  }

  protected void assertSetOnCreateSucceeds(String attribute, Object value) {
    File file = service.createRegularFile(new BasicFileAttribute<>(attribute, value));
    ASSERT.that(service.getAttribute(file, attribute)).is(value);
  }

  @SuppressWarnings("EmptyCatchBlock") // why am I having to suppress this here?
  protected void assertSetFails(String attribute, Object value) {
    try {
      service.setAttribute(file, attribute, value);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @SuppressWarnings("EmptyCatchBlock")
  protected void assertSetOnCreateFails(String attribute, Object value) {
    try {
      service.createRegularFile(new BasicFileAttribute<>(attribute, value));
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }
}
