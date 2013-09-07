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

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Arrays;
import java.util.Map;

/**
 * Tests for {@link UserDefinedAttributeProvider}.
 *
 * @author Colin Decker
 */
public class UserDefinedAttributeProviderTest extends AttributeProviderTest {

  @Override
  protected Iterable<? extends AttributeProvider> createProviders() {
    return ImmutableList.of(new UserDefinedAttributeProvider());
  }

  @Test
  public void testInitialAttributes() {
    // no initial attributes
    ASSERT.that(ImmutableList.copyOf(file.getAttributeKeys())).isEmpty();
  }

  @Test
  public void testBasicProperties() {
    UserDefinedAttributeProvider provider = new UserDefinedAttributeProvider();
    ASSERT.that(provider.isSettableOnCreate("anything")).isFalse();
    ASSERT.that(provider.isSettable(file, "anything")).isTrue();
    ASSERT.that(provider.acceptedTypes("anything"))
        .is(ImmutableSet.of(byte[].class, ByteBuffer.class));
  }

  @Test
  public void testGettingAndSetting() {
    byte[] bytes = {0, 1, 2, 3};
    service.setAttribute(file, "user", "one", bytes);
    service.setAttribute(file, "user:two", ByteBuffer.wrap(bytes));

    byte[] one = service.getAttribute(file, "user:one");
    byte[] two = service.getAttribute(file, "user", "two");
    ASSERT.that(Arrays.equals(one, bytes)).isTrue();
    ASSERT.that(Arrays.equals(two, bytes)).isTrue();

    assertSetOnCreateFails("user:foo", bytes);
    assertSetFails("user:foo", "hello");

    Map<String, Object> map = service.readAttributes(file, "user:*");
    ASSERT.that(map.size()).is(2);
    ASSERT.that(Arrays.equals((byte[]) map.get("one"), bytes)).isTrue();
    ASSERT.that(Arrays.equals((byte[]) map.get("two"), bytes)).isTrue();
  }

  @Test
  public void testView() throws IOException {
    UserDefinedFileAttributeView view =
        service.getFileAttributeView(fileSupplier(), UserDefinedFileAttributeView.class);
    assertNotNull(view);

    ASSERT.that(view.name()).is("user");
    ASSERT.that(view.list()).isEmpty();

    byte[] b1 = {0, 1, 2};
    byte[] b2 = {0, 1, 2, 3, 4};

    view.write("b1", ByteBuffer.wrap(b1));
    view.write("b2", ByteBuffer.wrap(b2));

    ASSERT.that(view.list()).has().allOf("b1", "b2");
    ASSERT.that(service.readAttributes(file, "user:*").keySet())
        .has().allOf("b1", "b2");

    ASSERT.that(view.size("b1")).is(3);
    ASSERT.that(view.size("b2")).is(5);

    ByteBuffer buf1 = ByteBuffer.allocate(view.size("b1"));
    ByteBuffer buf2 = ByteBuffer.allocate(view.size("b2"));

    view.read("b1", buf1);
    view.read("b2", buf2);

    ASSERT.that(Arrays.equals(b1, buf1.array())).isTrue();
    ASSERT.that(Arrays.equals(b2, buf2.array())).isTrue();

    view.delete("b2");

    ASSERT.that(view.list()).has().exactly("b1");
    ASSERT.that(service.readAttributes(file, "user:*").keySet())
        .has().exactly("b1");

    try {
      view.size("b2");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("not set");
    }

    try {
      view.read("b2", ByteBuffer.allocate(10));
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("not set");
    }

    view.write("b1", ByteBuffer.wrap(b2));
    ASSERT.that(view.size("b1")).is(5);

    view.delete("b2"); // succeeds
  }
}
