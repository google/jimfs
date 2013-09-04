package com.google.jimfs.internal;

import static com.google.jimfs.internal.attribute.UserLookupService.createUserPrincipal;
import static com.google.jimfs.testing.TestUtils.fakePath;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.internal.attribute.BasicAttributeProvider;
import com.google.jimfs.internal.attribute.OwnerAttributeProvider;
import com.google.jimfs.internal.attribute.TestAttributeProvider;
import com.google.jimfs.internal.attribute.TestAttributeView;
import com.google.jimfs.internal.attribute.TestAttributes;
import com.google.jimfs.internal.file.File;
import com.google.jimfs.internal.file.FileProvider;
import com.google.jimfs.testing.BasicFileAttribute;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;

/**
 * Tests for {@link JimfsFileStore}.
 *
 * @author Colin Decker
 */
public class JimfsFileStoreTest {

  private static final UserPrincipal USER = createUserPrincipal("user");
  private static final UserPrincipal FOO = createUserPrincipal("foo");

  private JimfsFileStore store;

  @Before
  public void setUp() {
    store = new JimfsFileStore("foo",
        new BasicAttributeProvider(),
        new TestAttributeProvider(),
        new OwnerAttributeProvider(USER));
  }

  @Test
  public void testCreateFiles_basic() {
    File file = store.createDirectory();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = store.createRegularFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = store.createSymbolicLink(fakePath());
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);
  }

  @Test
  public void testCreateFiles_withSupplier() {
    File file = store.directorySupplier().get();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = store.regularFileSupplier().get();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = store.symbolicLinkSupplier(fakePath()).get();
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);
  }

  @Test
  public void testCreateFiles_basic_withInitialAttribute() {
    FileAttribute<?> owner = new BasicFileAttribute<>("owner:owner", FOO);
    File file = store.createDirectory(owner);
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = store.createRegularFile(owner);
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = store.createSymbolicLink(fakePath(), owner);
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);
  }

  @Test
  public void testCreateFiles_withSupplier_withInitialAttribute() {
    FileAttribute<?> owner = new BasicFileAttribute<>("owner:owner", FOO);
    File file = store.directorySupplier(owner).get();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = store.regularFileSupplier(owner).get();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = store.symbolicLinkSupplier(fakePath(), owner).get();
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);
  }

  @Test
  public void testSupportedFileAttributeViews() {
    ASSERT.that(store.supportedFileAttributeViews())
        .is(ImmutableSet.of("basic", "test", "owner"));
  }

  @Test
  public void testSupportsFileAttributeView() {
    ASSERT.that(store.supportsFileAttributeView(BasicFileAttributeView.class)).isTrue();
    ASSERT.that(store.supportsFileAttributeView(TestAttributeView.class)).isTrue();
    ASSERT.that(store.supportsFileAttributeView(PosixFileAttributeView.class)).isFalse();
  }

  @Test
  public void testSetInitialAttributes() {
    File file = store.createRegularFile();

    // setInitialAttributes() is called during setUp()
    ASSERT.that(ImmutableSet.copyOf(file.getAttributeKeys())).is(
        ImmutableSet.of(
            "test:bar",
            "test:baz",
            "owner:owner"));

    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("test:bar")).is(0L);
    ASSERT.that(file.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testGetAttribute() {
    File file = store.createRegularFile();
    ASSERT.that(store.getAttribute(file, "test:foo")).is("hello");
    ASSERT.that(store.getAttribute(file, "test", "foo")).is("hello");
    ASSERT.that(store.getAttribute(file, "basic:isRegularFile")).is(true);
    ASSERT.that(store.getAttribute(file, "isDirectory")).is(false);
    ASSERT.that(store.getAttribute(file, "test:baz")).is(1);
  }

  @Test
  public void testGetAttribute_fromInheritedProvider() {
    File file = store.createRegularFile();
    ASSERT.that(store.getAttribute(file, "test:isRegularFile")).is(true);
    ASSERT.that(store.getAttribute(file, "test", "fileKey")).is(0L);
  }

  @Test
  public void testGetAttribute_failsForAttributesNotDefinedByProvider() {
    File file = store.createRegularFile();
    try {
      store.getAttribute(file, "test:blah");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      store.getAttribute(file, "basic", "baz");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testSetAttribute() {
    File file = store.createRegularFile();
    store.setAttribute(file, "test:bar", 10L);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);

    store.setAttribute(file, "test", "baz", 100);
    ASSERT.that(file.getAttribute("test:baz")).is(100);
  }

  @Test
  public void testSetAttribute_forInheritedProvider() {
    File file = store.createRegularFile();
    store.setAttribute(file, "test:lastModifiedTime", FileTime.fromMillis(0L));
    ASSERT.that(file.getAttribute("test:lastModifiedTime")).isNull();
    ASSERT.that(store.getAttribute(file, "basic:lastModifiedTime")).is(FileTime.fromMillis(0L));
  }

  @Test
  public void testSetAttribute_withAlternateAcceptedType() {
    File file = store.createRegularFile();
    store.setAttribute(file, "test:bar", 10f);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);

    store.setAttribute(file, "test:bar", BigInteger.valueOf(123L));
    ASSERT.that(file.getAttribute("test:bar")).is(123L);
  }

  @Test
  public void testSetAttribute_onCreate() {
    File file = store.createRegularFile(new BasicFileAttribute<>("test:baz", 123));
    ASSERT.that(file.getAttribute("test:baz")).is(123);
  }

  @Test
  public void testSetAttribute_failsForAttributesNotDefinedByProvider() {
    File file = store.createRegularFile();
    try {
      store.setAttribute(file, "test:blah", "blah");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      store.setAttribute(file, "basic", "baz", 5);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testSetAttribute_failsForArgumentThatIsNotOfCorrectType() {
    File file = store.createRegularFile();
    try {
      store.setAttribute(file, "test", "bar", "wrong");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForNullArgument() {
    File file = store.createRegularFile();
    try {
      store.setAttribute(file, "test:bar", null);
      fail();
    } catch (NullPointerException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForAttributeThatIsNotSettable() {
    File file = store.createRegularFile();
    try {
      store.setAttribute(file, "test:foo", "world");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:foo")).isNull();
  }

  @Test
  public void testSetAttribute_onCreate_failsForAttributeThatIsNotSettableOnCreate() {
    try {
      store.createRegularFile(new BasicFileAttribute<>("test:foo", "world"));
      fail();
    } catch (IllegalArgumentException expected) {
      // IAE because test:foo just can't be set
    }

    try {
      store.createRegularFile(new BasicFileAttribute<>("test:bar", 5L));
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testGetFileAttributeView() throws IOException {
    File file = store.createRegularFile();
    FileProvider fileProvider = FileProvider.ofFile(file);

    ASSERT.that(store.getFileAttributeView(fileProvider, TestAttributeView.class))
        .isNotNull();
    ASSERT.that(store.getFileAttributeView(fileProvider, BasicFileAttributeView.class))
        .isNotNull();

    TestAttributes attrs
        = store.getFileAttributeView(fileProvider, TestAttributeView.class).readAttributes();
    ASSERT.that(attrs.foo()).is("hello");
    ASSERT.that(attrs.bar()).is(0L);
    ASSERT.that(attrs.baz()).is(1);
  }

  @Test
  public void testGetFileAttributeView_isNullForUnsupportedView() {
    File file = store.createRegularFile();
    FileProvider fileProvider = FileProvider.ofFile(file);
    ASSERT.that(store.getFileAttributeView(fileProvider, PosixFileAttributeView.class))
        .isNull();
  }

  @Test
  public void testReadAttributes_asMap() {
    File file = store.createRegularFile();
    ImmutableMap<String, Object> map = store.readAttributes(file, "test:foo,bar,baz");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.of(
            "foo", "hello",
            "bar", 0L,
            "baz", 1));

    FileTime time = store.getAttribute(file, "basic:creationTime");

    map = store.readAttributes(file, "test:*");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.<String, Object>builder()
            .put("foo", "hello")
            .put("bar", 0L)
            .put("baz", 1)
            .put("fileKey", 0L)
            .put("isDirectory", false)
            .put("isRegularFile", true)
            .put("isSymbolicLink", false)
            .put("isOther", false)
            .put("size", 0L)
            .put("lastModifiedTime", time)
            .put("lastAccessTime", time)
            .put("creationTime", time)
            .build());

    map = store.readAttributes(file, "basic:*");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.<String, Object>builder()
            .put("fileKey", 0L)
            .put("isDirectory", false)
            .put("isRegularFile", true)
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
    File file = store.createRegularFile();
    try {
      store.readAttributes(file, "basic:fileKey,isOther,*,creationTime");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("invalid attributes");
    }

    try {
      store.readAttributes(file, "basic:fileKey,isOther,foo");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("invalid attribute");
    }
  }

  @Test
  public void testReadAttributes_asObject() {
    File file = store.createRegularFile();
    BasicFileAttributes basicAttrs = store.readAttributes(file, BasicFileAttributes.class);
    ASSERT.that(basicAttrs.fileKey()).is(0L);
    ASSERT.that(basicAttrs.isDirectory()).isFalse();

    TestAttributes testAttrs = store.readAttributes(file, TestAttributes.class);
    ASSERT.that(testAttrs.foo()).is("hello");
    ASSERT.that(testAttrs.bar()).is(0L);
    ASSERT.that(testAttrs.baz()).is(1);

    file.setAttribute("test:baz", 100);
    ASSERT.that(store.readAttributes(file, TestAttributes.class).baz()).is(100);
  }

  @Test
  public void testReadAttributes_failsForUnsupportedAttributesType() {
    File file = store.createRegularFile();
    try {
      store.readAttributes(file, PosixFileAttributes.class);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testIllegalAttributeFormats() {
    File file = store.createRegularFile();
    try {
      store.getAttribute(file, ":bar");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      store.getAttribute(file, "test:");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      store.getAttribute(file, "basic:test:isDirectory");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      store.getAttribute(file, "basic:fileKey,size");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("single attribute");
    }
  }
}
