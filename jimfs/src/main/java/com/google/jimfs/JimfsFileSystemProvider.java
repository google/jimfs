/*
 * Copyright 2013 Google Inc.
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

package com.google.jimfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.jimfs.Jimfs.CONFIG_KEY;
import static com.google.jimfs.Jimfs.URI_SCHEME;
import static java.nio.file.StandardOpenOption.APPEND;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

/**
 * {@link FileSystemProvider} implementation for JimFS. While this class is public, it should not
 * be used directly. To create a new file system instance, see {@link Jimfs}. For other operations,
 * use the public APIs in {@code java.nio.file}.
 *
 * @author Colin Decker
 */
public final class JimfsFileSystemProvider extends FileSystemProvider {

  @Override
  public String getScheme() {
    return URI_SCHEME;
  }

  /**
   * Cache of file systems that have been created but not closed.
   */
  private final ConcurrentMap<URI, JimfsFileSystem> fileSystems = new ConcurrentHashMap<>();

  @Override
  public JimfsFileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
    checkArgument(uri.getScheme().equalsIgnoreCase(URI_SCHEME),
        "uri (%s) scheme must be '%s'", uri, URI_SCHEME);
    checkArgument(isValidFileSystemUri(uri),
        "uri (%s) may not have a path, query or fragment", uri);
    checkArgument(env.get(CONFIG_KEY) instanceof Configuration,
        "env map (%s) must contain key '%s' mapped to an instance of Jimfs.Configuration",
        env, CONFIG_KEY);

    Configuration config = (Configuration) env.get(CONFIG_KEY);
    JimfsFileSystem fileSystem = JimfsFileSystems.newFileSystem(this, uri, config);
    if (fileSystems.putIfAbsent(uri, fileSystem) != null) {
      throw new FileSystemAlreadyExistsException(uri.toString());
    }
    return fileSystem;
  }

  @Override
  public JimfsFileSystem getFileSystem(URI uri) {
    JimfsFileSystem fileSystem = fileSystems.get(uri);
    if (fileSystem == null) {
      throw new FileSystemNotFoundException(uri.toString());
    }
    return fileSystem;
  }

  @Override
  public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    checkNotNull(env);

    URI pathUri = checkedPath.toUri();
    URI jarUri = URI.create("jar:" + pathUri);

    try {
      // pass the new jar:jimfs://... URI to be handled by ZipFileSystemProvider
      return FileSystems.newFileSystem(jarUri, env);
    } catch (Exception e) {
      // if any exception occurred, assume the file wasn't a zip file and that we don't support
      // viewing it as a file system
      throw new UnsupportedOperationException(e);
    }
  }

  /**
   * Called when the given file system is closed to remove it from this provider.
   */
  void remove(JimfsFileSystem fileSystem) {
    fileSystems.remove(fileSystem.getUri());
  }

  @Override
  public Path getPath(URI uri) {
    checkArgument(URI_SCHEME.equalsIgnoreCase(uri.getScheme()),
        "uri scheme does not match this provider: %s", uri);
    checkArgument(!isNullOrEmpty(uri.getPath()), "uri must have a path: %s", uri);

    return getFileSystem(toFileSystemUri(uri)).toPath(uri);
  }

  /**
   * Returns whether or not the given URI is valid as a base file system URI. It must not have a
   * path, query or fragment.
   */
  private static boolean isValidFileSystemUri(URI uri) {
    // would like to just check null, but fragment appears to be the empty string when not present
    return isNullOrEmpty(uri.getPath())
        && isNullOrEmpty(uri.getQuery())
        && isNullOrEmpty(uri.getFragment());
  }

  /**
   * Returns the given URI with any path, query or fragment stripped off.
   */
  private static URI toFileSystemUri(URI uri) {
    try {
      return new URI(
          uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
          null, null, null);
    } catch (URISyntaxException e) {
      throw new AssertionError(e);
    }
  }

  private static JimfsPath checkPath(Path path) {
    if (path instanceof JimfsPath) {
      return (JimfsPath) path;
    }
    throw new ProviderMismatchException(
        "path " + path + " is not associated with a JimFS file system");
  }

  /**
   * Gets the file system for the given path.
   */
  private static JimfsFileSystem getFileSystem(Path path) {
    return (JimfsFileSystem) checkPath(path).getFileSystem();
  }

  /**
   * Returns the default file system view for the given path.
   */
  private static FileSystemView getDefaultView(JimfsPath path) {
    return getFileSystem(path).getDefaultView();
  }

  @Override
  public JimfsFileChannel newFileChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    ImmutableSet<OpenOption> opts = Options.getOptionsForChannel(options);
    File file = getDefaultView(checkedPath).getOrCreateRegularFile(checkedPath, opts, attrs);
    return new JimfsFileChannel(file, opts);
  }

  @Override
  public SeekableByteChannel newByteChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    return newFileChannel(path, options, attrs);
  }

  @Override
  public AsynchronousFileChannel newAsynchronousFileChannel(
      Path path, Set<? extends OpenOption> options,
      @Nullable ExecutorService executor, FileAttribute<?>... attrs)
      throws IOException {
    JimfsFileChannel channel = newFileChannel(path, options, attrs);
    if (executor == null) {
      JimfsFileSystem fileSystem = (JimfsFileSystem) path.getFileSystem();
      executor = fileSystem.getDefaultThreadPool();
    }
    return channel.asAsynchronousFileChannel(executor);
  }

  @Override
  public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    ImmutableSet<OpenOption> opts = Options.getOptionsForInputStream(options);
    File file = getDefaultView(checkedPath)
        .getOrCreateRegularFile(checkedPath, opts, NO_ATTRS);
    return new JimfsInputStream(file);
  }

  private static final FileAttribute<?>[] NO_ATTRS = {};

  @Override
  public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    ImmutableSet<OpenOption> opts = Options.getOptionsForOutputStream(options);
    File file = getDefaultView(checkedPath)
        .getOrCreateRegularFile(checkedPath, opts, NO_ATTRS);
    return new JimfsOutputStream(file, opts.contains(APPEND));
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir,
      DirectoryStream.Filter<? super Path> filter) throws IOException {
    JimfsPath checkedPath = checkPath(dir);
    return getDefaultView(checkedPath)
        .newSecureDirectoryStream(checkedPath, filter, Options.FOLLOW_LINKS, checkedPath);
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    JimfsPath checkedPath = checkPath(dir);
    FileSystemView view = getDefaultView(checkedPath);
    view.createDirectory(checkedPath, attrs);
  }

  @Override
  public void createLink(Path link, Path existing) throws IOException {
    JimfsPath linkPath = checkPath(link);
    JimfsPath existingPath = checkPath(existing);
    checkArgument(linkPath.getFileSystem().equals(existingPath.getFileSystem()),
        "link and existing paths must belong to the same file system instance");
    FileSystemView view = getDefaultView(linkPath);
    view.link(linkPath, getDefaultView(existingPath), existingPath);
  }

  @Override
  public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs)
      throws IOException {
    JimfsPath linkPath = checkPath(link);
    JimfsPath targetPath = checkPath(target);
    checkArgument(linkPath.getFileSystem().equals(targetPath.getFileSystem()),
        "link and target paths must belong to the same file system instance");
    FileSystemView view = getDefaultView(linkPath);
    view.createSymbolicLink(linkPath, targetPath, attrs);
  }

  @Override
  public Path readSymbolicLink(Path link) throws IOException {
    JimfsPath checkedPath = checkPath(link);
    return getDefaultView(checkedPath).readSymbolicLink(checkedPath);
  }

  @Override
  public void delete(Path path) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    FileSystemView view = getDefaultView(checkedPath);
    view.deleteFile(checkedPath, FileSystemView.DeleteMode.ANY);
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    copy(source, target, Options.getCopyOptions(options), false);
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    copy(source, target, Options.getMoveOptions(options), true);
  }

  private void copy(Path source, Path target,
      ImmutableSet<CopyOption> options, boolean move) throws IOException {
    JimfsPath sourcePath = checkPath(source);
    JimfsPath targetPath = checkPath(target);

    FileSystemView sourceView = getDefaultView(sourcePath);
    FileSystemView targetView = getDefaultView(targetPath);
    sourceView.copy(sourcePath, targetView, targetPath, options, move);
  }

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException {
    if (path.equals(path2)) {
      return true;
    }

    if (!(path instanceof JimfsPath && path2 instanceof JimfsPath)) {
      return false;
    }

    JimfsPath checkedPath = (JimfsPath) path;
    JimfsPath checkedPath2 = (JimfsPath) path2;

    FileSystemView view = getDefaultView(checkedPath);
    FileSystemView view2 = getDefaultView(checkedPath2);

    return view.isSameFile(checkedPath, view2, checkedPath2);
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    // TODO(cgdecker): This should probably be configurable, but this seems fine for now
    /*
     * If the DOS view is supported, use the Windows isHidden method (check the dos:hidden
     * attribute). Otherwise, use the Unix isHidden method (just check if the file name starts with
     * ".").
     */
    JimfsPath checkedPath = checkPath(path);
    FileSystemView view = getDefaultView(checkedPath);
    if (getFileStore(path).supportsFileAttributeView("dos")) {
      return view.readAttributes(checkedPath, DosFileAttributes.class, Options.NOFOLLOW_LINKS)
          .isHidden();
    }
    return path.getNameCount() > 0 && path.getFileName().toString().startsWith(".");
  }

  @Override
  public JimfsFileStore getFileStore(Path path) throws IOException {
    return getFileSystem(path).getFileStore();
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    getDefaultView(checkedPath).checkAccess(checkedPath);
  }

  @Nullable
  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
      LinkOption... options) {
    JimfsPath checkedPath = checkPath(path);
    return getDefaultView(checkedPath)
        .getFileAttributeView(checkedPath, type, Options.getLinkOptions(options));
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type,
      LinkOption... options) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    return getDefaultView(checkedPath)
        .readAttributes(checkedPath, type, Options.getLinkOptions(options));
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
      throws IOException {
    JimfsPath checkedPath = checkPath(path);
    return getDefaultView(checkedPath)
        .readAttributes(checkedPath, attributes, Options.getLinkOptions(options));
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
      throws IOException {
    JimfsPath checkedPath = checkPath(path);
    getDefaultView(checkedPath)
        .setAttribute(checkedPath, attribute, value, Options.getLinkOptions(options));
  }
}
