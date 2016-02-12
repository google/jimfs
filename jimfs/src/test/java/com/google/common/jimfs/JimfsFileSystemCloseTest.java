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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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

    try {
      FileSystems.getFileSystem(uri);
      fail();
    } catch (FileSystemNotFoundException expected) {
    }
  }

  @Test
  public void testOpenStreamsClosed() throws IOException {
    Path p = fs.getPath("/foo");
    OutputStream out = Files.newOutputStream(p);
    InputStream in = Files.newInputStream(p);

    out.write(1);
    assertEquals(1, in.read());

    fs.close();

    try {
      out.write(1);
      fail();
    } catch (IOException expected) {
      assertEquals("stream is closed", expected.getMessage());
    }

    try {
      in.read();
      fail();
    } catch (IOException expected) {
      assertEquals("stream is closed", expected.getMessage());
    }
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

    try {
      fc.size();
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      sbc.size();
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      afc.size();
      fail();
    } catch (ClosedChannelException expected) {
    }
  }

  @Test
  public void testOpenDirectoryStreamsClosed() throws IOException {
    Path p = fs.getPath("/foo");
    Files.createDirectory(p);

    DirectoryStream<Path> stream = Files.newDirectoryStream(p);

    fs.close();

    try {
      stream.iterator();
      fail();
    } catch (ClosedDirectoryStreamException expected) {
    }
  }

  @Test
  public void testOpenWatchServicesClosed() throws IOException {
    WatchService ws1 = fs.newWatchService();
    WatchService ws2 = fs.newWatchService();

    assertNull(ws1.poll());
    assertNull(ws2.poll());

    fs.close();

    try {
      ws1.poll();
      fail();
    } catch (ClosedWatchServiceException expected) {
    }

    try {
      ws2.poll();
      fail();
    } catch (ClosedWatchServiceException expected) {
    }
  }

  @Test
  public void testPathMethodsThrow() throws IOException {
    Path p = fs.getPath("/foo");
    Files.createDirectory(p);

    WatchService ws = fs.newWatchService();

    fs.close();

    try {
      p.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
      fail();
    } catch (ClosedWatchServiceException expected) {
    }

    try {
      p = p.toRealPath();
      fail();
    } catch (ClosedFileSystemException expected) {
    }

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

    try {
      view.readAttributes();
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      view.setTimes(null, null, null);
      fail();
    } catch (ClosedFileSystemException expected) {
    }
  }

  @Test
  public void testFileSystemMethodsThrow() throws IOException {
    fs.close();

    try {
      fs.getPath("/foo");
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      fs.getRootDirectories();
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      fs.getFileStores();
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      fs.getPathMatcher("glob:*.java");
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      fs.getUserPrincipalLookupService();
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      fs.newWatchService();
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      fs.supportedFileAttributeViews();
      fail();
    } catch (ClosedFileSystemException expected) {
    }
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

    try {
      Files.delete(file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.createDirectory(nothing);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.createFile(nothing);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.write(nothing, ImmutableList.of("hello world"), UTF_8);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.newInputStream(file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.newOutputStream(file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.newByteChannel(file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.newDirectoryStream(dir);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.copy(file, nothing);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.move(file, nothing);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.copy(dir, nothing);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.move(dir, nothing);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.createSymbolicLink(nothing, file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.createLink(nothing, file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.exists(file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.getAttribute(file, "size");
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.setAttribute(file, "lastModifiedTime", FileTime.fromMillis(0));
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.getFileAttributeView(file, BasicFileAttributeView.class);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.readAttributes(file, "basic:size,lastModifiedTime");
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.readAttributes(file, BasicFileAttributes.class);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.isDirectory(dir);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.readAllBytes(file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }

    try {
      Files.isReadable(file);
      fail();
    } catch (ClosedFileSystemException expected) {
    }
  }
}
