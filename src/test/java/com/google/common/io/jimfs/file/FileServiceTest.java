package com.google.common.io.jimfs.file;

import static com.google.common.io.jimfs.attribute.UserLookupService.createUserPrincipal;
import static org.truth0.Truth.ASSERT;

import com.google.common.io.jimfs.FakeJimfsFileSystem;
import com.google.common.io.jimfs.attribute.AttributeService;
import com.google.common.io.jimfs.attribute.BasicAttributeProvider;
import com.google.common.io.jimfs.attribute.OwnerAttributeProvider;
import com.google.common.io.jimfs.path.JimfsPath;
import com.google.common.io.jimfs.testing.BasicFileAttribute;

import org.junit.Before;
import org.junit.Test;

import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;

/**
 * Tests for {@link FileService}.
 *
 * @author Colin Decker
 */
public class FileServiceTest {

  private static final UserPrincipal USER = createUserPrincipal("user");
  private static final UserPrincipal FOO = createUserPrincipal("foo");

  private FileService fileService;

  @Before
  public void setUp() {
    AttributeService attributeService =
        new AttributeService(new BasicAttributeProvider(), new OwnerAttributeProvider(USER));
    fileService = new FileService(attributeService);
  }

  @Test
  public void testCreateFiles_basic() {
    File file = fileService.createDirectory();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = fileService.createRegularFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = fileService.createSymbolicLink(JimfsPath.empty(new FakeJimfsFileSystem()));
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);
  }

  @Test
  public void testCreateFiles_withCallback() {
    File file = fileService.directoryCallback().createFile();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = fileService.regularFileCallback().createFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);

    file = fileService.symbolicLinkCallback(JimfsPath.empty(new FakeJimfsFileSystem()))
        .createFile();
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(USER);
  }

  @Test
  public void testCreateFiles_basic_withInitialAttribute() {
    FileAttribute<?> owner = new BasicFileAttribute<>("owner:owner", FOO);
    File file = fileService.createDirectory(owner);
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = fileService.createRegularFile(owner);
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = fileService.createSymbolicLink(JimfsPath.empty(new FakeJimfsFileSystem()), owner);
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);
  }

  @Test
  public void testCreateFiles_withCallback_withInitialAttribute() {
    FileAttribute<?> owner = new BasicFileAttribute<>("owner:owner", FOO);
    File file = fileService.directoryCallback(owner).createFile();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = fileService.regularFileCallback(owner).createFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);

    file = fileService.symbolicLinkCallback(JimfsPath.empty(new FakeJimfsFileSystem()), owner)
        .createFile();
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.getAttribute("basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("owner:owner")).is(FOO);
  }
}
