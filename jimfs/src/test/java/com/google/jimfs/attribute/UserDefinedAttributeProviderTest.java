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
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Arrays;
import java.util.Set;

/**
 * Tests for {@link UserDefinedAttributeProvider}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class UserDefinedAttributeProviderTest
    extends AbstractAttributeProviderTest<UserDefinedAttributeProvider> {

  @Override
  protected UserDefinedAttributeProvider createProvider() {
    return new UserDefinedAttributeProvider();
  }

  @Override
  protected Set<? extends AttributeProvider> createInheritedProviders() {
    return ImmutableSet.of();
  }

  @Test
  public void testInitialAttributes() {
    // no initial attributes
    ASSERT.that(ImmutableList.copyOf(inode.getAttributeKeys())).isEmpty();
    ASSERT.that(provider.attributes(inode)).isEmpty();
  }

  @Test
  public void testGettingAndSetting() {
    byte[] bytes = {0, 1, 2, 3};
    provider.set(inode, "user", "one", bytes, false);
    provider.set(inode, "user", "two", ByteBuffer.wrap(bytes), false);

    byte[] one = (byte[]) provider.get(inode, "one");
    byte[] two = (byte[]) provider.get(inode, "two");
    ASSERT.that(Arrays.equals(one, bytes)).isTrue();
    ASSERT.that(Arrays.equals(two, bytes)).isTrue();

    assertSetFails("foo", "hello");

    ASSERT.that(provider.attributes(inode)).has().exactly("one", "two");
  }

  @Test
  public void testSetOnCreate() {
    assertSetFailsOnCreate("anything", new byte[0]);
  }

  @Test
  public void testView() throws IOException {
    UserDefinedFileAttributeView view = provider.view(inodeLookup(), NO_INHERITED_VIEWS);
    assertNotNull(view);

    ASSERT.that(view.name()).is("user");
    ASSERT.that(view.list()).isEmpty();

    byte[] b1 = {0, 1, 2};
    byte[] b2 = {0, 1, 2, 3, 4};

    view.write("b1", ByteBuffer.wrap(b1));
    view.write("b2", ByteBuffer.wrap(b2));

    ASSERT.that(view.list()).has().allOf("b1", "b2");
    ASSERT.that(inode.getAttributeKeys()).has().exactly("user:b1", "user:b2");

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
    ASSERT.that(inode.getAttributeKeys()).has().exactly("user:b1");

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
