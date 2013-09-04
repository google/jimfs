package com.google.jimfs.internal.file;

import static com.google.jimfs.testing.TestUtils.fakePath;
import static org.truth0.Truth.ASSERT;

import com.google.common.testing.EqualsTester;
import com.google.jimfs.internal.bytestore.ArrayByteStore;
import com.google.jimfs.internal.path.JimfsPath;
import com.google.jimfs.testing.FakeFileContent;

import org.junit.Test;

/**
 * Tests for {@link File}.
 *
 * @author Colin Decker
 */
public class FileTest {

  @Test
  public void testFileBasics() {
    FileContent content = new FakeFileContent();
    File file = new File(0L, content);

    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.content()).is(content);
    ASSERT.that(file.links()).is(0);
  }

  @Test
  public void testDirectory() {
    File file = new File(0L, new DirectoryTable());
    ASSERT.that(file.isDirectory()).isTrue();
    ASSERT.that(file.isRegularFile()).isFalse();
    ASSERT.that(file.isSymbolicLink()).isFalse();
    ASSERT.that(file.content()).isA(DirectoryTable.class);
  }

  @Test
  public void testRegularFile() {
    File file = new File(0L, new ArrayByteStore());
    ASSERT.that(file.isDirectory()).isFalse();
    ASSERT.that(file.isRegularFile()).isTrue();
    ASSERT.that(file.isSymbolicLink()).isFalse();
    ASSERT.that(file.content()).isA(ArrayByteStore.class);
  }

  @Test
  public void testSymbolicLink() {
    File file = new File(0L, fakePath());
    ASSERT.that(file.isDirectory()).isFalse();
    ASSERT.that(file.isRegularFile()).isFalse();
    ASSERT.that(file.isSymbolicLink()).isTrue();
    ASSERT.that(file.content()).isA(JimfsPath.class);
  }

  @Test
  public void testRootDirectory() {
    DirectoryTable table = new DirectoryTable();
    File file = new File(0L, table);
    table.linkParent(file);
    table.linkSelf(file);

    ASSERT.that(file.isRootDirectory()).isTrue();

    table.unlinkParent();
    table.linkParent(new File(1L, new DirectoryTable()));

    ASSERT.that(file.isRegularFile()).isFalse();
  }

  @Test
  public void testLinkAndUnlink() {
    File file = new File(0L, new FakeFileContent());
    ASSERT.that(file.links()).is(0);

    file.linked();
    ASSERT.that(file.links()).is(1);

    file.linked();
    ASSERT.that(file.links()).is(2);

    file.unlinked();
    ASSERT.that(file.links()).is(1);

    file.unlinked();
    ASSERT.that(file.links()).is(0);
  }

  @Test
  public void testAttributes() {
    // these methods are basically just thin wrappers around a map, so no need to test too
    // thoroughly

    File file = new File(0L, new FakeFileContent());

    ASSERT.that(file.getAttributeKeys()).isEmpty();
    ASSERT.that(file.getAttribute("foo:foo")).isNull();

    file.deleteAttribute("foo:foo"); // doesn't throw

    file.setAttribute("foo:foo", "foo");

    ASSERT.that(file.getAttributeKeys()).iteratesAs("foo:foo");
    ASSERT.that(file.getAttribute("foo:foo")).is("foo");

    file.deleteAttribute("foo:foo");

    ASSERT.that(file.getAttributeKeys()).isEmpty();
    ASSERT.that(file.getAttribute("foo:foo")).isNull();
  }

  @Test
  public void testEquality() {
    File file1 = new File(0L, new FakeFileContent());
    File file2 = new File(0L, new DirectoryTable());
    File file3 = new File(1L, new ArrayByteStore());
    File file4 = new File(1L, new FakeFileContent());

    new EqualsTester()
        .addEqualityGroup(file1, file2)
        .addEqualityGroup(file3, file4)
        .testEquals();
  }
}
