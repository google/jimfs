/*
 * Copyright 2015 Google Inc.
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

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.common.io.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Tests that {@link URL} instances can be created and used from jimfs URIs.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class UrlTest {

  private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
  private Path path = fs.getPath("foo");

  @Test
  public void creatUrl() throws MalformedURLException {
    URL url = path.toUri().toURL();
    assertThat(url).isNotNull();
  }

  @Test
  public void readFromUrl() throws IOException {
    Files.write(path, ImmutableList.of("Hello World"), UTF_8);

    URL url = path.toUri().toURL();
    assertThat(Resources.asCharSource(url, UTF_8).read())
        .isEqualTo("Hello World" + LINE_SEPARATOR.value());
  }

  @Test
  public void readDirectoryContents() throws IOException {
    Files.createDirectory(path);
    Files.createFile(path.resolve("a.txt"));
    Files.createFile(path.resolve("b.txt"));
    Files.createDirectory(path.resolve("c"));

    URL url = path.toUri().toURL();
    assertThat(Resources.asCharSource(url, UTF_8).read())
        .isEqualTo("a.txt\nb.txt\nc\n");
  }

  @Test
  public void headers() throws IOException {
    byte[] bytes = {1, 2, 3};
    Files.write(path, bytes);
    FileTime lastModified = Files.getLastModifiedTime(path);

    URL url = path.toUri().toURL();
    URLConnection conn = url.openConnection();

    // read header fields directly
    assertThat(conn.getHeaderFields()).containsEntry("content-length", ImmutableList.of("3"));
    assertThat(conn.getHeaderFields())
        .containsEntry("content-type", ImmutableList.of("application/octet-stream"));

    if (lastModified != null) {
      assertThat(conn.getHeaderFields()).containsKey("last-modified");
      assertThat(conn.getHeaderFields()).hasSize(3);
    } else {
      assertThat(conn.getHeaderFields()).hasSize(2);
    }

    // use the specific methods for reading the expected headers
    assertThat(conn.getContentLengthLong()).isEqualTo(Files.size(path));
    assertThat(conn.getContentType()).isEqualTo("application/octet-stream");

    if (lastModified != null) {
      // The HTTP date format does not include milliseconds, which means that the last modified time
      // returned from the connection may not be exactly the same as that of the file system itself.
      // The difference should less than 1000ms though, and should never be greater.
      long difference = lastModified.toMillis() - conn.getLastModified();
      assertThat(difference).isIn(Range.closedOpen(0L, 1000L));
    } else {
      assertThat(conn.getLastModified()).isEqualTo(0L);
    }
  }

  @Test
  public void contentType() throws IOException {
    path = fs.getPath("foo.txt");
    Files.write(path, ImmutableList.of("Hello World"), UTF_8);

    URL url = path.toUri().toURL();
    URLConnection conn = url.openConnection();

    // Should be text/plain, but this is entirely dependent on the installed FileTypeDetectors
    String detectedContentType = Files.probeContentType(path);
    if (detectedContentType == null) {
      assertThat(conn.getContentType()).isEqualTo("application/octet-stream");
    } else {
      assertThat(conn.getContentType()).isEqualTo(detectedContentType);
    }
  }
}
