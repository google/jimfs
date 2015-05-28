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
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import javax.annotation.Nullable;

/**
 * View of a file system with a specific working directory. As all file system operations need to
 * work when given either relative or absolute paths, this class contains the implementation of most
 * file system operations, with relative path operations resolving against the working directory.
 *
 * <p>A file system has one default view using the file system's working directory. Additional views
 * may be created for use in {@link SecureDirectoryStream} instances, which each have a different
 * working directory they use.
 *
 * @author Colin Decker
 */
final class FileSystemView {

  private final JimfsFileStore store;

  private final Directory workingDirectory;
  private final JimfsPath workingDirectoryPath;

  /**
   * Creates a new file system view.
   */
  public FileSystemView(
      JimfsFileStore store, Directory workingDirectory, JimfsPath workingDirectoryPath) {
    this.store = checkNotNull(store);
    this.workingDirectory = checkNotNull(workingDirectory);
    this.workingDirectoryPath = checkNotNull(workingDirectoryPath);
  }

  /**
   * Returns whether or not this view and the given view belong to the same file system.
   */
  private boolean isSameFileSystem(FileSystemView other) {
    return store == other.store;
  }

  /**
   * Returns the file system state.
   */
  public FileSystemState state() {
    return store.state();
  }

  /**
   * Returns the path of the working directory at the time this view was created. Does not reflect
   * changes to the path caused by the directory being moved.
   */
  public JimfsPath getWorkingDirectoryPath() {
    return workingDirectoryPath;
  }

  /**
   * Attempt to look up the file at the given path.
   */
  DirectoryEntry lookUpWithLock(JimfsPath path, Set<? super LinkOption> options)
      throws IOException {
    store.readLock().lock();
    try {
      return lookUp(path, options);
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Looks up the file at the given path without locking.
   */
  private DirectoryEntry lookUp(JimfsPath path, Set<? super LinkOption> options)
      throws IOException {
    return store.lookUp(workingDirectory, path, options);
  }

  /**
   * Creates a new directory stream for the directory located by the given path. The given
   * {@code basePathForStream} is that base path that the returned stream will use. This will be
   * the same as {@code dir} except for streams created relative to another secure stream.
   */
  public DirectoryStream<Path> newDirectoryStream(
      JimfsPath dir,
      DirectoryStream.Filter<? super Path> filter,
      Set<? super LinkOption> options,
      JimfsPath basePathForStream)
      throws IOException {
    Directory file = (Directory) lookUpWithLock(dir, options)
        .requireDirectory(dir)
        .file();
    FileSystemView view = new FileSystemView(store, file, basePathForStream);
    JimfsSecureDirectoryStream stream = new JimfsSecureDirectoryStream(view, filter, state());
    return store.supportsFeature(Feature.SECURE_DIRECTORY_STREAM)
        ? stream
        : new DowngradedDirectoryStream(stream);
  }

  /**
   * Snapshots the entries of the working directory of this view.
   */
  public ImmutableSortedSet<Name> snapshotWorkingDirectoryEntries() {
    store.readLock().lock();
    try {
      ImmutableSortedSet<Name> names = workingDirectory.snapshot();
      workingDirectory.updateAccessTime();
      return names;
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Returns a snapshot mapping the names of each file in the directory at the given path to the
   * last modified time of that file.
   */
  public ImmutableMap<Name, Long> snapshotModifiedTimes(JimfsPath path) throws IOException {
    ImmutableMap.Builder<Name, Long> modifiedTimes = ImmutableMap.builder();

    store.readLock().lock();
    try {
      Directory dir = (Directory) lookUp(path, Options.FOLLOW_LINKS)
          .requireDirectory(path)
          .file();
      // TODO(cgdecker): Investigate whether WatchServices should keep a reference to the actual
      // directory when SecureDirectoryStream is supported rather than looking up the directory
      // each time the WatchService polls

      for (DirectoryEntry entry : dir) {
        if (!entry.name().equals(Name.SELF) && !entry.name().equals(Name.PARENT)) {
          modifiedTimes.put(entry.name(), entry.file().getLastModifiedTime());
        }
      }

      return modifiedTimes.build();
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Returns whether or not the two given paths locate the same file. The second path is located
   * using the given view rather than this file view.
   */
  public boolean isSameFile(JimfsPath path, FileSystemView view2, JimfsPath path2)
      throws IOException {
    if (!isSameFileSystem(view2)) {
      return false;
    }

    store.readLock().lock();
    try {
      File file = lookUp(path, Options.FOLLOW_LINKS).fileOrNull();
      File file2 = view2.lookUp(path2, Options.FOLLOW_LINKS).fileOrNull();
      return file != null && Objects.equals(file, file2);
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Gets the {@linkplain Path#toRealPath(LinkOption...) real path} to the file located by the
   * given path.
   */
  public JimfsPath toRealPath(
      JimfsPath path, PathService pathService, Set<? super LinkOption> options) throws IOException {
    checkNotNull(path);
    checkNotNull(options);

    store.readLock().lock();
    try {
      DirectoryEntry entry = lookUp(path, options).requireExists(path);

      List<Name> names = new ArrayList<>();
      names.add(entry.name());
      while (!entry.file().isRootDirectory()) {
        entry = entry.directory().entryInParent();
        names.add(entry.name());
      }

      // names are ordered last to first in the list, so get the reverse view
      List<Name> reversed = Lists.reverse(names);
      Name root = reversed.remove(0);
      return pathService.createPath(root, reversed);
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Creates a new directory at the given path. The given attributes will be set on the new file if
   * possible.
   */
  public Directory createDirectory(JimfsPath path, FileAttribute<?>... attrs) throws IOException {
    return (Directory) createFile(path, store.directoryCreator(), true, attrs);
  }

  /**
   * Creates a new symbolic link at the given path with the given target. The given attributes will
   * be set on the new file if possible.
   */
  public SymbolicLink createSymbolicLink(
      JimfsPath path, JimfsPath target, FileAttribute<?>... attrs) throws IOException {
    if (!store.supportsFeature(Feature.SYMBOLIC_LINKS)) {
      throw new UnsupportedOperationException();
    }
    return (SymbolicLink) createFile(path, store.symbolicLinkCreator(target), true, attrs);
  }

  /**
   * Creates a new file at the given path if possible, using the given supplier to create the file.
   * Returns the new file. If {@code allowExisting} is {@code true} and a file already exists at
   * the given path, returns that file. Otherwise, throws {@link FileAlreadyExistsException}.
   */
  private File createFile(
      JimfsPath path,
      Supplier<? extends File> fileCreator,
      boolean failIfExists,
      FileAttribute<?>... attrs)
      throws IOException {
    checkNotNull(path);
    checkNotNull(fileCreator);

    store.writeLock().lock();
    try {
      DirectoryEntry entry = lookUp(path, Options.NOFOLLOW_LINKS);

      if (entry.exists()) {
        if (failIfExists) {
          throw new FileAlreadyExistsException(path.toString());
        }

        // currently can only happen if getOrCreateFile doesn't find the file with the read lock
        // and then the file is created between when it releases the read lock and when it
        // acquires the write lock; so, very unlikely
        return entry.file();
      }

      Directory parent = entry.directory();

      File newFile = fileCreator.get();
      store.setInitialAttributes(newFile, attrs);
      parent.link(path.name(), newFile);
      parent.updateModifiedTime();
      return newFile;
    } finally {
      store.writeLock().unlock();
    }
  }

  /**
   * Gets the regular file at the given path, creating it if it doesn't exist and the given options
   * specify that it should be created.
   */
  public RegularFile getOrCreateRegularFile(
      JimfsPath path, Set<OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    checkNotNull(path);

    if (!options.contains(CREATE_NEW)) {
      // assume file exists unless we're explicitly trying to create a new file
      RegularFile file = lookUpRegularFile(path, options);
      if (file != null) {
        return file;
      }
    }

    if (options.contains(CREATE) || options.contains(CREATE_NEW)) {
      return getOrCreateRegularFileWithWriteLock(path, options, attrs);
    } else {
      throw new NoSuchFileException(path.toString());
    }
  }

  /**
   * Looks up the regular file at the given path, throwing an exception if the file isn't a regular
   * file. Returns null if the file did not exist.
   */
  @Nullable
  private RegularFile lookUpRegularFile(JimfsPath path, Set<OpenOption> options)
      throws IOException {
    store.readLock().lock();
    try {
      DirectoryEntry entry = lookUp(path, options);
      if (entry.exists()) {
        File file = entry.file();
        if (!file.isRegularFile()) {
          throw new FileSystemException(path.toString(), null, "not a regular file");
        }
        return open((RegularFile) file, options);
      } else {
        return null;
      }
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Gets or creates a new regular file with a write lock (assuming the file does not exist).
   */
  private RegularFile getOrCreateRegularFileWithWriteLock(
      JimfsPath path, Set<OpenOption> options, FileAttribute<?>[] attrs) throws IOException {
    store.writeLock().lock();
    try {
      File file = createFile(path, store.regularFileCreator(), options.contains(CREATE_NEW), attrs);
      // the file already existed but was not a regular file
      if (!file.isRegularFile()) {
        throw new FileSystemException(path.toString(), null, "not a regular file");
      }
      return open((RegularFile) file, options);
    } finally {
      store.writeLock().unlock();
    }
  }

  /**
   * Opens the given regular file with the given options, truncating it if necessary and
   * incrementing its open count. Returns the given file.
   */
  private static RegularFile open(RegularFile file, Set<OpenOption> options) {
    if (options.contains(TRUNCATE_EXISTING) && options.contains(WRITE)) {
      file.writeLock().lock();
      try {
        file.truncate(0);
      } finally {
        file.writeLock().unlock();
      }
    }

    // must be opened while holding a file store lock to ensure no race between opening and
    // deleting the file
    file.opened();

    return file;
  }

  /**
   * Returns the target of the symbolic link at the given path.
   */
  public JimfsPath readSymbolicLink(JimfsPath path) throws IOException {
    if (!store.supportsFeature(Feature.SYMBOLIC_LINKS)) {
      throw new UnsupportedOperationException();
    }

    SymbolicLink symbolicLink =
        (SymbolicLink) lookUpWithLock(path, Options.NOFOLLOW_LINKS)
            .requireSymbolicLink(path)
            .file();

    return symbolicLink.target();
  }

  /**
   * Checks access to the file at the given path for the given modes. Since access controls are not
   * implemented for this file system, this just checks that the file exists.
   */
  public void checkAccess(JimfsPath path) throws IOException {
    // just check that the file exists
    lookUpWithLock(path, Options.FOLLOW_LINKS).requireExists(path);
  }

  /**
   * Creates a hard link at the given link path to the regular file at the given path. The existing
   * file must exist and must be a regular file. The given file system view must belong to the same
   * file system as this view.
   */
  public void link(JimfsPath link, FileSystemView existingView, JimfsPath existing)
      throws IOException {
    checkNotNull(link);
    checkNotNull(existingView);
    checkNotNull(existing);

    if (!store.supportsFeature(Feature.LINKS)) {
      throw new UnsupportedOperationException();
    }

    if (!isSameFileSystem(existingView)) {
      throw new FileSystemException(
          link.toString(),
          existing.toString(),
          "can't link: source and target are in different file system instances");
    }

    Name linkName = link.name();

    // existingView is in the same file system, so just one lock is needed
    store.writeLock().lock();
    try {
      // we do want to follow links when finding the existing file
      File existingFile =
          existingView
              .lookUp(existing, Options.FOLLOW_LINKS)
              .requireExists(existing)
              .file();
      if (!existingFile.isRegularFile()) {
        throw new FileSystemException(
            link.toString(), existing.toString(), "can't link: not a regular file");
      }

      Directory linkParent =
          lookUp(link, Options.NOFOLLOW_LINKS).requireDoesNotExist(link).directory();

      linkParent.link(linkName, existingFile);
      linkParent.updateModifiedTime();
    } finally {
      store.writeLock().unlock();
    }
  }

  /**
   * Deletes the file at the given absolute path.
   */
  public void deleteFile(JimfsPath path, DeleteMode deleteMode) throws IOException {
    store.writeLock().lock();
    try {
      DirectoryEntry entry = lookUp(path, Options.NOFOLLOW_LINKS).requireExists(path);
      delete(entry, deleteMode, path);
    } finally {
      store.writeLock().unlock();
    }
  }

  /**
   * Deletes the given directory entry from its parent directory.
   */
  private void delete(DirectoryEntry entry, DeleteMode deleteMode, JimfsPath pathForException)
      throws IOException {
    Directory parent = entry.directory();
    File file = entry.file();

    checkDeletable(file, deleteMode, pathForException);
    parent.unlink(entry.name());
    parent.updateModifiedTime();

    file.deleted();
  }

  /**
   * Mode for deleting. Determines what types of files can be deleted.
   */
  public enum DeleteMode {
    /**
     * Delete any file.
     */
    ANY,
    /**
     * Only delete non-directory files.
     */
    NON_DIRECTORY_ONLY,
    /**
     * Only delete directory files.
     */
    DIRECTORY_ONLY
  }

  /**
   * Checks that the given file can be deleted, throwing an exception if it can't.
   */
  private void checkDeletable(File file, DeleteMode mode, Path path) throws IOException {
    if (file.isRootDirectory()) {
      throw new FileSystemException(path.toString(), null, "can't delete root directory");
    }

    if (file.isDirectory()) {
      if (mode == DeleteMode.NON_DIRECTORY_ONLY) {
        throw new FileSystemException(path.toString(), null, "can't delete: is a directory");
      }

      checkEmpty(((Directory) file), path);
    } else if (mode == DeleteMode.DIRECTORY_ONLY) {
      throw new FileSystemException(path.toString(), null, "can't delete: is not a directory");
    }

    if (file == workingDirectory && !path.isAbsolute()) {
      // this is weird, but on Unix at least, the file system seems to be happy to delete the
      // working directory if you give the absolute path to it but fail if you use a relative path
      // that resolves to the working directory (e.g. "" or ".")
      throw new FileSystemException(path.toString(), null, "invalid argument");
    }
  }

  /**
   * Checks that given directory is empty, throwing {@link DirectoryNotEmptyException} if not.
   */
  private void checkEmpty(Directory dir, Path pathForException) throws FileSystemException {
    if (!dir.isEmpty()) {
      throw new DirectoryNotEmptyException(pathForException.toString());
    }
  }

  /**
   * Copies or moves the file at the given source path to the given dest path.
   */
  public void copy(
      JimfsPath source,
      FileSystemView destView,
      JimfsPath dest,
      Set<CopyOption> options,
      boolean move)
      throws IOException {
    checkNotNull(source);
    checkNotNull(destView);
    checkNotNull(dest);
    checkNotNull(options);

    boolean sameFileSystem = isSameFileSystem(destView);

    File sourceFile;
    File copyFile = null; // non-null after block completes iff source file was copied
    lockBoth(store.writeLock(), destView.store.writeLock());
    try {
      DirectoryEntry sourceEntry = lookUp(source, options).requireExists(source);
      DirectoryEntry destEntry = destView.lookUp(dest, Options.NOFOLLOW_LINKS);

      Directory sourceParent = sourceEntry.directory();
      sourceFile = sourceEntry.file();

      Directory destParent = destEntry.directory();

      if (move && sourceFile.isDirectory()) {
        if (sameFileSystem) {
          checkMovable(sourceFile, source);
          checkNotAncestor(sourceFile, destParent, destView);
        } else {
          // move to another file system is accomplished by copy-then-delete, so the source file
          // must be deletable to be moved
          checkDeletable(sourceFile, DeleteMode.ANY, source);
        }
      }

      if (destEntry.exists()) {
        if (destEntry.file().equals(sourceFile)) {
          return;
        } else if (options.contains(REPLACE_EXISTING)) {
          destView.delete(destEntry, DeleteMode.ANY, dest);
        } else {
          throw new FileAlreadyExistsException(dest.toString());
        }
      }

      if (move && sameFileSystem) {
        // Real move on the same file system.
        sourceParent.unlink(source.name());
        sourceParent.updateModifiedTime();

        destParent.link(dest.name(), sourceFile);
        destParent.updateModifiedTime();
      } else {
        // Doing a copy OR a move to a different file system, which must be implemented by copy and
        // delete.

        // By default, don't copy attributes.
        AttributeCopyOption attributeCopyOption = AttributeCopyOption.NONE;
        if (move) {
          // Copy only the basic attributes of the file to the other file system, as it may not
          // support all the attribute views that this file system does. This also matches the
          // behavior of moving a file to a foreign file system with a different
          // FileSystemProvider.
          attributeCopyOption = AttributeCopyOption.BASIC;
        } else if (options.contains(COPY_ATTRIBUTES)) {
          // As with move, if we're copying the file to a different file system, only copy its
          // basic attributes.
          attributeCopyOption =
              sameFileSystem ? AttributeCopyOption.ALL : AttributeCopyOption.BASIC;
        }

        // Copy the file, but don't copy its content while we're holding the file store locks.
        copyFile = destView.store.copyWithoutContent(sourceFile, attributeCopyOption);
        destParent.link(dest.name(), copyFile);
        destParent.updateModifiedTime();

        // In order for the copy to be atomic (not strictly necessary, but seems preferable since
        // we can) lock both source and copy files before leaving the file store locks. This
        // ensures that users cannot observe the copy's content until the content has been copied.
        // This also marks the source file as opened, preventing its content from being deleted
        // until after it's copied if the source file itself is deleted in the next step.
        lockSourceAndCopy(sourceFile, copyFile);

        if (move) {
          // It should not be possible for delete to throw an exception here, because we already
          // checked that the file was deletable above.
          delete(sourceEntry, DeleteMode.ANY, source);
        }
      }
    } finally {
      destView.store.writeLock().unlock();
      store.writeLock().unlock();
    }

    if (copyFile != null) {
      // Copy the content. This is done outside the above block to minimize the time spent holding
      // file store locks, since copying the content of a regular file could take a (relatively)
      // long time. If done inside the above block, copying using Files.copy can be slower than
      // copying with an InputStream and an OutputStream if many files are being copied on
      // different threads.
      try {
        sourceFile.copyContentTo(copyFile);
      } finally {
        // Unlock the files, allowing the content of the copy to be observed by the user. This also
        // closes the source file, allowing its content to be deleted if it was deleted.
        unlockSourceAndCopy(sourceFile, copyFile);
      }
    }
  }

  private void checkMovable(File file, JimfsPath path) throws FileSystemException {
    if (file.isRootDirectory()) {
      throw new FileSystemException(path.toString(), null, "can't move root directory");
    }
  }

  /**
   * Acquires both write locks in a way that attempts to avoid the possibility of deadlock. Note
   * that typically (when only one file system instance is involved), both locks will be the same
   * lock and there will be no issue at all.
   */
  private static void lockBoth(Lock sourceWriteLock, Lock destWriteLock) {
    while (true) {
      sourceWriteLock.lock();
      if (destWriteLock.tryLock()) {
        return;
      } else {
        sourceWriteLock.unlock();
      }

      destWriteLock.lock();
      if (sourceWriteLock.tryLock()) {
        return;
      } else {
        destWriteLock.unlock();
      }
    }
  }

  /**
   * Checks that source is not an ancestor of dest, throwing an exception if it is.
   */
  private void checkNotAncestor(File source, Directory destParent, FileSystemView destView)
      throws IOException {
    // if dest is not in the same file system, it couldn't be in source's subdirectories
    if (!isSameFileSystem(destView)) {
      return;
    }

    Directory current = destParent;
    while (true) {
      if (current.equals(source)) {
        throw new IOException(
            "invalid argument: can't move directory into a subdirectory of itself");
      }

      if (current.isRootDirectory()) {
        return;
      } else {
        current = current.parent();
      }
    }
  }

  /**
   * Locks source and copy files before copying content. Also marks the source file as opened so
   * that its content won't be deleted until after the copy if it is deleted.
   */
  private void lockSourceAndCopy(File sourceFile, File copyFile) {
    sourceFile.opened();
    ReadWriteLock sourceLock = sourceFile.contentLock();
    if (sourceLock != null) {
      sourceLock.readLock().lock();
    }
    ReadWriteLock copyLock = copyFile.contentLock();
    if (copyLock != null) {
      copyLock.writeLock().lock();
    }
  }

  /**
   * Unlocks source and copy files after copying content. Also closes the source file so its
   * content can be deleted if it was deleted.
   */
  private void unlockSourceAndCopy(File sourceFile, File copyFile) {
    ReadWriteLock sourceLock = sourceFile.contentLock();
    if (sourceLock != null) {
      sourceLock.readLock().unlock();
    }
    ReadWriteLock copyLock = copyFile.contentLock();
    if (copyLock != null) {
      copyLock.writeLock().unlock();
    }
    sourceFile.closed();
  }

  /**
   * Returns a file attribute view using the given lookup callback.
   */
  @Nullable
  public <V extends FileAttributeView> V getFileAttributeView(FileLookup lookup, Class<V> type) {
    return store.getFileAttributeView(lookup, type);
  }

  /**
   * Returns a file attribute view for the given path in this view.
   */
  @Nullable
  public <V extends FileAttributeView> V getFileAttributeView(
      final JimfsPath path, Class<V> type, final Set<? super LinkOption> options) {
    return store.getFileAttributeView(
        new FileLookup() {
          @Override
          public File lookup() throws IOException {
            return lookUpWithLock(path, options)
                .requireExists(path)
                .file();
          }
        },
        type);
  }

  /**
   * Reads attributes of the file located by the given path in this view as an object.
   */
  public <A extends BasicFileAttributes> A readAttributes(
      JimfsPath path, Class<A> type, Set<? super LinkOption> options) throws IOException {
    File file = lookUpWithLock(path, options)
        .requireExists(path)
        .file();
    return store.readAttributes(file, type);
  }

  /**
   * Reads attributes of the file located by the given path in this view as a map.
   */
  public ImmutableMap<String, Object> readAttributes(
      JimfsPath path, String attributes, Set<? super LinkOption> options) throws IOException {
    File file = lookUpWithLock(path, options)
        .requireExists(path)
        .file();
    return store.readAttributes(file, attributes);
  }

  /**
   * Sets the given attribute to the given value on the file located by the given path in this
   * view.
   */
  public void setAttribute(
      JimfsPath path, String attribute, Object value, Set<? super LinkOption> options)
      throws IOException {
    File file = lookUpWithLock(path, options)
        .requireExists(path)
        .file();
    store.setAttribute(file, attribute, value);
  }
}
