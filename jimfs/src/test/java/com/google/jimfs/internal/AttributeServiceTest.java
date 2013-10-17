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
import com.google.jimfs.attribute.FakeInode;
import com.google.jimfs.attribute.Inode;
import com.google.jimfs.attribute.StandardAttributeProviders;
import com.google.jimfs.attribute.TestAttributeProvider;
import com.google.jimfs.attribute.TestAttributeView;
import com.google.jimfs.attribute.TestAttributes;

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
 * Tests for {@link AttributeService}.
 *
 * @author Colin Decker
 */
public class AttributeServiceTest {

  private AttributeService service;

  @Before
  public void setUp() {
    ImmutableSet<AttributeProvider> providers = ImmutableSet.of(
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
    Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode);

    ASSERT.that(ImmutableSet.copyOf(inode.getAttributeKeys())).is(
        ImmutableSet.of(
            "test:bar",
            "test:baz",
            "owner:owner"));

    ASSERT.that(service.getAttribute(inode, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(inode.getAttribute("test:bar")).is(0L);
    ASSERT.that(inode.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testGetAttribute() {
    Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode);

    ASSERT.that(service.getAttribute(inode, "test:foo")).is("hello");
    ASSERT.that(service.getAttribute(inode, "test", "foo")).is("hello");
    ASSERT.that(service.getAttribute(inode, "basic:isRegularFile")).is(false);
    ASSERT.that(service.getAttribute(inode, "isDirectory")).is(true);
    ASSERT.that(service.getAttribute(inode, "test:baz")).is(1);
  }

  @Test
  public void testGetAttribute_fromInheritedProvider() {
    Inode inode = new FakeInode(0);
    ASSERT.that(service.getAttribute(inode, "test:isRegularFile")).is(false);
    ASSERT.that(service.getAttribute(inode, "test:isDirectory")).is(true);
    ASSERT.that(service.getAttribute(inode, "test", "fileKey")).is(0);
  }

  @Test
  public void testGetAttribute_failsForAttributesNotDefinedByProvider() {
    Inode inode = new FakeInode(0);
    try {
      service.getAttribute(inode, "test:blah");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      service.getAttribute(inode, "basic", "baz");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testSetAttribute() {
    Inode inode = new FakeInode(0);
    service.setAttribute(inode, "test:bar", 10L, false);
    ASSERT.that(inode.getAttribute("test:bar")).is(10L);

    service.setAttribute(inode, "test:baz", 100, false);
    ASSERT.that(inode.getAttribute("test:baz")).is(100);
  }

  @Test
  public void testSetAttribute_forInheritedProvider() {
    Inode inode = new FakeInode(0);
    service.setAttribute(inode, "test:lastModifiedTime", FileTime.fromMillis(0), false);
    ASSERT.that(inode.getAttribute("test:lastModifiedTime")).isNull();
    ASSERT.that(service.getAttribute(inode, "basic:lastModifiedTime")).is(FileTime.fromMillis(0));
  }

  @Test
  public void testSetAttribute_withAlternateAcceptedType() {
    Inode inode = new FakeInode(0);
    service.setAttribute(inode, "test:bar", 10F, false);
    ASSERT.that(inode.getAttribute("test:bar")).is(10L);

    service.setAttribute(inode, "test:bar", BigInteger.valueOf(123), false);
    ASSERT.that(inode.getAttribute("test:bar")).is(123L);
  }

  @Test
  public void testSetAttribute_onCreate() {
    Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode, new BasicFileAttribute<>("test:baz", 123));
    ASSERT.that(inode.getAttribute("test:baz")).is(123);
  }

  @Test
  public void testSetAttribute_failsForAttributesNotDefinedByProvider() {
    Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode);

    try {
      service.setAttribute(inode, "test:blah", "blah", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      service.setAttribute(inode, "basic:baz", 5, false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(inode.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testSetAttribute_failsForArgumentThatIsNotOfCorrectType() {
    Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode);
    try {
      service.setAttribute(inode, "test:bar", "wrong", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(inode.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForNullArgument() {
    Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode);
    try {
      service.setAttribute(inode, "test:bar", null, false);
      fail();
    } catch (NullPointerException expected) {
    }

    ASSERT.that(inode.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForAttributeThatIsNotSettable() {
    Inode inode = new FakeInode(0);
    try {
      service.setAttribute(inode, "test:foo", "world", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(inode.getAttribute("test:foo")).isNull();
  }

  @Test
  public void testSetAttribute_onCreate_failsForAttributeThatIsNotSettableOnCreate() {
    Inode inode = new FakeInode(0);
    try {
      service.setInitialAttributes(inode, new BasicFileAttribute<>("test:foo", "world"));
      fail();
    } catch (IllegalArgumentException expected) {
      // IAE because test:foo just can't be set
    }

    try {
      service.setInitialAttributes(inode, new BasicFileAttribute<>("test:bar", 5));
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testGetFileAttributeView() throws IOException {
    final Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode);

    Inode.Lookup inodeLookup = new Inode.Lookup() {
      @Override
      public Inode lookup() throws IOException {
        return inode;
      }
    };

    ASSERT.that(service.getFileAttributeView(inodeLookup, TestAttributeView.class))
        .isNotNull();
    ASSERT.that(service.getFileAttributeView(inodeLookup, BasicFileAttributeView.class))
        .isNotNull();

    TestAttributes attrs
        = service.getFileAttributeView(inodeLookup, TestAttributeView.class).readAttributes();
    ASSERT.that(attrs.foo()).is("hello");
    ASSERT.that(attrs.bar()).is(0);
    ASSERT.that(attrs.baz()).is(1);
  }

  @Test
  public void testGetFileAttributeView_isNullForUnsupportedView() {
    final Inode inode = new FakeInode(0);
    Inode.Lookup inodeLookup = new Inode.Lookup() {
      @Override
      public Inode lookup() throws IOException {
        return inode;
      }
    };
    ASSERT.that(service.getFileAttributeView(inodeLookup, PosixFileAttributeView.class))
        .isNull();
  }

  @Test
  public void testReadAttributes_asMap() {
    Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode);

    ImmutableMap<String, Object> map = service.readAttributes(inode, "test:foo,bar,baz");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.of(
            "foo", "hello",
            "bar", 0L,
            "baz", 1));

    FileTime time = service.getAttribute(inode, "basic:creationTime");

    map = service.readAttributes(inode, "test:*");
    ASSERT.that(map).isEqualTo(
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

    map = service.readAttributes(inode, "basic:*");
    ASSERT.that(map).isEqualTo(
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
    Inode inode = new FakeInode(0);
    try {
      service.readAttributes(inode, "basic:fileKey,isOther,*,creationTime");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("invalid attributes");
    }

    try {
      service.readAttributes(inode, "basic:fileKey,isOther,foo");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("invalid attribute");
    }
  }

  @Test
  public void testReadAttributes_asObject() {
    Inode inode = new FakeInode(0);
    service.setInitialAttributes(inode);

    BasicFileAttributes basicAttrs = service.readAttributes(inode, BasicFileAttributes.class);
    ASSERT.that(basicAttrs.fileKey()).is(0);
    ASSERT.that(basicAttrs.isDirectory()).isTrue();
    ASSERT.that(basicAttrs.isRegularFile()).isFalse();

    TestAttributes testAttrs = service.readAttributes(inode, TestAttributes.class);
    ASSERT.that(testAttrs.foo()).is("hello");
    ASSERT.that(testAttrs.bar()).is(0);
    ASSERT.that(testAttrs.baz()).is(1);

    inode.setAttribute("test:baz", 100);
    ASSERT.that(service.readAttributes(inode, TestAttributes.class).baz()).is(100);
  }

  @Test
  public void testReadAttributes_failsForUnsupportedAttributesType() {
    Inode inode = new FakeInode(0);
    try {
      service.readAttributes(inode, PosixFileAttributes.class);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testIllegalAttributeFormats() {
    Inode inode = new FakeInode(0);
    try {
      service.getAttribute(inode, ":bar");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(inode, "test:");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(inode, "basic:test:isDirectory");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(inode, "basic:fileKey,size");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("single attribute");
    }
  }
}
