package com.google.common.io.jimfs.file;

import static com.google.common.io.jimfs.attribute.UserLookupService.createUserPrincipal;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.jimfs.FakeJimfsFileSystem;
import com.google.common.io.jimfs.attribute.BasicAttributeProvider;
import com.google.common.io.jimfs.attribute.OwnerAttributeProvider;
import com.google.common.io.jimfs.attribute.TestAttributeProvider;
import com.google.common.io.jimfs.attribute.TestAttributeView;
import com.google.common.io.jimfs.attribute.TestAttributes;
import com.google.common.io.jimfs.path.JimfsPath;
import com.google.common.io.jimfs.testing.BasicFileAttribute;

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
 * Tests for {@link FileService}.
 *
 * @author Colin Decker
 */
public class FileServiceTest {

  private static final UserPrincipal USER = createUserPrincipal("user");
  private static final UserPrincipal FOO = createUserPrincipal("foo");

  private FileService service;

  @Before
  public void setUp() {
    service = new FileService(new BasicAttributeProvider(),
        new TestAttributeProvider(), new OwnerAttributeProvider(USER));
  }

  @Test
  public void testCreateFiles_basic() {
    File file = service.createDirectory();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = service.createRegularFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = service.createSymbolicLink(JimfsPath.empty(new FakeJimfsFileSystem()));
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);
  }

  @Test
  public void testCreateFiles_withCallback() {
    File file = service.directoryCreator().createFile();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = service.regularFileCreator().createFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = service.symbolicLinkCreator(JimfsPath.empty(new FakeJimfsFileSystem()))
        .createFile();
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);
  }

  @Test
  public void testCreateFiles_basic_withInitialAttribute() {
    FileAttribute<?> owner = new BasicFileAttribute<>("owner:owner", FOO);
    File file = service.createDirectory(owner);
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = service.createRegularFile(owner);
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = service.createSymbolicLink(JimfsPath.empty(new FakeJimfsFileSystem()), owner);
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);
  }

  @Test
  public void testCreateFiles_withCallback_withInitialAttribute() {
    FileAttribute<?> owner = new BasicFileAttribute<>("owner:owner", FOO);
    File file = service.directoryCreator(owner).createFile();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = service.regularFileCreator(owner).createFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = service.symbolicLinkCreator(JimfsPath.empty(new FakeJimfsFileSystem()), owner)
        .createFile();
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);
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
    File file = service.createRegularFile();

    // setInitialAttributes() is called during setUp()
    ASSERT.that(ImmutableSet.copyOf(file.getAttributeKeys())).is(
        ImmutableSet.of(
            "basic:lastModifiedTime",
            "basic:creationTime",
            "basic:lastAccessTime",
            "test:bar",
            "test:baz",
            "owner:owner"));

    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("test:bar")).is(0L);
    ASSERT.that(file.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testGetAttribute() {
    File file = service.createRegularFile();
    ASSERT.that(service.getAttribute(file, "test:foo")).is("hello");
    ASSERT.that(service.getAttribute(file, "test", "foo")).is("hello");
    ASSERT.that(service.getAttribute(file, "basic:isRegularFile")).is(true);
    ASSERT.that(service.getAttribute(file, "isDirectory")).is(false);
    ASSERT.that(service.getAttribute(file, "test:baz")).is(1);
  }

  @Test
  public void testGetAttribute_fromInheritedProvider() {
    File file = service.createRegularFile();
    ASSERT.that(service.getAttribute(file, "test:isRegularFile")).is(true);
    ASSERT.that(service.getAttribute(file, "test", "fileKey")).is(0L);
  }

  @Test
  public void testGetAttribute_failsForAttributesNotDefinedByProvider() {
    File file = service.createRegularFile();
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
    File file = service.createRegularFile();
    service.setAttribute(file, "test:bar", 10L);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);

    service.setAttribute(file, "test", "baz", 100);
    ASSERT.that(file.getAttribute("test:baz")).is(100);
  }

  @Test
  public void testSetAttribute_forInheritedProvider() {
    File file = service.createRegularFile();
    service.setAttribute(file, "test:lastModifiedTime", FileTime.fromMillis(0L));
    ASSERT.that(file.getAttribute("test:lastModifiedTime")).isNull();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).is(FileTime.fromMillis(0L));
  }

  @Test
  public void testSetAttribute_withAlternateAcceptedType() {
    File file = service.createRegularFile();
    service.setAttribute(file, "test:bar", 10f);
    ASSERT.that(file.getAttribute("test:bar")).is(10L);

    service.setAttribute(file, "test:bar", BigInteger.valueOf(123L));
    ASSERT.that(file.getAttribute("test:bar")).is(123L);
  }

  @Test
  public void testSetAttribute_onCreate() {
    File file = service.createRegularFile();
    file = service.createRegularFile(new BasicFileAttribute<>("test:baz", 123));
    ASSERT.that(file.getAttribute("test:baz")).is(123);
  }

  @Test
  public void testSetAttribute_failsForAttributesNotDefinedByProvider() {
    File file = service.createRegularFile();
    try {
      service.setAttribute(file, "test:blah", "blah");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      service.setAttribute(file, "basic", "baz", 5);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:baz")).is(1);
  }

  @Test
  public void testSetAttribute_failsForArgumentThatIsNotOfCorrectType() {
    File file = service.createRegularFile();
    try {
      service.setAttribute(file, "test", "bar", "wrong");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForNullArgument() {
    File file = service.createRegularFile();
    try {
      service.setAttribute(file, "test:bar", null);
      fail();
    } catch (NullPointerException expected) {
    }

    ASSERT.that(file.getAttribute("test:bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForAttributeThatIsNotSettable() {
    File file = service.createRegularFile();
    try {
      service.setAttribute(file, "test:foo", "world");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test:foo")).isNull();
  }

  @Test
  public void testSetAttribute_onCreate_failsForAttributeThatIsNotSettableOnCreate() {
    try {
      service.createRegularFile(new BasicFileAttribute<>("test:foo", "world"));
      fail();
    } catch (IllegalArgumentException expected) {
      // IAE because test:foo just can't be set
    }

    try {
      service.createRegularFile(new BasicFileAttribute<>("test:bar", 5L));
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testGetFileAttributeView() throws IOException {
    File file = service.createRegularFile();
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
    File file = service.createRegularFile();
    FileProvider fileProvider = FileProvider.ofFile(file);
    ASSERT.that(service.getFileAttributeView(fileProvider, PosixFileAttributeView.class))
        .isNull();
  }

  @Test
  public void testReadAttributes_asMap() {
    File file = service.createRegularFile();
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
            .put("isRegularFile", true)
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
    File file = service.createRegularFile();
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
    File file = service.createRegularFile();
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
    File file = service.createRegularFile();
    try {
      service.readAttributes(file, PosixFileAttributes.class);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testIllegalAttributeFormats() {
    File file = service.createRegularFile();
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
