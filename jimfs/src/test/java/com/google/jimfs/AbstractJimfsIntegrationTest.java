package com.google.jimfs;

import static com.google.jimfs.testing.PathSubject.paths;
import static org.truth0.Truth.ASSERT;

import com.google.jimfs.testing.PathSubject;

import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/**
 * @author Colin Decker
 */
public abstract class AbstractJimfsIntegrationTest {

  protected FileSystem fs;

  @Before
  public void setUp() throws IOException {
    fs = createFileSystem();
  }

  @After
  public void tearDown() throws IOException {
    fs.close();
  }

  /**
   * Creates the file system to use in the tests.
   */
  protected abstract FileSystem createFileSystem();

  // helpers

  protected Path path(String first, String... more) {
    return fs.getPath(first, more);
  }

  protected Object getFileKey(String path, LinkOption... options) throws IOException {
    return Files.getAttribute(path(path), "fileKey", options);
  }

  protected PathSubject assertThat(String path, LinkOption... options) {
    return assertThat(path(path), options);
  }

  protected static PathSubject assertThat(Path path, LinkOption... options) {
    PathSubject subject = ASSERT.about(paths()).that(path);
    if (options.length != 0) {
      subject = subject.noFollowLinks();
    }
    return subject;
  }

  /**
   * Tester for testing changes in file times.
   */
  protected static final class FileTimeTester {

    private final Path path;

    private FileTime accessTime;
    private FileTime modifiedTime;

    FileTimeTester(Path path) throws IOException {
      this.path = path;

      BasicFileAttributes attrs = attrs();
      accessTime = attrs.lastAccessTime();
      modifiedTime = attrs.lastModifiedTime();
    }

    private BasicFileAttributes attrs() throws IOException {
      return Files.readAttributes(path, BasicFileAttributes.class);
    }

    public void assertAccessTimeChanged() throws IOException {
      FileTime t = attrs().lastAccessTime();
      ASSERT.that(t).isNotEqualTo(accessTime);
      accessTime = t;
    }

    public void assertAccessTimeDidNotChange() throws IOException {
      FileTime t = attrs().lastAccessTime();
      ASSERT.that(t).isEqualTo(accessTime);
    }

    public void assertModifiedTimeChanged() throws IOException {
      FileTime t = attrs().lastModifiedTime();
      ASSERT.that(t).isNotEqualTo(modifiedTime);
      modifiedTime = t;
    }

    public void assertModifiedTimeDidNotChange() throws IOException {
      FileTime t = attrs().lastModifiedTime();
      ASSERT.that(t).isEqualTo(modifiedTime);
    }
  }
}
