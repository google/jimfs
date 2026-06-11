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
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

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
import org.jspecify.annotations.Nullable;

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

  synchronized void checkOpen() {
    if (!open) {
      throw new ClosedDirectoryStreamException();
    }
  }

  private final class DirectoryIterator extends AbstractIterator<Path> {

    private @Nullable Iterator<Name> fileNames;

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

  /** A stream filter that always returns true. */
  public static final Filter<Object> ALWAYS_TRUE_FILTER =
      new Filter<Object>() {
        @Override
        public boolean accept(Object entry) {
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

    // We only check that targetDir is a Jimfs stream and that targetPath is from the same file
    // system as targetDir here.
    JimfsSecureDirectoryStream checkedTargetDir = checkDirectoryStream(targetDir);
    JimfsPath checkedTargetPath = checkedTargetDir.checkPath(targetPath);

    // The case where the target dir and path agree but are for a different file system is handled
    // here, since getMoveOptions doesn't allow ATOMIC_MOVE when the two paths aren't for the same
    // FileSystem. Same as for Files.move, this also adds NOFOLLOW_LINKS since move specifies that
    // it doesn't follow links. (Technically it only seems to specify that if the target file is a
    // link it will move the link and not what it points to, which doesn't necessarily imply that it
    // won't follow intermediate directory links, but in practice it does not follow any links for
    // Unix at least.)
    ImmutableSet<CopyOption> options =
        Options.getMoveOptions(checkedSrcPath, checkedTargetPath, ATOMIC_MOVE);
    view.copy(checkedSrcPath, checkedTargetDir.view, checkedTargetPath, options, true);
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
            return view.lookUpWithLock(checkedPath, optionsSet).requireExists(checkedPath).file();
          }
        },
        type);
  }

  private JimfsPath checkPath(Path path) {
    if (!(path instanceof JimfsPath)) {
      throw new ProviderMismatchException(
          "path " + path + " is not associated with a Jimfs file system");
    }
    // SecureDirectoryStream is not clear about what should happen when it's given paths that are
    // from different FileSystems with the same FileSystemProvider. My thinking is that it doesn't
    // make sense to try to resolve a Path from one file system against a directory from a totally
    // different file system and that it's likely a programmer error if that is ever happening, but
    // it's not totally clear that's true. I can imagine, for example, that when copying something
    // from file system A to B one might want to resolve a relative path from A against a path from
    // B to get a new B path. But I tend to think it's better to be clearer about the conversion in
    // that case, for example by writing `bPath.resolve(aPath.toString())` (admittedly still not all
    // that clear).
    //
    // This behavior does match how our implementation of Path.resolve(Path) works, though that
    // method _also_ doesn't specify what should happen in that case.
    if (!path().getFileSystem().equals(path.getFileSystem())) {
      throw new ProviderMismatchException(
          "path "
              + path
              + " is associated with a different Jimfs file system than this "
              + "SecureDirectoryStream");
    }
    return (JimfsPath) path;
  }

  private static JimfsSecureDirectoryStream checkDirectoryStream(
      SecureDirectoryStream<Path> stream) {
    if (!(stream instanceof JimfsSecureDirectoryStream)) {
      throw new ProviderMismatchException(
          stream + " isn't a secure directory stream associated with this file system");
    }
    // We don't check that the stream's file system is the same as the calling stream's because we
    // let that problem be handled by the atomic move requirement of SecureDirectoryStream.move.
    return (JimfsSecureDirectoryStream) stream;
  }
}
