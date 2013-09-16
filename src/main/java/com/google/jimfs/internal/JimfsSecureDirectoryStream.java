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

package com.google.jimfs.internal;

import static com.google.jimfs.internal.FileSystemService.DeleteMode;
import static com.google.jimfs.internal.JimfsFileSystemProvider.getOptionsForChannel;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Set;

/**
 * Secure directory stream implementation that uses a {@link FileSystemService} with the stream's
 * directory as its working directory.
 *
 * @author Colin Decker
 */
final class JimfsSecureDirectoryStream
    extends JimfsDirectoryStream implements SecureDirectoryStream<Path> {

  private final FileSystemService service;

  public JimfsSecureDirectoryStream(FileSystemService service, Filter<? super Path> filter) {
    super(service.getWorkingDirectoryPath(), filter);
    this.service = service;
  }

  @Override
  protected Iterable<String> snapshotEntryNames() throws IOException {
    return service.snapshotBaseEntries();
  }

  @Override
  public SecureDirectoryStream<Path> newDirectoryStream(Path path, LinkOption... options)
      throws IOException {
    JimfsPath checkedPath = checkPath(path);
    return service.newSecureDirectoryStream(
        checkedPath, ALWAYS_TRUE_FILTER, LinkHandling.fromOptions(options),
        path().resolve(checkedPath));
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
      FileAttribute<?>... attrs) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    options = getOptionsForChannel(options);
    return new JimfsFileChannel(service.getRegularFile(checkedPath, options), options);
  }

  @Override
  public void deleteFile(Path path) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    service.deleteFile(checkedPath, DeleteMode.NON_DIRECTORY_ONLY);
  }

  @Override
  public void deleteDirectory(Path path) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    service.deleteFile(checkedPath, DeleteMode.DIRECTORY_ONLY);
  }

  @Override
  public void move(Path srcPath, SecureDirectoryStream<Path> targetDir, Path targetPath)
      throws IOException {
    JimfsPath checkedSrcPath = checkPath(srcPath);
    JimfsPath checkedTargetPath = checkPath(targetPath);

    if (!(targetDir instanceof JimfsSecureDirectoryStream)) {
      throw new ProviderMismatchException(
          "targetDir isn't a secure directory stream associated with this file system");
    }

    JimfsSecureDirectoryStream checkedTargetDir = (JimfsSecureDirectoryStream) targetDir;

    service.moveFile(
        checkedSrcPath, checkedTargetDir.service, checkedTargetPath, ImmutableSet.<CopyOption>of());
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Class<V> type) {
    return getFileAttributeView(path().getFileSystem().getPath("."), type);
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
      LinkOption... options) {
    JimfsPath checkedPath = checkPath(path);
    return service.getFileAttributeView(checkedPath, type, LinkHandling.fromOptions(options));
  }

  private static JimfsPath checkPath(Path path) {
    if (path instanceof JimfsPath) {
      return (JimfsPath) path;
    }
    throw new ProviderMismatchException(
        "path " + path + " is not associated with a JIMFS file system");
  }
}
