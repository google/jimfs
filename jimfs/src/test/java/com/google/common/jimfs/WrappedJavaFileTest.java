package com.google.common.jimfs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.*;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Test for {@link com.google.common.jimfs.WrappedJavaFile} which enables {@link JimfsPath#toFile()}
 *
 * @author Morgen Peschke
 */
@RunWith(JUnit4.class)
public class WrappedJavaFileTest {
    private FileSystem createFileSystem() {
        return Jimfs.newFileSystem();
    }

    private void setContents(Path path, String contents) throws IOException {
        try(BufferedWriter br = Files.newBufferedWriter(path, Charset.defaultCharset())) {
            br.write(contents);
        }
    }

    private String getContents(Path path) throws IOException {
        StringBuilder builder = new StringBuilder();
        try(BufferedReader br = Files.newBufferedReader(path, Charset.defaultCharset())) {
            int value;
            do {
                value = br.read();
                if (value != -1) {
                    builder.append((char)value);
                }
            } while(value != -1);
        }
        return builder.toString();
    }

    /* -- Path-component accessors -- */

    @Test
    public void testGetName() {
        FileSystem fs = createFileSystem();
        assertThat(fs.getPath("/foo/bar/baz").toFile().getName(), is("baz"));
        assertThat(fs.getPath("/foo/bar/").toFile().getName(), is("bar"));
        assertThat(fs.getPath("/foo").toFile().getName(), is("foo"));
        assertThat(fs.getPath("/").toFile().getName(), is(""));

        assertThat(fs.getPath("foo/bar/baz").toFile().getName(), is("baz"));
        assertThat(fs.getPath("foo/bar/").toFile().getName(), is("bar"));
    }

    @Test
    public void testGetParent() {
        FileSystem fs = createFileSystem();
        Path root = fs.getPath("/");
        Path parent = root.resolve("/foo");
        Path source = parent.resolve("bar");
        java.io.File asFile = source.toFile();
        java.io.File parentFile = parent.toFile();
        java.io.File rootFile = root.toFile();
        java.io.File relativeFile = fs.getPath("relative").toFile();

        assertThat(asFile.getParentFile(), is(parentFile));
        assertThat(asFile.getParent(), is("/foo"));

        assertThat(parentFile.getParentFile(), is(rootFile));
        assertThat(parentFile.getParent(), is("/"));

        assertNull(rootFile.getParentFile());
        assertNull(rootFile.getParent());

        assertNull(relativeFile.getParentFile());
        assertNull(relativeFile.getParent());
    }

    @Test
    public void testGetPath() {
        FileSystem fs = createFileSystem();
        assertThat(fs.getPath("/foo/bar/baz").toFile().getPath(), is("/foo/bar/baz"));
        assertThat(fs.getPath("/foo/bar/").toFile().getPath(), is("/foo/bar"));
        assertThat(fs.getPath("/foo").toFile().getPath(), is("/foo"));
        assertThat(fs.getPath("/").toFile().getPath(), is("/"));

        assertThat(fs.getPath("foo/bar/baz").toFile().getPath(), is("foo/bar/baz"));
        assertThat(fs.getPath("foo/bar/").toFile().getPath(), is("foo/bar"));
    }

    /* -- Path operations -- */

    @Test
    public void testIsAbsolute() {
        FileSystem fs = createFileSystem();

        assertTrue("path is absolute", fs.getPath("/foo/bar").toFile().isAbsolute());
        assertFalse("path is relative", fs.getPath("foo/bar").toFile().isAbsolute());
    }

    @Test
    public void testGetAbsolutePath() {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("foo/bar");
        java.io.File asFile = source.toFile();

        assertThat(asFile.getAbsolutePath(), is("/work/foo/bar"));
    }

    @Test
    public void testGetAbsoluteFile() {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("foo/bar");
        java.io.File asFile = source.toFile();
        Path cwd = fs.getPath("/work");

        assertThat(asFile.getAbsoluteFile(), is(cwd.resolve(source).toFile()));
    }

    @Test
    public void testGetCanonicalPath() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo/bar/../baz");
        java.io.File asFile = source.toFile();

        try {
            String result = asFile.getCanonicalPath();
            fail("Should have thrown an exception, actually returned: " + result);
        } catch (IOException ex) {
            // This is the expected case
        }

        Files.createDirectories(fs.getPath("/foo/bar"));
        Files.createFile(fs.getPath("/foo/baz"));

        assertThat(asFile.getCanonicalPath(), is("/foo/baz"));
    }

    @Test
    public void testGetCanonicalFile() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo/bar/../baz");
        java.io.File asFile = source.toFile();

        try {
            java.io.File result = asFile.getCanonicalFile();
            fail("Should have thrown an exception, actually returned: " + result);
        } catch (IOException ex) {
            // This is the expected case
        }

        Path expected = fs.getPath("/foo/baz");
        Files.createDirectories(fs.getPath("/foo/bar"));
        Files.createFile(expected);

        assertThat(asFile.getCanonicalFile(), is(expected.toFile()));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testToURL() throws MalformedURLException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo/bar/baz");
        java.io.File asFile = source.toFile();

        assertThat(asFile.toURL(), is(source.toUri().toURL()));
    }

    @Test
    public void testToURI() {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo/bar/baz");
        java.io.File asFile = source.toFile();

        assertThat(asFile.toURI(), is(source.toUri()));
    }

    /* -- Attribute accessors -- */

    @Test
    public void testCanRead_file() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse("can't be read, as it doesn't exist", asFile.canRead());

        Files.createFile(source);

        assertTrue(asFile.canRead());
    }

    @Test
    public void testCanRead_dir() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse("can't be read, as it doesn't exist", asFile.canRead());

        Files.createDirectory(source);

        assertTrue(asFile.canRead());
    }

    @Test
    public void testCanWrite_file() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse("can't be write, as it doesn't exist", asFile.canWrite());

        Files.createFile(source);

        assertTrue(asFile.canWrite());
    }

    @Test
    public void testCanWrite_dir() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse("can't be write, as it doesn't exist", asFile.canWrite());

        Files.createDirectory(source);

        assertTrue(asFile.canWrite());
    }

    @Test
    public void testExists() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse(asFile.exists());

        Files.createFile(source);

        assertTrue(asFile.exists());
    }

    @Test
    public void testIsDirectory() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse(asFile.isDirectory());

        Files.createFile(source);

        assertFalse(asFile.isDirectory());

        Files.delete(source);
        Files.createDirectory(source);

        assertTrue(asFile.isDirectory());
    }

    @Test
    public void testIsFile() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse(asFile.isFile());

        Files.createDirectory(source);

        assertFalse(asFile.isFile());

        Files.delete(source);
        Files.createFile(source);

        assertTrue(asFile.isFile());
    }

    @Test
    public void testIsHidden_not_hidden() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        Files.deleteIfExists(source);
        Files.createFile(source);
        assertFalse("file shouldn't report as hidden", asFile.isHidden());
    }

    @Test
    public void testIsHidden_hidden_file() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/.foo");
        java.io.File asFile = source.toFile();

        Files.deleteIfExists(source);
        Files.createFile(source);
        assertTrue("file should report as hidden", asFile.isHidden());
    }

    @Test
    public void testLastModified() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertThat(asFile.lastModified(), is(0L));

        Long lower = System.currentTimeMillis();
        Files.createFile(source);
        Long upper = System.currentTimeMillis();

        Long lastModified = asFile.lastModified();

        assertTrue(
                "Expected " + lastModified + " <= " + upper,
                lastModified <= upper);

        assertTrue(
                "Expected" + lastModified + " >= " + lower,
                lastModified >= lower);
    }

    @Test
    public void testLength() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertThat("non-existent files have no size", asFile.length(), is(0L));

        Files.createFile(source);

        assertThat(asFile.length(), is(0L));

        setContents(
                source,
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                        "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.");

        assertThat(asFile.length(), is(231L));
    }

    /* -- File operations -- */

    @Test
    public void testCreateNewFile() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertTrue(asFile.createNewFile());
        assertTrue("path should report as existing", Files.exists(source));
        assertTrue("file should report as existing", asFile.exists());
        assertFalse("file should already exist", asFile.createNewFile());

        assertTrue("should be a file", asFile.isFile());
        assertFalse("should not be a directory", asFile.isDirectory());
    }

    @Test
    public void testDelete() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertTrue(asFile.createNewFile());
        assertTrue(asFile.delete());
        assertFalse(Files.exists(source));
        assertFalse(asFile.exists());
        assertFalse("file should already have been deleted", asFile.delete());

        assertTrue(asFile.mkdir());
        assertTrue(asFile.delete());
        assertFalse("path report as deleted", Files.exists(source));
        assertFalse("file should report as deleted", asFile.exists());
        assertFalse("directory should have already been deleted", asFile.delete());
    }

    @Test
    public void testList_no_arguments() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        Files.deleteIfExists(source);
        Files.createDirectory(source);

        assertThat("directory is empty", asFile.list(), is(new String[0]));

        Files.createFile(source.resolve("a"));
        Files.createFile(source.resolve("b"));
        Path child = source.resolve("c");
        Files.createDirectory(child);

        String[] expected = new String[] { "/foo/a", "/foo/b", "/foo/c" };
        assertThat("directory now has children", asFile.list(), is(expected));

        Files.createFile(child.resolve("d"));
        assertThat("should not recurse", asFile.list(), is(expected));
    }

    @Test
    public void testList_with_FileNameFilter() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        final java.io.File asFile = source.toFile();

        Files.deleteIfExists(source);
        Files.createDirectory(source);

        Files.createFile(source.resolve("aa"));
        Files.createFile(source.resolve("bb"));
        Files.createDirectory(source.resolve("ac"));

        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return dir.equals(asFile) && name.startsWith("a");
            }
        };

        String[] expected = new String[] { "/foo/aa", "/foo/ac" };
        assertThat("\"bb\" should be ignored", asFile.list(filter), is(expected));
    }

    @Test
    public void testListFiles_no_arguments() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        Files.deleteIfExists(source);
        Files.createDirectory(source);

        assertThat("directory is empty", asFile.listFiles(), is(new java.io.File[0]));

        Files.createFile(source.resolve("a"));
        Files.createFile(source.resolve("b"));
        Path child = source.resolve("c");
        Files.createDirectory(child);

        java.io.File[] expected = new java.io.File[] {
                source.resolve("a").toFile(),
                source.resolve("b").toFile(),
                source.resolve("c").toFile()
        };
        assertThat("directory now has children", asFile.listFiles(), is(expected));

        Files.createFile(child.resolve("d"));
        assertThat("should not recurse", asFile.listFiles(), is(expected));
    }

    @Test
    public void testListFiles_with_FileFilter() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        final java.io.File asFile = source.toFile();

        Files.deleteIfExists(source);
        Files.createDirectory(source);

        Files.createFile(source.resolve("aa"));
        Files.createFile(source.resolve("bb"));
        Files.createDirectory(source.resolve("ca"));

        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.toString().endsWith("a");
            }
        };

        java.io.File[] expected = new java.io.File[] {
                source.resolve("aa").toFile(),
                source.resolve("ca").toFile()
        };
        assertThat("\"bb\" should be ignored", asFile.listFiles(filter), is(expected));
    }

    @Test
    public void testListFiles_with_FilenameFilter() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        final java.io.File asFile = source.toFile();

        Files.deleteIfExists(source);
        Files.createDirectory(source);

        Files.createFile(source.resolve("aa"));
        Files.createFile(source.resolve("bb"));
        Files.createDirectory(source.resolve("ac"));

        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("a");
            }
        };

        java.io.File[] expected = new java.io.File[] {
                source.resolve("aa").toFile(),
                source.resolve("ac").toFile()
        };
        assertThat("\"bb\" should be ignored", asFile.listFiles(filter), is(expected));
    }

    @Test
    public void testMkDir() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo/bar");
        java.io.File asFile = source.toFile();

        assertFalse("parent directory doesn't exist", asFile.mkdir());

        Files.createDirectory(fs.getPath("/foo"));

        assertTrue(asFile.mkdir());
        assertTrue("path should report as existing", Files.exists(source));
        assertTrue("file should report as existing", asFile.exists());
        assertFalse("directory should already exist", asFile.mkdir());

        assertFalse("should not be a file", asFile.isFile());
        assertTrue("should be a directory", asFile.isDirectory());
    }

    @Test
    public void testMkDirs() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo/bar");
        java.io.File asFile = source.toFile();

        assertFalse("parent directory shouldn't exist", Files.exists(fs.getPath("/foo")));
        assertTrue("mkdirs should succeed without a parent dir", asFile.mkdirs());
        assertTrue(Files.exists(source));

        Path child = source.resolve("baz");
        assertTrue("mkdirs should succeed with a parent dir", child.toFile().mkdirs());
        assertTrue(Files.exists(child));
    }

    @Test
    public void testRenameTo() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        Path dest = fs.getPath("/bar");
        java.io.File sourceFile = source.toFile();
        java.io.File destFile = dest.toFile();

        Files.createFile(source);
        assertTrue("rename should succeed", sourceFile.renameTo(destFile));
        assertFalse("old file should be gone", Files.exists(source));
        assertTrue("new file should exist", Files.exists(dest));
    }

    @Test
    public void testRenameTo_with_overwrite() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        Path dest = fs.getPath("/bar");
        java.io.File sourceFile = source.toFile();
        java.io.File destFile = dest.toFile();

        Files.createFile(source);
        Files.createFile(dest);

        setContents(source, "Hello World");

        assertTrue("rename should succeed", sourceFile.renameTo(destFile));
        assertFalse("old file should be gone", Files.exists(source));
        assertTrue("new file should exist", Files.exists(dest));

        assertThat("new file should have the expected contents", getContents(dest), is("Hello World"));
    }

    @Test
    public void testCanExecute_file() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse("can't be read, as it doesn't exist", asFile.canExecute());

        Files.createFile(source);

        assertTrue(asFile.canExecute());
    }

    @Test
    public void testCanExecute_dir() throws IOException {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertFalse("can't be read, as it doesn't exist", asFile.canExecute());

        Files.createDirectory(source);

        assertTrue(asFile.canExecute());
    }

    /* -- Disk usage -- */

    @Test
    public void testGetTotalSpace() throws IOException {
        Configuration config =
                Configuration
                        .unix()
                        .toBuilder()
                        .setMaxSize(1024L)
                        .setBlockSize(32)
                        .build();
        FileSystem fs = Jimfs.newFileSystem(config);

        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();
        assertThat(asFile.getTotalSpace(), is(1024L));
    }

    @Test
    public void testGetFreeSpace() throws IOException {
        Configuration config =
                Configuration
                        .unix()
                        .toBuilder()
                        .setMaxSize(1024L)
                        .setBlockSize(32)
                        .build();
        FileSystem fs = Jimfs.newFileSystem(config);

        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        String contents = "Hello World";
        Files.createFile(source);
        setContents(source, contents);

        assertThat(asFile.getFreeSpace(), is(1024L - 32L));
    }

    @Test
    public void testGetUsableSpace() throws IOException {
        Configuration config =
                Configuration
                        .unix()
                        .toBuilder()
                        .setMaxSize(1024L)
                        .setBlockSize(50)
                        .build();
        FileSystem fs = Jimfs.newFileSystem(config);

        Path source = fs.getPath("/foo");
        java.io.File asFile = source.toFile();

        assertThat(asFile.getUsableSpace(), is(1000L));
    }

    /* -- Basic infrastructure -- */

    @Test
    public void testCompareTo() {
        FileSystem fs = createFileSystem();
        java.io.File aFile = fs.getPath("/a").toFile();
        java.io.File bFile = fs.getPath("/b").toFile();
        java.io.File aFile2 = fs.getPath("/a").toFile();

        assertThat(aFile.compareTo(aFile2), is(0));
        assertThat(aFile.compareTo(bFile), is(-1));
        assertThat(bFile.compareTo(aFile), is(1));
    }

    @Test
    public void testEquality() {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo/bar/baz");
        java.io.File asFile = source.toFile();

        java.io.File other = fs.getPath(".").toFile();

        // Equality checks
        assertTrue(asFile.equals(source.toFile()));
        assertFalse(asFile.equals(other));
    }

    @Test
    public void testHashCode() {
        FileSystem fs = createFileSystem();
        Path aPath = fs.getPath("/a");
        Path bPath = fs.getPath("/b");
        java.io.File aFile = aPath.toFile();
        java.io.File bFile = bPath.toFile();

        assertThat(aFile.hashCode(), is(aPath.hashCode() + 1));
        assertThat(bFile.hashCode(), is(bPath.hashCode() + 1));
    }

    /* -- Integration with java.nio.file -- */

    @Test
    public void testToPath() {
        FileSystem fs = createFileSystem();
        Path source = fs.getPath("/foo/bar");
        java.io.File asFile = source.toFile();

        assertSame(source, asFile.toPath());
    }
}