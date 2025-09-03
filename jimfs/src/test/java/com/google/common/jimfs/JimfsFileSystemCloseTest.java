/*
 * Copyright 2014 Google Inc.
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for what happens when a file system is closed.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class JimfsFileSystemCloseTest {

  private JimfsFileSystem fs = (JimfsFileSystem) Jimfs.newFileSystem(Configuration.unix());

  @Test
  public void testIsNotOpen() throws IOException {
    assertTrue(fs.isOpen());
    fs.close();
    assertFalse(fs.isOpen());
  }

  @Test
  public void testIsNotAvailableFromProvider() throws IOException {
    URI uri = fs.getUri();
    assertEquals(fs, FileSystems.getFileSystem(uri));

    fs.close();

    assertThrows(FileSystemNotFoundException.class, () -> FileSystems.getFileSystem(uri));
  }

  @Test
  public void testOpenStreamsClosed() throws IOException {
    Path p = fs.getPath("/foo");
    OutputStream out = Files.newOutputStream(p);
    InputStream in = Files.newInputStream(p);

    out.write(1);
    assertEquals(1, in.read());

    fs.close();

    IOException expected = assertThrows(IOException.class, () -> out.write(1));
    assertThat(expected).hasMessageThat().isEqualTo("stream is closed");

    expected = assertThrows(IOException.class, () -> in.read());
    assertThat(expected).hasMessageThat().isEqualTo("stream is closed");
  }

  @Test
  public void testOpenChannelsClosed() throws IOException {
    Path p = fs.getPath("/foo");
    FileChannel fc = FileChannel.open(p, READ, WRITE, CREATE);
    SeekableByteChannel sbc = Files.newByteChannel(p, READ);
    AsynchronousFileChannel afc = AsynchronousFileChannel.open(p, READ, WRITE);

    assertTrue(fc.isOpen());
    assertTrue(sbc.isOpen());
    assertTrue(afc.isOpen());

    fs.close();

    assertFalse(fc.isOpen());
    assertFalse(sbc.isOpen());
    assertFalse(afc.isOpen());

    assertThrows(ClosedChannelException.class, () -> fc.size());

    assertThrows(ClosedChannelException.class, () -> sbc.size());

    assertThrows(ClosedChannelException.class, () -> afc.size());
  }

  @Test
  public void testOpenDirectoryStreamsClosed() throws IOException {
    Path p = fs.getPath("/foo");
    Files.createDirectory(p);

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {

      fs.close();

      assertThrows(ClosedDirectoryStreamException.class, () -> stream.iterator());
    }
  }

  @Test
  public void testOpenWatchServicesClosed() throws IOException {
    WatchService ws1 = fs.newWatchService();
    WatchService ws2 = fs.newWatchService();

    assertNull(ws1.poll());
    assertNull(ws2.poll());

    fs.close();

    assertThrows(ClosedWatchServiceException.class, () -> ws1.poll());

    assertThrows(ClosedWatchServiceException.class, () -> ws2.poll());
  }

  @Test
  public void testPathMethodsThrow() throws IOException {
    Path p = fs.getPath("/foo");
    Files.createDirectory(p);

    WatchService ws = fs.newWatchService();

    fs.close();

    assertThrows(
        ClosedWatchServiceException.class,
        () -> p.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY));

    assertThrows(ClosedFileSystemException.class, () -> p.toRealPath());

    // While technically (according to the FileSystem.close() spec) all methods on Path should
    // probably throw, we only throw for methods that access the file system itself in some way...
    // path manipulation methods seem totally harmless to keep working, and I don't see any need to
    // add the overhead of checking that the file system is open for each of those method calls.
  }

  @Test
  public void testOpenFileAttributeViewsThrow() throws IOException {
    Path p = fs.getPath("/foo");
    Files.createFile(p);

    BasicFileAttributeView view = Files.getFileAttributeView(p, BasicFileAttributeView.class);

    fs.close();

    assertThrows(ClosedFileSystemException.class, () -> view.readAttributes());

    assertThrows(ClosedFileSystemException.class, () -> view.setTimes(null, null, null));
  }

  @Test
  public void testFileSystemMethodsThrow() throws IOException {
    fs.close();

    assertThrows(ClosedFileSystemException.class, () -> fs.getPath("/foo"));

    assertThrows(ClosedFileSystemException.class, () -> fs.getRootDirectories());

    assertThrows(ClosedFileSystemException.class, () -> fs.getFileStores());

    assertThrows(ClosedFileSystemException.class, () -> fs.getPathMatcher("glob:*.java"));

    assertThrows(ClosedFileSystemException.class, () -> fs.getUserPrincipalLookupService());

    assertThrows(ClosedFileSystemException.class, () -> fs.newWatchService());

    assertThrows(ClosedFileSystemException.class, () -> fs.supportedFileAttributeViews());
  }

  @Test
  public void testFilesMethodsThrow() throws IOException {
    Path file = fs.getPath("/file");
    Path dir = fs.getPath("/dir");
    Path nothing = fs.getPath("/nothing");

    Files.createDirectory(dir);
    Files.createFile(file);

    fs.close();

    // not exhaustive, but should cover every major type of functionality accessible through Files
    // TODO(cgdecker): reflectively invoke all methods with default arguments?

    assertThrows(ClosedFileSystemException.class, () -> Files.delete(file));

    assertThrows(ClosedFileSystemException.class, () -> Files.createDirectory(nothing));

    assertThrows(ClosedFileSystemException.class, () -> Files.createFile(nothing));

    assertThrows(
        ClosedFileSystemException.class,
        () -> Files.write(nothing, ImmutableList.of("hello world"), UTF_8));

    assertThrows(ClosedFileSystemException.class, () -> Files.newInputStream(file));

    assertThrows(ClosedFileSystemException.class, () -> Files.newOutputStream(file));

    assertThrows(ClosedFileSystemException.class, () -> Files.newByteChannel(file));

    assertThrows(ClosedFileSystemException.class, () -> Files.newDirectoryStream(dir));

    assertThrows(ClosedFileSystemException.class, () -> Files.copy(file, nothing));

    assertThrows(ClosedFileSystemException.class, () -> Files.move(file, nothing));

    assertThrows(ClosedFileSystemException.class, () -> Files.copy(dir, nothing));

    assertThrows(ClosedFileSystemException.class, () -> Files.move(dir, nothing));

    assertThrows(ClosedFileSystemException.class, () -> Files.createSymbolicLink(nothing, file));

    assertThrows(ClosedFileSystemException.class, () -> Files.createLink(nothing, file));

    assertThrows(ClosedFileSystemException.class, () -> Files.exists(file));

    assertThrows(ClosedFileSystemException.class, () -> Files.getAttribute(file, "size"));

    assertThrows(
        ClosedFileSystemException.class,
        () -> Files.setAttribute(file, "lastModifiedTime", FileTime.fromMillis(0)));

    assertThrows(
        ClosedFileSystemException.class,
        () -> Files.getFileAttributeView(file, BasicFileAttributeView.class));

    assertThrows(
        ClosedFileSystemException.class,
        () -> Files.readAttributes(file, "basic:size,lastModifiedTime"));

    assertThrows(
        ClosedFileSystemException.class,
        () -> Files.readAttributes(file, BasicFileAttributes.class));

    assertThrows(ClosedFileSystemException.class, () -> Files.isDirectory(dir));

    assertThrows(ClosedFileSystemException.class, () -> Files.readAllBytes(file));

    assertThrows(ClosedFileSystemException.class, () -> Files.isReadable(file));
  }
}
