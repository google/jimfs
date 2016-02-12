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
import static org.junit.Assert.fail;

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
    assertThat(ImmutableList.copyOf(file.getAttributeKeys())).isEmpty();
    assertThat(provider.attributes(file)).isEmpty();
  }

  @Test
  public void testGettingAndSetting() {
    byte[] bytes = {0, 1, 2, 3};
    provider.set(file, "user", "one", bytes, false);
    provider.set(file, "user", "two", ByteBuffer.wrap(bytes), false);

    byte[] one = (byte[]) provider.get(file, "one");
    byte[] two = (byte[]) provider.get(file, "two");
    assertThat(Arrays.equals(one, bytes)).isTrue();
    assertThat(Arrays.equals(two, bytes)).isTrue();

    assertSetFails("foo", "hello");

    assertThat(provider.attributes(file)).containsExactly("one", "two");
  }

  @Test
  public void testSetOnCreate() {
    assertSetFailsOnCreate("anything", new byte[0]);
  }

  @Test
  public void testView() throws IOException {
    UserDefinedFileAttributeView view = provider.view(fileLookup(), NO_INHERITED_VIEWS);
    assertNotNull(view);

    assertThat(view.name()).isEqualTo("user");
    assertThat(view.list()).isEmpty();

    byte[] b1 = {0, 1, 2};
    byte[] b2 = {0, 1, 2, 3, 4};

    view.write("b1", ByteBuffer.wrap(b1));
    view.write("b2", ByteBuffer.wrap(b2));

    assertThat(view.list()).containsAllOf("b1", "b2");
    assertThat(file.getAttributeKeys()).containsExactly("user:b1", "user:b2");

    assertThat(view.size("b1")).isEqualTo(3);
    assertThat(view.size("b2")).isEqualTo(5);

    ByteBuffer buf1 = ByteBuffer.allocate(view.size("b1"));
    ByteBuffer buf2 = ByteBuffer.allocate(view.size("b2"));

    view.read("b1", buf1);
    view.read("b2", buf2);

    assertThat(Arrays.equals(b1, buf1.array())).isTrue();
    assertThat(Arrays.equals(b2, buf2.array())).isTrue();

    view.delete("b2");

    assertThat(view.list()).containsExactly("b1");
    assertThat(file.getAttributeKeys()).containsExactly("user:b1");

    try {
      view.size("b2");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("not set");
    }

    try {
      view.read("b2", ByteBuffer.allocate(10));
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("not set");
    }

    view.write("b1", ByteBuffer.wrap(b2));
    assertThat(view.size("b1")).isEqualTo(5);

    view.delete("b2"); // succeeds
  }
}
