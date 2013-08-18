package com.google.common.io.jimfs.attribute;

import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.CREATE;
import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.NORMAL;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.jimfs.file.FakeFileContent;
import com.google.common.io.jimfs.file.File;
import com.google.common.io.jimfs.file.FileProvider;

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
  private File file;

  @Before
  public void setUp() {
    service = new AttributeService(
        ImmutableList.of(new BasicAttributeProvider(), new TestAttributeProvider()));
    file = new File(0L, new FakeFileContent());
    service.setInitialAttributes(file);
  }

  @Test
  public void testSupportedFileAttributeViews() {
    ASSERT.that(service.supportedFileAttributeViews()).is(ImmutableSet.of("basic", "test"));
  }

  @Test
  public void testSupportsFileAttributeView() {
    ASSERT.that(service.supportsFileAttributeView(BasicFileAttributeView.class)).isTrue();
    ASSERT.that(service.supportsFileAttributeView(TestAttributeView.class)).isTrue();
    ASSERT.that(service.supportsFileAttributeView(PosixFileAttributeView.class)).isFalse();
  }

  @Test
  public void testSetInitialAttributes() {
    // setInitialAttributes() is called during setUp()
    ASSERT.that(ImmutableSet.copyOf(file.getAttributeKeys())).is(
        ImmutableSet.of(
            "basic:lastModifiedTime",
            "basic:creationTime",
            "basic:lastAccessTime",
            "test:bar",
            "test:baz"));

    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("test:bar")).is(0L);
    ASSERT.that(file.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testGetAttribute() {
    ASSERT.that(service.getAttribute(file, "test:foo")).is("hello");
    ASSERT.that(service.getAttribute(file, "test", "foo")).is("hello");
    ASSERT.that(service.getAttribute(file, "basic:isOther")).is(true);
    ASSERT.that(service.getAttribute(file, "isDirectory")).is(false);
    ASSERT.that(service.getAttribute(file, "test:baz")).is(1);
  }

  @Test
  public void testGetAttribute_fromInheritedProvider() {
    ASSERT.that(service.getAttribute(file, "test:isOther")).is(true);
    ASSERT.that(service.getAttribute(file, "test", "fileKey")).is(0L);
  }

  @Test
  public void testGetAttribute_failsForAttributesNotDefinedByProvider() {
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
    service.setAttribute(file, "test:bar", 10L, NORMAL);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);

    service.setAttribute(file, "test", "baz", 100, NORMAL);
    ASSERT.that(file.getAttribute("test:baz")).is(100);
  }

  @Test
  public void testSetAttribute_forInheritedProvider() {
    service.setAttribute(file, "test:lastModifiedTime", FileTime.fromMillis(0L), NORMAL);
    ASSERT.that(file.getAttribute("test:lastModifiedTime")).isNull();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).is(FileTime.fromMillis(0L));
  }

  @Test
  public void testSetAttribute_withAlternateAcceptedType() {
    service.setAttribute(file, "test:bar", 10f, NORMAL);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);

    service.setAttribute(file, "test:bar", BigInteger.valueOf(123L), NORMAL);
    ASSERT.that(file.getAttribute("test:bar")).is(123L);
  }

  @Test
  public void testSetAttribute_onCreate() {
    service.setAttribute(file, "test:baz", 123, CREATE);
    ASSERT.that(file.getAttribute("test:baz")).is(123);
  }

  @Test
  public void testSetAttribute_failsForAttributesNotDefinedByProvider() {
    try {
      service.setAttribute(file, "test:blah", "blah", NORMAL);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      service.setAttribute(file, "basic", "baz", 5, NORMAL);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testSetAttribute_failsForArgumentThatIsNotOfCorrectType() {
    try {
      service.setAttribute(file, "test", "bar", "wrong", NORMAL);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForNullArgument() {
    try {
      service.setAttribute(file, "test:bar", null, NORMAL);
      fail();
    } catch (NullPointerException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForAttributeThatIsNotSettable() {
    try {
      service.setAttribute(file, "test:foo", "world", NORMAL);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:foo")).isNull();
  }

  @Test
  public void testSetAttribute_onCreate_failsForAttributeThatIsNotSettableOnCreate() {
    try {
      service.setAttribute(file, "test", "foo", "world", CREATE);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:foo")).isNull();

    try {
      service.setAttribute(file, "test", "bar", 5L, CREATE);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testGetFileAttributeView() throws IOException {
    FileProvider fileProvider = FileProvider.ofFile(file);

    ASSERT.that(service.getFileAttributeView(fileProvider, TestAttributeView.class))
        .isNotNull();
    ASSERT.that(service.getFileAttributeView(fileProvider, BasicFileAttributeView.class))
        .isNotNull();

    TestAttributes attrs
        = service.getFileAttributeView(fileProvider, TestAttributeView.class).readAttributes();
    ASSERT.that(attrs.foo()).is("hello");
    ASSERT.that(attrs.bar()).is(0L);
    ASSERT.that(attrs.baz()).is(1);
  }

  @Test
  public void testGetFileAttributeView_isNullForUnsupportedView() {
    FileProvider fileProvider = FileProvider.ofFile(file);
    ASSERT.that(service.getFileAttributeView(fileProvider, PosixFileAttributeView.class))
        .isNull();
  }

  @Test
  public void testReadAttributes_asMap() {
    ImmutableMap<String, Object> map = service.readAttributes(file, "test:foo,bar,baz");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.of(
            "foo", "hello",
            "bar", 0L,
            "baz", 1));

    FileTime time = (FileTime) file.getAttribute("basic:creationTime");

    map = service.readAttributes(file, "test:*");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.<String, Object>builder()
            .put("foo", "hello")
            .put("bar", 0L)
            .put("baz", 1)
            .put("fileKey", 0L)
            .put("isDirectory", false)
            .put("isRegularFile", false)
            .put("isSymbolicLink", false)
            .put("isOther", true)
            .put("size", 0L)
            .put("lastModifiedTime", time)
            .put("lastAccessTime", time)
            .put("creationTime", time)
            .build());

    map = service.readAttributes(file, "basic:*");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.<String, Object>builder()
            .put("fileKey", 0L)
            .put("isDirectory", false)
            .put("isRegularFile", false)
            .put("isSymbolicLink", false)
            .put("isOther", true)
            .put("size", 0L)
            .put("lastModifiedTime", time)
            .put("lastAccessTime", time)
            .put("creationTime", time)
            .build());
  }

  @Test
  public void testReadAttributes_asMap_failsForInvalidAttributes() {
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
    BasicFileAttributes basicAttrs = service.readAttributes(file, BasicFileAttributes.class);
    ASSERT.that(basicAttrs.fileKey()).is(0L);
    ASSERT.that(basicAttrs.isDirectory()).isFalse();

    TestAttributes testAttrs = service.readAttributes(file, TestAttributes.class);
    ASSERT.that(testAttrs.foo()).is("hello");
    ASSERT.that(testAttrs.bar()).is(0L);
    ASSERT.that(testAttrs.baz()).is(1);

    file.setAttribute("test:baz", 100);
    ASSERT.that(service.readAttributes(file, TestAttributes.class).baz()).is(100);
  }

  @Test
  public void testReadAttributes_failsForUnsupportedAttributesType() {
    try {
      service.readAttributes(file, PosixFileAttributes.class);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testIllegalAttributeFormats() {
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
