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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * {@code URLConnection} implementation.
 *
 * @author Colin Decker
 */
final class PathURLConnection extends URLConnection {

  /*
   * This implementation should be able to work for any proper file system implementation... it
   * might be useful to release it and make it usable by other file systems.
   */

  private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss \'GMT\'";
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  private InputStream stream;
  private ImmutableListMultimap<String, String> headers = ImmutableListMultimap.of();

  PathURLConnection(URL url) {
    super(checkNotNull(url));
  }

  @Override
  public void connect() throws IOException {
    if (stream != null) {
      return;
    }

    Path path = Paths.get(toUri(url));

    long length;
    if (Files.isDirectory(path)) {
      // Match File URL behavior for directories by having the stream contain the filenames in
      // the directory separated by newlines.
      StringBuilder builder = new StringBuilder();
      try (DirectoryStream<Path> files = Files.newDirectoryStream(path)) {
        for (Path file : files) {
          builder.append(file.getFileName()).append('\n');
        }
      }
      byte[] bytes = builder.toString().getBytes(UTF_8);
      stream = new ByteArrayInputStream(bytes);
      length = bytes.length;
    } else {
      stream = Files.newInputStream(path);
      length = Files.size(path);
    }

    FileTime lastModified = Files.getLastModifiedTime(path);
    String contentType =
        MoreObjects.firstNonNull(Files.probeContentType(path), DEFAULT_CONTENT_TYPE);

    ImmutableListMultimap.Builder<String, String> builder = ImmutableListMultimap.builder();
    builder.put("content-length", "" + length);
    builder.put("content-type", contentType);
    if (lastModified != null) {
      DateFormat format = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
      format.setTimeZone(TimeZone.getTimeZone("GMT"));
      builder.put("last-modified", format.format(new Date(lastModified.toMillis())));
    }

    headers = builder.build();
  }

  private static URI toUri(URL url) throws IOException {
    try {
      return url.toURI();
    } catch (URISyntaxException e) {
      throw new IOException("URL " + url + " cannot be converted to a URI", e);
    }
  }

  @Override
  public InputStream getInputStream() throws IOException {
    connect();
    return stream;
  }

  @SuppressWarnings("unchecked") // safe by specification of ListMultimap.asMap()
  @Override
  public Map<String, List<String>> getHeaderFields() {
    try {
      connect();
    } catch (IOException e) {
      return ImmutableMap.of();
    }
    return (ImmutableMap<String, List<String>>) (ImmutableMap<String, ?>) headers.asMap();
  }

  @Override
  public String getHeaderField(String name) {
    try {
      connect();
    } catch (IOException e) {
      return null;
    }

    // no header should have more than one value
    return Iterables.getFirst(headers.get(Ascii.toLowerCase(name)), null);
  }
}
