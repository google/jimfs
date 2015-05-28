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

package com.google.common.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Secure directory stream implementation that uses a {@link FileSystemView} with the stream's
 * directory as its working directory.
 *
 * @author Colin Decker
 */
final class JimfsSecureDirectoryStream implements SecureDirectoryStream<Path> {

  private final FileSystemView view;
  private final Filter<? super Path> filter;
  private final FileSystemState fileSystemState;

  private boolean open = true;
  private Iterator<Path> iterator = new DirectoryIterator();

  public JimfsSecureDirectoryStream(
      FileSystemView view, Filter<? super Path> filter, FileSystemState fileSystemState) {
    this.view = checkNotNull(view);
    this.filter = checkNotNull(filter);
    this.fileSystemState = fileSystemState;
    fileSystemState.register(this);
  }

  private JimfsPath path() {
    return view.getWorkingDirectoryPath();
  }

  @Override
  public synchronized Iterator<Path> iterator() {
    checkOpen();
    Iterator<Path> result = iterator;
    checkState(result != null, "iterator() has already been called once");
    iterator = null;
    return result;
  }

  @Override
  public synchronized void close() {
    open = false;
    fileSystemState.unregister(this);
  }

  protected synchronized void checkOpen() {
    if (!open) {
      throw new ClosedDirectoryStreamException();
    }
  }

  private final class DirectoryIterator extends AbstractIterator<Path> {

    @Nullable private Iterator<Name> fileNames;

    @Override
    protected synchronized Path computeNext() {
      checkOpen();

      try {
        if (fileNames == null) {
          fileNames = view.snapshotWorkingDirectoryEntries().iterator();
        }

        while (fileNames.hasNext()) {
          Name name = fileNames.next();
          Path path = view.getWorkingDirectoryPath().resolve(name);

          if (filter.accept(path)) {
            return path;
          }
        }

        return endOfData();
      } catch (IOException e) {
        throw new DirectoryIteratorException(e);
      }
    }
  }

  /**
   * A stream filter that always returns true.
   */
  public static final Filter<Object> ALWAYS_TRUE_FILTER =
      new Filter<Object>() {
        @Override
        public boolean accept(Object entry) throws IOException {
          return true;
        }
      };

  @Override
  public SecureDirectoryStream<Path> newDirectoryStream(Path path, LinkOption... options)
      throws IOException {
    checkOpen();
    JimfsPath checkedPath = checkPath(path);

    // safe cast because a file system that supports SecureDirectoryStream always creates
    // SecureDirectoryStreams
    return (SecureDirectoryStream<Path>)
        view.newDirectoryStream(
            checkedPath,
            ALWAYS_TRUE_FILTER,
            Options.getLinkOptions(options),
            path().resolve(checkedPath));
  }

  @Override
  public SeekableByteChannel newByteChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    checkOpen();
    JimfsPath checkedPath = checkPath(path);
    ImmutableSet<OpenOption> opts = Options.getOptionsForChannel(options);
    return new JimfsFileChannel(
        view.getOrCreateRegularFile(checkedPath, opts), opts, fileSystemState);
  }

  @Override
  public void deleteFile(Path path) throws IOException {
    checkOpen();
    JimfsPath checkedPath = checkPath(path);
    view.deleteFile(checkedPath, FileSystemView.DeleteMode.NON_DIRECTORY_ONLY);
  }

  @Override
  public void deleteDirectory(Path path) throws IOException {
    checkOpen();
    JimfsPath checkedPath = checkPath(path);
    view.deleteFile(checkedPath, FileSystemView.DeleteMode.DIRECTORY_ONLY);
  }

  @Override
  public void move(Path srcPath, SecureDirectoryStream<Path> targetDir, Path targetPath)
      throws IOException {
    checkOpen();
    JimfsPath checkedSrcPath = checkPath(srcPath);
    JimfsPath checkedTargetPath = checkPath(targetPath);

    if (!(targetDir instanceof JimfsSecureDirectoryStream)) {
      throw new ProviderMismatchException(
          "targetDir isn't a secure directory stream associated with this file system");
    }

    JimfsSecureDirectoryStream checkedTargetDir = (JimfsSecureDirectoryStream) targetDir;

    view.copy(
        checkedSrcPath,
        checkedTargetDir.view,
        checkedTargetPath,
        ImmutableSet.<CopyOption>of(),
        true);
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Class<V> type) {
    return getFileAttributeView(path().getFileSystem().getPath("."), type);
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(
      Path path, Class<V> type, LinkOption... options) {
    checkOpen();
    final JimfsPath checkedPath = checkPath(path);
    final ImmutableSet<LinkOption> optionsSet = Options.getLinkOptions(options);
    return view.getFileAttributeView(
        new FileLookup() {
          @Override
          public File lookup() throws IOException {
            checkOpen(); // per the spec, must check that the stream is open for each view operation
            return view
                .lookUpWithLock(checkedPath, optionsSet)
                .requireExists(checkedPath)
                .file();
          }
        },
        type);
  }

  private static JimfsPath checkPath(Path path) {
    if (path instanceof JimfsPath) {
      return (JimfsPath) path;
    }
    throw new ProviderMismatchException(
        "path " + path + " is not associated with a Jimfs file system");
  }
}
