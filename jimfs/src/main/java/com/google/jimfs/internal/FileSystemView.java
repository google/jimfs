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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.jimfs.attribute.Inode;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

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

  private final File workingDirectory;
  private final JimfsPath workingDirectoryPath;

  /**
   * Creates a new file system view.
   */
  public FileSystemView(JimfsFileStore store,
      File workingDirectory, JimfsPath workingDirectoryPath) {
    this.store = checkNotNull(store);
    this.workingDirectory = checkNotNull(workingDirectory);
    this.workingDirectoryPath = checkNotNull(workingDirectoryPath);
  }

  /**
   * Creates a new file system view identical to the given view but using the given working
   * directory instead.
   */
  private FileSystemView(
      File workingDirectory, JimfsPath workingDirectoryPath, FileSystemView parent) {
    this(parent.store, workingDirectory, workingDirectoryPath);
  }

  /**
   * Returns whether or not this view and the given view belong to the same file system.
   */
  private boolean isSameFileSystem(FileSystemView other) {
    return store == other.store;
  }

  /**
   * Returns the path of the working directory at the time this view was created. Does not reflect
   * changes to the path caused by the directory being moved.
   */
  public JimfsPath getWorkingDirectoryPath() {
    return workingDirectoryPath;
  }

  /**
   * Attempt to lookup the file at the given path.
   */
  private DirectoryEntry lookupWithLock(
      JimfsPath path, Set<? super LinkOption> options) throws IOException {
    store.readLock().lock();
    try {
      return lookup(path, options);
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Looks up the file at the given path without locking.
   */
  private DirectoryEntry lookup(
      JimfsPath path, Set<? super LinkOption> options) throws IOException {
    return store.lookup(workingDirectory, path, options);
  }

  /**
   * Creates a new secure directory stream for the directory located by the given path. The given
   * {@code basePathForStream} is that base path that the returned stream will use. This will be the
   * same as {@code dir} except for streams created relative to another secure stream.
   */
  public JimfsSecureDirectoryStream newSecureDirectoryStream(
      JimfsPath dir,
      DirectoryStream.Filter<? super Path> filter,
      Set<? super LinkOption> options,
      JimfsPath basePathForStream) throws IOException {
    File file = lookupWithLock(dir, options)
        .requireDirectory(dir)
        .file();

    FileSystemView view = new FileSystemView(file, basePathForStream, this);
    return new JimfsSecureDirectoryStream(view, filter);
  }

  /**
   * Returns a snapshot of the entries in the working directory of this view.
   */
  public ImmutableSortedSet<Name> snapshotBaseEntries() {
    ImmutableSortedSet<Name> names;
    store.readLock().lock();
    try {
      names = workingDirectory.directory().snapshot();
      workingDirectory.updateAccessTime();
    } finally {
      store.readLock().unlock();
    }
    return names;
  }

  /**
   * Returns a snapshot mapping the names of each file in the directory at the given path to the
   * last modified time of that file.
   */
  public ImmutableMap<Name, Long> snapshotModifiedTimes(JimfsPath path) throws IOException {
    Map<Name, Long> modifiedTimes = new HashMap<>();

    store.readLock().lock();
    try {
      File dir = lookup(path, Options.NOFOLLOW_LINKS)
          .requireDirectory(path)
          .file();

      for (DirectoryEntry entry : dir.directory()) {
        if (!entry.name().equals(Name.SELF) && !entry.name().equals(Name.PARENT)) {
          long modifiedTime = entry.file().getLastModifiedTime();
          modifiedTimes.put(entry.name(), modifiedTime);
        }
      }

      return ImmutableMap.copyOf(modifiedTimes);
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Returns whether or not the two given paths locate the same file. The second path is located
   * using the given view rather than this file view.
   */
  public boolean isSameFile(
      JimfsPath path, FileSystemView view2, JimfsPath path2) throws IOException {
    if (!isSameFileSystem(view2)) {
      return false;
    }

    store.readLock().lock();
    try {
      File file = lookup(path, Options.FOLLOW_LINKS).orNull();
      File file2 = view2.lookup(path2, Options.FOLLOW_LINKS).orNull();
      return file != null && Objects.equal(file, file2);
    } finally {
      store.readLock().unlock();
    }
  }

  /**
   * Gets the real path to the file located by the given path.
   */
  public JimfsPath toRealPath(
      JimfsPath path, PathService pathService, Set<? super LinkOption> options) throws IOException {
    checkNotNull(path);
    checkNotNull(options);

    store.readLock().lock();
    try {
      DirectoryEntry entry = lookup(path, options)
          .requireExists(path);

      List<Name> names = new ArrayList<>();
      names.add(entry.name());

      // if the result was a root directory, the name for the result is the only name
      if (!entry.file().isRootDirectory()) {
        File file = entry.directory();
        while (true) {
          DirectoryTable fileTable = file.directory();
          names.add(fileTable.name());
          File parent = fileTable.parent();
          if (file.isRootDirectory()) {
            // file is a root directory
            break;
          }
          file = parent;
        }
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
  public File createDirectory(JimfsPath path, FileAttribute<?>... attrs) throws IOException {
    return createFile(path, store.createDirectory(), false, attrs);
  }

  /**
   * Creates a new symbolic link at the given path with the given target. The given attributes will
   * be set on the new file if possible.
   */
  public File createSymbolicLink(
      JimfsPath path, JimfsPath target, FileAttribute<?>... attrs) throws IOException {
    return createFile(path, store.createSymbolicLink(target), false, attrs);
  }

  /**
   * Creates a new file at the given path if possible, using the given factory to create and store
   * the file. Returns the key of the new file. If {@code allowExisting} is {@code true} and a file
   * already exists at the given path, returns the key of that file. Otherwise, throws {@link
   * FileAlreadyExistsException}.
   */
  private File createFile(JimfsPath path, Supplier<File> fileSupplier,
      boolean allowExisting, FileAttribute<?>... attrs) throws IOException {
    checkNotNull(path);
    checkNotNull(fileSupplier);

    Name name = path.name();

    store.writeLock().lock();
    try {
      DirectoryEntry entry = lookup(path, Options.NOFOLLOW_LINKS);

      if (entry.exists()) {
        if (allowExisting) {
          // currently can only happen if getOrCreateFile doesn't find the file with the read lock
          // and then the file is created between when it releases the read lock and when it
          // acquires the write lock; so, very unlikely
          return entry.file();
        } else {
          throw new FileAlreadyExistsException(path.toString());
        }
      }

      File parent = entry.directory();

      File newFile = fileSupplier.get();
      store.setInitialAttributes(newFile, attrs);
      parent.directory().link(name, newFile);
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
  public File getOrCreateRegularFile(
      JimfsPath path, Set<OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    checkNotNull(path);
    boolean createNew = options.contains(CREATE_NEW);
    boolean create = createNew || options.contains(CREATE);

    // assume no file exists at the path if CREATE_NEW was specified
    if (!createNew) {
      // otherwise try to just lookup with a read lock so as to avoid unnecessary write lock
      // while it could make sense to just look up with a write lock if we're in CREATE mode to
      // avoid the chance of needing to do 2 lookups, the fact that all calls to openOutputStream
      // that don't provide any options are automatically in CREATE mode make me not want to
      store.readLock().lock();
      try {
        DirectoryEntry entry = lookup(path, options);
        if (entry.exists()) {
          File file = entry.file();
          if (!file.isRegularFile()) {
            throw new FileSystemException(path.toString(), null, "not a regular file");
          }

          return open(file, options);
        } else if (!create) {
          // if we aren't in create mode and no file was found, throw
          throw new NoSuchFileException(path.toString());
        }
      } finally {
        store.readLock().unlock();
      }
    }

    // otherwise, we're in create mode and there was no file, so try to create the file, using the
    // value of createNew to tell the create method whether or not it should allow returning the
    // existing key if the file already exists when it tries to create it
    store.writeLock().lock();
    try {
      File file = createFile(path, store.createRegularFile(), !createNew, attrs);
      // the file already existed but was not a regular file
      if (!file.isRegularFile()) {
        throw new FileSystemException(path.toString(), null, "not a regular file");
      }
      return open(file, options);
    } finally {
      store.writeLock().unlock();
    }
  }

  private static File open(File regularFile, Set<OpenOption> options) {
    if (options.contains(TRUNCATE_EXISTING) && options.contains(WRITE)) {
      ByteStore byteStore = regularFile.bytes();
      byteStore.writeLock().lock();
      try {
        byteStore.truncate(0);
      } finally {
        byteStore.writeLock().unlock();
      }
    }

    // must be opened while holding a file store lock to ensure no race between opening and
    // deleting the file
    regularFile.bytes().opened();

    return regularFile;
  }

  /**
   * Returns the target of the symbolic link at the given path.
   */
  public JimfsPath readSymbolicLink(JimfsPath path) throws IOException {
    File symbolicLink = lookup(path, Options.NOFOLLOW_LINKS)
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
    lookup(path, Options.FOLLOW_LINKS).requireExists(path);
  }

  /**
   * Creates a hard link at the given link path to the regular file at the given path. The existing
   * file must exist and must be a regular file. The given file system view must belong to the same
   * file system as this view.
   */
  public void link(
      JimfsPath link, FileSystemView existingView, JimfsPath existing) throws IOException {
    checkNotNull(link);
    checkNotNull(existingView);
    checkNotNull(existing);

    if (!isSameFileSystem(existingView)) {
      throw new FileSystemException(link.toString(), existing.toString(),
          "can't link: source and target are in different file system instances");
    }

    Name linkName = link.name();

    // existingView is in the same file system, so just one lock is needed
    store.writeLock().lock();
    try {
      // we do want to follow links when finding the existing file
      File existingFile = existingView.lookup(existing, Options.FOLLOW_LINKS)
          .requireExists(existing)
          .file();
      if (!existingFile.isRegularFile()) {
        throw new FileSystemException(link.toString(), existing.toString(),
            "can't link: not a regular file");
      }

      File linkParent = lookup(link, Options.NOFOLLOW_LINKS)
          .requireDoesNotExist(link)
          .directory();

      linkParent.directory().link(linkName, existingFile);
      linkParent.updateModifiedTime();
    } finally {
      store.writeLock().unlock();
    }
  }

  /**
   * Deletes the file at the given absolute path.
   */
  public void deleteFile(JimfsPath path) throws IOException {
    deleteFile(path, DeleteMode.ANY);
  }

  /**
   * Deletes the file at the given absolute path.
   */
  public void deleteFile(JimfsPath path, DeleteMode deleteMode) throws IOException {
    store.writeLock().lock();
    try {
      DirectoryEntry entry = lookup(path, Options.NOFOLLOW_LINKS)
          .requireExists(path);
      delete(entry, deleteMode, path);
    } finally {
      store.writeLock().unlock();
    }
  }

  /**
   * Deletes the file with the given name and key from the given parent directory.
   */
  private void delete(DirectoryEntry entry,
      DeleteMode deleteMode, JimfsPath pathForException) throws IOException {
    File parent = entry.directory();
    File file = entry.file();

    checkDeletable(file, deleteMode, pathForException);
    parent.directory().unlink(entry.name());
    parent.updateModifiedTime();

    if (file.links() == 0) {
      file.content().delete();
    }
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
  private void checkDeletable(
      File file, DeleteMode mode, Path path) throws IOException {
    if (file.isRootDirectory()) {
      throw new FileSystemException(
          path.toString(), null, "can't delete root directory");
    }

    if (file.isDirectory()) {
      if (mode == DeleteMode.NON_DIRECTORY_ONLY) {
        throw new FileSystemException(
            path.toString(), null, "can't delete: is a directory");
      }

      checkEmpty(file, path);
    } else if (mode == DeleteMode.DIRECTORY_ONLY) {
      throw new FileSystemException(
          path.toString(), null, "can't delete: is not a directory");
    }

    if (file == workingDirectory && !path.isAbsolute()) {
      // this is weird, but on Unix at least, the file system seems to be happy to delete the
      // working directory if you give the absolute path to it but fail if you use a relative path
      // that resolves to the working directory (e.g. "" or ".")
      throw new FileSystemException(
          path.toString(), null, "invalid argument");
    }
  }

  /**
   * Checks that the directory identified by the given key is empty, throwing {@link
   * DirectoryNotEmptyException} if it isn't.
   */
  private void checkEmpty(File file, Path pathForException) throws FileSystemException {
    if (!file.directory().isEmpty()) {
      throw new DirectoryNotEmptyException(pathForException.toString());
    }
  }

  /**
   * Copies or moves the file at the given source path to the given dest path.
   */
  public void copy(JimfsPath source, FileSystemView destView, JimfsPath dest,
      Set<CopyOption> options, boolean move) throws IOException {
    checkNotNull(source);
    checkNotNull(destView);
    checkNotNull(dest);
    checkNotNull(options);

    boolean sameFileSystem = isSameFileSystem(destView);

    Name sourceName = source.name();
    Name destName = dest.name();

    lockBoth(store.writeLock(), destView.store.writeLock());
    try {
      DirectoryEntry sourceEntry = lookup(source, options)
          .requireExists(source);
      DirectoryEntry destEntry = destView.lookup(dest, Options.NOFOLLOW_LINKS);

      File sourceParent = sourceEntry.directory();
      File sourceFile = sourceEntry.file();

      File destParent = destEntry.directory();

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
        // identity because files from 2 file system instances could have the same ID
        // TODO(cgdecker): consider changing this to make the IDs unique per VM
        if (destEntry.file() == sourceFile) {
          return;
        } else if (options.contains(REPLACE_EXISTING)) {
          destView.delete(destEntry, DeleteMode.ANY, dest);
        } else {
          throw new FileAlreadyExistsException(dest.toString());
        }
      }

      // can only do an actual move within one file system instance
      // otherwise we have to copy and delete
      if (move && sameFileSystem) {
        sourceParent.directory().unlink(sourceName);
        sourceParent.updateModifiedTime();

        destParent.directory().link(destName, sourceFile);
        destParent.updateModifiedTime();
      } else {
        // copy
        boolean copyAttributes = options.contains(COPY_ATTRIBUTES) && !move;
        File copy = destView.store.copy(sourceFile, copyAttributes);
        destParent.directory().link(destName, copy);
        destParent.updateModifiedTime();

        if (move) {
          store.copyBasicAttributes(sourceFile, copy);
          delete(sourceEntry, DeleteMode.ANY, source);
        }
      }
    } finally {
      destView.store.writeLock().unlock();
      store.writeLock().unlock();
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
  private void checkNotAncestor(
      File source, File destParent, FileSystemView destView) throws IOException {
    // if dest is not in the same file system, it couldn't be in source's subdirectories
    if (!isSameFileSystem(destView)) {
      return;
    }

    File current = destParent;
    while (true) {
      if (current.equals(source)) {
        throw new IOException(
            "invalid argument: can't move directory into a subdirectory of itself");
      }

      if (current.isRootDirectory()) {
        return;
      } else {
        current = current.directory().parent();
      }
    }
  }

  /**
   * Returns a file attribute view for the given path in this view.
   */
  public <V extends FileAttributeView> V getFileAttributeView(
      final JimfsPath path, Class<V> type, final Set<? super LinkOption> options) {
    return store.getFileAttributeView(new Inode.Lookup() {
      @Override
      public Inode lookup() throws IOException {
        return lookupWithLock(path, options)
            .requireExists(path)
            .file();
      }
    }, type);
  }

  /**
   * Reads attributes of the file located by the given path in this view as an object.
   */
  public <A extends BasicFileAttributes> A readAttributes(
      JimfsPath path, Class<A> type, Set<? super LinkOption> options) throws IOException {
    File file = lookupWithLock(path, options)
        .requireExists(path)
        .file();
    return store.readAttributes(file, type);
  }

  /**
   * Reads attributes of the file located by the given path in this view as a map.
   */
  public Map<String, Object> readAttributes(
      JimfsPath path, String attributes, Set<? super LinkOption> options) throws IOException {
    File file = lookupWithLock(path, options)
        .requireExists(path)
        .file();
    return store.readAttributes(file, attributes);
  }

  /**
   * Sets the given attribute to the given value on the file located by the given path in this
   * view.
   */
  public void setAttribute(JimfsPath path, String attribute, Object value,
      Set<? super LinkOption> options) throws IOException {
    File file = lookupWithLock(path, options)
        .requireExists(path)
        .file();
    store.setAttribute(file, attribute, value);
  }
}
