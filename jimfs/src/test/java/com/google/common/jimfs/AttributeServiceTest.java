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
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;

/**
 * Tests for {@link AttributeService}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class AttributeServiceTest {

  private AttributeService service;

  @Before
  public void setUp() {
    ImmutableSet<AttributeProvider> providers =
        ImmutableSet.of(
            StandardAttributeProviders.get("basic"),
            StandardAttributeProviders.get("owner"),
            new TestAttributeProvider());
    service = new AttributeService(providers, ImmutableMap.<String, Object>of());
  }

  @Test
  public void testSupportedFileAttributeViews() {
    assertThat(service.supportedFileAttributeViews())
        .isEqualTo(ImmutableSet.of("basic", "test", "owner"));
  }

  @Test
  public void testSupportsFileAttributeView() {
    assertThat(service.supportsFileAttributeView(BasicFileAttributeView.class)).isTrue();
    assertThat(service.supportsFileAttributeView(TestAttributeView.class)).isTrue();
    assertThat(service.supportsFileAttributeView(PosixFileAttributeView.class)).isFalse();
  }

  @Test
  public void testSetInitialAttributes() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    assertThat(file.getAttributeNames("test")).containsExactly("bar", "baz");
    assertThat(file.getAttributeNames("owner")).containsExactly("owner");

    assertThat(service.getAttribute(file, "basic:lastModifiedTime")).isInstanceOf(FileTime.class);
    assertThat(file.getAttribute("test", "bar")).isEqualTo(0L);
    assertThat(file.getAttribute("test", "baz")).isEqualTo(1);
  }

  @Test
  public void testGetAttribute() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    assertThat(service.getAttribute(file, "test:foo")).isEqualTo("hello");
    assertThat(service.getAttribute(file, "test", "foo")).isEqualTo("hello");
    assertThat(service.getAttribute(file, "basic:isRegularFile")).isEqualTo(false);
    assertThat(service.getAttribute(file, "isDirectory")).isEqualTo(true);
    assertThat(service.getAttribute(file, "test:baz")).isEqualTo(1);
  }

  @Test
  public void testGetAttribute_fromInheritedProvider() {
    File file = Directory.create(0);
    assertThat(service.getAttribute(file, "test:isRegularFile")).isEqualTo(false);
    assertThat(service.getAttribute(file, "test:isDirectory")).isEqualTo(true);
    assertThat(service.getAttribute(file, "test", "fileKey")).isEqualTo(0);
  }

  @Test
  public void testGetAttribute_failsForAttributesNotDefinedByProvider() {
    File file = Directory.create(0);
    try {
      service.getAttribute(file, "test:blah");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      service.getAttribute(file, "basic", "baz");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testSetAttribute() {
    File file = Directory.create(0);
    service.setAttribute(file, "test:bar", 10L, false);
    assertThat(file.getAttribute("test", "bar")).isEqualTo(10L);

    service.setAttribute(file, "test:baz", 100, false);
    assertThat(file.getAttribute("test", "baz")).isEqualTo(100);
  }

  @Test
  public void testSetAttribute_forInheritedProvider() {
    File file = Directory.create(0);
    service.setAttribute(file, "test:lastModifiedTime", FileTime.fromMillis(0), false);
    assertThat(file.getAttribute("test", "lastModifiedTime")).isNull();
    assertThat(service.getAttribute(file, "basic:lastModifiedTime"))
        .isEqualTo(FileTime.fromMillis(0));
  }

  @Test
  public void testSetAttribute_withAlternateAcceptedType() {
    File file = Directory.create(0);
    service.setAttribute(file, "test:bar", 10F, false);
    assertThat(file.getAttribute("test", "bar")).isEqualTo(10L);

    service.setAttribute(file, "test:bar", BigInteger.valueOf(123), false);
    assertThat(file.getAttribute("test", "bar")).isEqualTo(123L);
  }

  @Test
  public void testSetAttribute_onCreate() {
    File file = Directory.create(0);
    service.setInitialAttributes(file, new BasicFileAttribute<>("test:baz", 123));
    assertThat(file.getAttribute("test", "baz")).isEqualTo(123);
  }

  @Test
  public void testSetAttribute_failsForAttributesNotDefinedByProvider() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    try {
      service.setAttribute(file, "test:blah", "blah", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      service.setAttribute(file, "basic:baz", 5, false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    assertThat(file.getAttribute("test", "baz")).isEqualTo(1);
  }

  @Test
  public void testSetAttribute_failsForArgumentThatIsNotOfCorrectType() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);
    try {
      service.setAttribute(file, "test:bar", "wrong", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    assertThat(file.getAttribute("test", "bar")).isEqualTo(0L);
  }

  @Test
  public void testSetAttribute_failsForNullArgument() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);
    try {
      service.setAttribute(file, "test:bar", null, false);
      fail();
    } catch (NullPointerException expected) {
    }

    assertThat(file.getAttribute("test", "bar")).isEqualTo(0L);
  }

  @Test
  public void testSetAttribute_failsForAttributeThatIsNotSettable() {
    File file = Directory.create(0);
    try {
      service.setAttribute(file, "test:foo", "world", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    assertThat(file.getAttribute("test", "foo")).isNull();
  }

  @Test
  public void testSetAttribute_onCreate_failsForAttributeThatIsNotSettableOnCreate() {
    File file = Directory.create(0);
    try {
      service.setInitialAttributes(file, new BasicFileAttribute<>("test:foo", "world"));
      fail();
    } catch (IllegalArgumentException expected) {
      // IAE because test:foo just can't be set
    }

    try {
      service.setInitialAttributes(file, new BasicFileAttribute<>("test:bar", 5));
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testGetFileAttributeView() throws IOException {
    final File file = Directory.create(0);
    service.setInitialAttributes(file);

    FileLookup fileLookup =
        new FileLookup() {
          @Override
          public File lookup() throws IOException {
            return file;
          }
        };

    assertThat(service.getFileAttributeView(fileLookup, TestAttributeView.class)).isNotNull();
    assertThat(service.getFileAttributeView(fileLookup, BasicFileAttributeView.class)).isNotNull();

    TestAttributes attrs =
        service.getFileAttributeView(fileLookup, TestAttributeView.class).readAttributes();
    assertThat(attrs.foo()).isEqualTo("hello");
    assertThat(attrs.bar()).isEqualTo(0);
    assertThat(attrs.baz()).isEqualTo(1);
  }

  @Test
  public void testGetFileAttributeView_isNullForUnsupportedView() {
    final File file = Directory.create(0);
    FileLookup fileLookup =
        new FileLookup() {
          @Override
          public File lookup() throws IOException {
            return file;
          }
        };
    assertThat(service.getFileAttributeView(fileLookup, PosixFileAttributeView.class)).isNull();
  }

  @Test
  public void testReadAttributes_asMap() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    ImmutableMap<String, Object> map = service.readAttributes(file, "test:foo,bar,baz");
    assertThat(map)
        .isEqualTo(
            ImmutableMap.of(
                "foo", "hello",
                "bar", 0L,
                "baz", 1));

    FileTime time = (FileTime) service.getAttribute(file, "basic:creationTime");

    map = service.readAttributes(file, "test:*");
    assertThat(map)
        .isEqualTo(
            ImmutableMap.<String, Object>builder()
                .put("foo", "hello")
                .put("bar", 0L)
                .put("baz", 1)
                .put("fileKey", 0)
                .put("isDirectory", true)
                .put("isRegularFile", false)
                .put("isSymbolicLink", false)
                .put("isOther", false)
                .put("size", 0L)
                .put("lastModifiedTime", time)
                .put("lastAccessTime", time)
                .put("creationTime", time)
                .build());

    map = service.readAttributes(file, "basic:*");
    assertThat(map)
        .isEqualTo(
            ImmutableMap.<String, Object>builder()
                .put("fileKey", 0)
                .put("isDirectory", true)
                .put("isRegularFile", false)
                .put("isSymbolicLink", false)
                .put("isOther", false)
                .put("size", 0L)
                .put("lastModifiedTime", time)
                .put("lastAccessTime", time)
                .put("creationTime", time)
                .build());
  }

  @Test
  public void testReadAttributes_asMap_failsForInvalidAttributes() {
    File file = Directory.create(0);
    try {
      service.readAttributes(file, "basic:fileKey,isOther,*,creationTime");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("invalid attributes");
    }

    try {
      service.readAttributes(file, "basic:fileKey,isOther,foo");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("invalid attribute");
    }
  }

  @Test
  public void testReadAttributes_asObject() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    BasicFileAttributes basicAttrs = service.readAttributes(file, BasicFileAttributes.class);
    assertThat(basicAttrs.fileKey()).isEqualTo(0);
    assertThat(basicAttrs.isDirectory()).isTrue();
    assertThat(basicAttrs.isRegularFile()).isFalse();

    TestAttributes testAttrs = service.readAttributes(file, TestAttributes.class);
    assertThat(testAttrs.foo()).isEqualTo("hello");
    assertThat(testAttrs.bar()).isEqualTo(0);
    assertThat(testAttrs.baz()).isEqualTo(1);

    file.setAttribute("test", "baz", 100);
    assertThat(service.readAttributes(file, TestAttributes.class).baz()).isEqualTo(100);
  }

  @Test
  public void testReadAttributes_failsForUnsupportedAttributesType() {
    File file = Directory.create(0);
    try {
      service.readAttributes(file, PosixFileAttributes.class);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testIllegalAttributeFormats() {
    File file = Directory.create(0);
    try {
      service.getAttribute(file, ":bar");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "test:");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "basic:test:isDirectory");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "basic:fileKey,size");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("single attribute");
    }
  }
}
