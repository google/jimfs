package com.google.common.jimfs;

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
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * {@code URLConnection} implementation.
 *
 * @author Colin Decker
 */
final class PathURLConnection extends URLConnection {

  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  private InputStream stream;
  private ImmutableListMultimap<String, String> headers = ImmutableListMultimap.of();

  PathURLConnection(URL url) {
    super(url);
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

    headers = buildHeaders(length, lastModified, contentType);
  }

  private static URI toUri(URL url) throws IOException {
    try {
      return url.toURI();
    } catch (URISyntaxException e) {
      throw new IOException("URL " + url + " cannot be converted to a URI", e);
    }
  }

  private static ImmutableListMultimap<String, String> buildHeaders(
          long length, FileTime lastModified, String contentType) {
    ImmutableListMultimap.Builder<String, String> builder = ImmutableListMultimap.builder();
    builder.put("content-length", "" + length);
    builder.put("content-type", contentType);
    if (lastModified != null) {
      String formattedDate = HeaderFormatter.formatLastModified(lastModified);
      builder.put("last-modified", formattedDate);
    }
    return builder.build();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    connect();
    return stream;
  }

  @SuppressWarnings("unchecked")
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
  public @Nullable String getHeaderField(String name) {
    try {
      connect();
    } catch (IOException e) {
      return null;
    }

    // no header should have more than one value
    return Iterables.getFirst(headers.get(Ascii.toLowerCase(name)), null);
  }
}
