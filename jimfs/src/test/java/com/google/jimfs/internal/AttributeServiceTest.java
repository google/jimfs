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

package com.google.jimfs.internal;

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.BasicFileAttribute;
import com.google.jimfs.attribute.FakeFileMetadata;
import com.google.jimfs.attribute.FileMetadata;
import com.google.jimfs.attribute.TestAttributeProvider;
import com.google.jimfs.attribute.TestAttributeView;
import com.google.jimfs.attribute.TestAttributes;
import com.google.jimfs.attribute.providers.StandardAttributeProviders;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;

/**
 * @author Colin Decker
 */
public class AttributeServiceTest {

  private AttributeService service;

  @Before
  public void setUp() {
    ImmutableSet<AttributeProvider<?>> providers = ImmutableSet.of(
        StandardAttributeProviders.get("basic"),
        StandardAttributeProviders.get("owner"),
        new TestAttributeProvider());
    service = new AttributeService(providers, ImmutableMap.<String, Object>of());
  }


  @Test
  public void testSupportedFileAttributeViews() {
    ASSERT.that(service.supportedFileAttributeViews())
        .is(ImmutableSet.of("basic", "test", "owner"));
  }

  @Test
  public void testSupportsFileAttributeView() {
    ASSERT.that(service.supportsFileAttributeView(BasicFileAttributeView.class)).isTrue();
    ASSERT.that(service.supportsFileAttributeView(TestAttributeView.class)).isTrue();
    ASSERT.that(service.supportsFileAttributeView(PosixFileAttributeView.class)).isFalse();
  }

  @Test
  public void testSetInitialAttributes() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setInitialAttributes(file);

    ASSERT.that(ImmutableSet.copyOf(file.getAttributeKeys())).is(
        ImmutableSet.of(
            "test:bar",
            "test:baz",
            "owner:owner"));

    ASSERT.that(service.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("test:bar")).is(0L);
    ASSERT.that(file.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testGetAttribute() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setInitialAttributes(file);

    ASSERT.that(service.getAttribute(file, "test:foo")).is("hello");
    ASSERT.that(service.getAttribute(file, "test", "foo")).is("hello");
    ASSERT.that(service.getAttribute(file, "basic:isRegularFile")).is(false);
    ASSERT.that(service.getAttribute(file, "isDirectory")).is(true);
    ASSERT.that(service.getAttribute(file, "test:baz")).is(1);
  }

  @Test
  public void testGetAttribute_fromInheritedProvider() {
    FileMetadata file = new FakeFileMetadata(0L);
    ASSERT.that(service.getAttribute(file, "test:isRegularFile")).is(false);
    ASSERT.that(service.getAttribute(file, "test:isDirectory")).is(true);
    ASSERT.that(service.getAttribute(file, "test", "fileKey")).is(0L);
  }

  @Test
  public void testGetAttribute_failsForAttributesNotDefinedByProvider() {
    FileMetadata file = new FakeFileMetadata(0L);
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
    FileMetadata file = new FakeFileMetadata(0L);
    service.setAttribute(file, "test:bar", 10L, false);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);

    service.setAttribute(file, "test:baz", 100, false);
    ASSERT.that(file.getAttribute("test:baz")).is(100);
  }

  @Test
  public void testSetAttribute_forInheritedProvider() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setAttribute(file, "test:lastModifiedTime", FileTime.fromMillis(0L), false);
    ASSERT.that(file.getAttribute("test:lastModifiedTime")).isNull();
    ASSERT.that(service.getAttribute(file, "basic:lastModifiedTime")).is(FileTime.fromMillis(0L));
  }

  @Test
  public void testSetAttribute_withAlternateAcceptedType() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setAttribute(file, "test:bar", 10f, false);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);

    service.setAttribute(file, "test:bar", BigInteger.valueOf(123L), false);
    ASSERT.that(file.getAttribute("test:bar")).is(123L);
  }

  @Test
  public void testSetAttribute_onCreate() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setInitialAttributes(file, new BasicFileAttribute<>("test:baz", 123));
    ASSERT.that(file.getAttribute("test:baz")).is(123);
  }

  @Test
  public void testSetAttribute_failsForAttributesNotDefinedByProvider() {
    FileMetadata file = new FakeFileMetadata(0L);
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

    ASSERT.that(file.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testSetAttribute_failsForArgumentThatIsNotOfCorrectType() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setInitialAttributes(file);
    try {
      service.setAttribute(file, "test:bar", "wrong", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForNullArgument() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setInitialAttributes(file);
    try {
      service.setAttribute(file, "test:bar", null, false);
      fail();
    } catch (NullPointerException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForAttributeThatIsNotSettable() {
    FileMetadata file = new FakeFileMetadata(0L);
    try {
      service.setAttribute(file, "test:foo", "world", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:foo")).isNull();
  }

  @Test
  public void testSetAttribute_onCreate_failsForAttributeThatIsNotSettableOnCreate() {
    FileMetadata file = new FakeFileMetadata(0L);
    try {
      service.setInitialAttributes(file, new BasicFileAttribute<>("test:foo", "world"));
      fail();
    } catch (IllegalArgumentException expected) {
      // IAE because test:foo just can't be set
    }

    try {
      service.setInitialAttributes(file, new BasicFileAttribute<>("test:bar", 5L));
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testGetFileAttributeView() throws IOException {
    final FileMetadata file = new FakeFileMetadata(0L);
    service.setInitialAttributes(file);

    FileMetadata.Lookup fileSupplier = new FileMetadata.Lookup() {
      @Override
      public FileMetadata lookup() throws IOException {
        return file;
      }
    };

    ASSERT.that(service.getFileAttributeView(fileSupplier, TestAttributeView.class))
        .isNotNull();
    ASSERT.that(service.getFileAttributeView(fileSupplier, BasicFileAttributeView.class))
        .isNotNull();

    TestAttributes attrs
        = service.getFileAttributeView(fileSupplier, TestAttributeView.class).readAttributes();
    ASSERT.that(attrs.foo()).is("hello");
    ASSERT.that(attrs.bar()).is(0L);
    ASSERT.that(attrs.baz()).is(1);
  }

  @Test
  public void testGetFileAttributeView_isNullForUnsupportedView() {
    final FileMetadata file = new FakeFileMetadata(0L);
    FileMetadata.Lookup fileSupplier = new FileMetadata.Lookup() {
      @Override
      public FileMetadata lookup() throws IOException {
        return file;
      }
    };
    ASSERT.that(service.getFileAttributeView(fileSupplier, PosixFileAttributeView.class))
        .isNull();
  }

  @Test
  public void testReadAttributes_asMap() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setInitialAttributes(file);

    ImmutableMap<String, Object> map = service.readAttributes(file, "test:foo,bar,baz");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.of(
            "foo", "hello",
            "bar", 0L,
            "baz", 1));

    FileTime time = service.getAttribute(file, "basic:creationTime");

    map = service.readAttributes(file, "test:*");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.<String, Object>builder()
            .put("foo", "hello")
            .put("bar", 0L)
            .put("baz", 1)
            .put("fileKey", 0L)
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
    ASSERT.that(map).isEqualTo(
        ImmutableMap.<String, Object>builder()
            .put("fileKey", 0L)
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
    FileMetadata file = new FakeFileMetadata(0L);
    try {
      service.readAttributes(file, "basic:fileKey,isOther,*,creationTime");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("invalid attributes");
    }

    try {
      service.readAttributes(file, "basic:fileKey,isOther,foo");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("invalid attribute");
    }
  }

  @Test
  public void testReadAttributes_asObject() {
    FileMetadata file = new FakeFileMetadata(0L);
    service.setInitialAttributes(file);

    BasicFileAttributes basicAttrs = service.readAttributes(file, BasicFileAttributes.class);
    ASSERT.that(basicAttrs.fileKey()).is(0L);
    ASSERT.that(basicAttrs.isDirectory()).isTrue();
    ASSERT.that(basicAttrs.isRegularFile()).isFalse();

    TestAttributes testAttrs = service.readAttributes(file, TestAttributes.class);
    ASSERT.that(testAttrs.foo()).is("hello");
    ASSERT.that(testAttrs.bar()).is(0L);
    ASSERT.that(testAttrs.baz()).is(1);

    file.setAttribute("test:baz", 100);
    ASSERT.that(service.readAttributes(file, TestAttributes.class).baz()).is(100);
  }

  @Test
  public void testReadAttributes_failsForUnsupportedAttributesType() {
    FileMetadata file = new FakeFileMetadata(0L);
    try {
      service.readAttributes(file, PosixFileAttributes.class);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testIllegalAttributeFormats() {
    FileMetadata file = new FakeFileMetadata(0L);
    try {
      service.getAttribute(file, ":bar");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "test:");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "basic:test:isDirectory");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "basic:fileKey,size");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("single attribute");
    }
  }
}
