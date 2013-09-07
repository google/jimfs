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
import static com.google.jimfs.internal.LinkHandling.FOLLOW_LINKS;
import static com.google.jimfs.internal.LinkHandling.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.jimfs.internal.file.ByteStore;
import com.google.jimfs.internal.file.DirectoryTable;
import com.google.jimfs.internal.file.File;
import com.google.jimfs.internal.file.FileProvider;
import com.google.jimfs.internal.path.JimfsPath;
import com.google.jimfs.internal.path.PathService;
import com.google.jimfs.path.Name;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import javax.annotation.Nullable;

/**
 * Structure used for lookups and modification of the file hierarchy. A file tree has a base
 * directory. All operations on the tree that are given a <i>relative</i> path resolve that path
 * relative to that actual directory, even if the directory has been moved.
 *
 * <p>By default, a file system has two such file trees:
 *
 * <p>The first is the <i>super root</i>, which is a file tree representing the super root
 * directory at the very base of the file system. This directory, which doesn't exist from a user
 * perspective, links the names of root directories (e.g. "/" or "C:\") to those directories. All
 * operations on any file tree that are passed an <i>absolute</i> path will just hand the
 * operation off to the super root tree.
 *
 * <p>The second is the <i>working directory</i>, which is a file tree representing the tree
 * starting at, of course, the file system's working directory.
 *
 * <p>There is also a third use for file trees: {@link SecureDirectoryStream}. A secure directory
 * stream primarily acts as a view of an actual directory in the file system, not the path to that
 * directory. It provides methods for a variety of operations that work relative to that actual
 * directory. This class operates on the same model and provides a basis for implementing a secure
 * directory stream.
 *
 * @author Colin Decker
 */
public final class FileTree {

  private final File base;
  private final JimfsPath basePath;

  private final ReadWriteLock lock;
  private final FileTree superRoot;
  private final JimfsFileStore store;
  private final PathService pathService;
  private final LookupService lookupService;

  /**
   * Creates a new file tree with the given base, using the given services.
   */
  public FileTree(File base, JimfsPath basePath, @Nullable FileTree superRoot,
      ReadWriteLock lock, JimfsFileStore store,
      PathService pathService, LookupService lookupService) {
    this.base = checkNotNull(base);
    this.basePath = checkNotNull(basePath);
    this.superRoot = superRoot == null ? this : superRoot;

    this.lock = checkNotNull(lock);
    this.store = checkNotNull(store);
    this.pathService = checkNotNull(pathService);
    this.lookupService = checkNotNull(lookupService);
  }

  /**
   * Returns the read lock.
   */
  public Lock readLock() {
    return lock.readLock();
  }

  /**
   * Returns the write lock.
   */
  public Lock writeLock() {
    return lock.writeLock();
  }

  /**
   * Returns the path of the directory at the base of this tree at the time the tree was created.
   * Does not reflect changes to the path of the tree caused by the directory being moved.
   */
  public JimfsPath getBasePath() {
    return basePath;
  }

  /**
   * Returns the super root tree for the file system.
   */
  public FileTree getSuperRoot() {
    return superRoot;
  }

  /**
   * Returns whether or not this tree and the given tree are in the same file system.
   */
  private boolean isSameFileSystem(FileTree other) {
    return superRoot == other.superRoot;
  }

  /**
   * Returns the directory that is the base of this tree.
   */
  public File base() {
    return base;
  }

  /**
   * Attempt to lookup the file at the given path.
   */
  public LookupResult lookup(JimfsPath path, LinkHandling linkHandling) throws IOException {
    return lookupService.lookup(this, path, linkHandling);
  }

  /**
   * Returns a {@link FileProvider} that does a lookup of the given path in this given tree, using
   * the given link handling option.
   */
  public FileProvider lookupProvider(final JimfsPath path, final LinkHandling linkHandling) {
    checkNotNull(path);
    checkNotNull(linkHandling);
    return new FileProvider() {
      @Override
      public File getFile() throws IOException {
        return lookup(path, linkHandling)
            .requireFound(path)
            .file();
      }
    };
  }

  /**
   * Gets the regular file at the given path, throwing an exception if the file isn't a regular
   * file. If the CREATE or CREATE_NEW option is specified, the file will be created if it does not
   * exist.
   */
  public File getRegularFile(JimfsPath path,
      Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    File file = getOrCreateRegularFile(path, options, attrs);
    if (!file.isRegularFile()) {
      throw new FileSystemException(path.toString(), null, "not a regular file");
    }

    return file;
  }

  /**
   * Creates a new secure directory stream for the directory located by the given path. The given
   * {@code basePathForStream} is that base path that the returned stream will use. This will be
   * the same as {@code dir} except for streams created relative to another secure stream.
   */
  public JimfsSecureDirectoryStream newSecureDirectoryStream(JimfsPath dir,
      DirectoryStream.Filter<? super Path> filter, LinkHandling linkHandling,
      JimfsPath basePathForStream) throws IOException {
    File file = lookup(dir, linkHandling)
        .requireDirectory(dir)
        .file();

    FileTree tree = new FileTree(file, basePathForStream,
        superRoot, lock, store, pathService, lookupService);
    return new JimfsSecureDirectoryStream(tree, filter);
  }

  /**
   * Returns a snapshot of the entries in the base directory of this tree.
   */
  public ImmutableSortedSet<String> snapshotBaseEntries() {
    ImmutableSortedSet<Name> names;
    readLock().lock();
    try {
      DirectoryTable table = base.content();
      names = table.snapshot();
      base.updateAccessTime();
    } finally {
      readLock().unlock();
    }

    ImmutableSortedSet.Builder<String> builder = ImmutableSortedSet.naturalOrder();
    for (Name name : names) {
      builder.add(name.toString());
    }
    return builder.build();
  }

  /**
   * Returns whether or not the two given paths locate the same file. The second path is located
   * using the given file tree rather than this file tree.
   */
  public boolean isSameFile(JimfsPath path, FileTree tree2, JimfsPath path2) throws IOException {
    if (!isSameFileSystem(tree2)) {
      return false;
    }

    readLock().lock();
    try {
      File file = lookup(path, FOLLOW_LINKS).orNull();
      File file2 = tree2.lookup(path2, FOLLOW_LINKS).orNull();
      return file != null && Objects.equal(file, file2);
    } finally {
      readLock().unlock();
    }
  }

  /**
   * Gets the real path to the file located by the given path.
   */
  public JimfsPath toRealPath(JimfsPath path, LinkHandling linkHandling) throws IOException {
    checkNotNull(path);
    checkNotNull(linkHandling);

    readLock().lock();
    try {
      LookupResult lookupResult = lookup(path, linkHandling)
          .requireFound(path);

      List<Name> names = new ArrayList<>();
      names.add(lookupResult.name());

      // if the result was a root directory, the name for the result is the only name
      if (!lookupResult.file().isRootDirectory()) {
        File file = lookupResult.parent();
        while (!file.isRootDirectory()) {
          DirectoryTable fileTable = file.content();
          names.add(fileTable.name());
          file = fileTable.parent();
        }

        // must handle root directory separately
        File superRootBase = superRoot.base();
        DirectoryTable superRootBaseTable = superRootBase.content();
        names.add(superRootBaseTable.getName(file));
      }

      // names are ordered last to first in the list, so get the reverse view
      List<Name> reversed = Lists.reverse(names);
      Name root = reversed.remove(0);
      return pathService.createPath(root, reversed);
    } finally {
      readLock().unlock();
    }
  }

  public File createDirectory(JimfsPath path, FileAttribute<?>... attrs) throws IOException {
    return createFile(path, store.directorySupplier(attrs), false);
  }

  public File createSymbolicLink(
      JimfsPath path, JimfsPath target, FileAttribute<?>... attrs) throws IOException {
    return createFile(path, store.symbolicLinkSupplier(target, attrs), false);
  }

  /**
   * Creates a new file at the given path if possible, using the given factory to create and
   * store the file. Returns the key of the new file. If {@code allowExisting} is {@code true} and
   * a file already exists at the given path, returns the key of that file. Otherwise, throws
   * {@link FileAlreadyExistsException}.
   */
  public File createFile(
      JimfsPath path, Supplier<File> fileSupplier, boolean allowExisting) throws IOException {
    checkNotNull(path);
    checkNotNull(fileSupplier);

    Name name = path.name();

    writeLock().lock();
    try {
      LookupResult result = lookup(path, NOFOLLOW_LINKS)
          .requireParentFound(path);

      if (result.found()) {
        if (allowExisting) {
          // currently can only happen if getOrCreateFile doesn't find the file with the read lock
          // and then the file is created between when it releases the read lock and when it
          // acquires the write lock; so, very unlikely
          return result.file();
        } else {
          throw new FileAlreadyExistsException(path.toString());
        }
      }

      File parent = result.parent();
      DirectoryTable parentTable = parent.content();

      File newFile = fileSupplier.get();
      parentTable.link(name, newFile);
      parent.updateModifiedTime();

      if (newFile.isDirectory()) {
        linkSelfAndParent(newFile, parent);
      }
      return newFile;
    } finally {
      writeLock().unlock();
    }
  }

  private void linkSelfAndParent(File self, File parent) {
    DirectoryTable table = self.content();
    table.linkSelf(self);
    table.linkParent(parent);
  }

  private void unlinkSelfAndParent(File dir) {
    DirectoryTable table = dir.content();
    table.unlinkSelf();
    table.unlinkParent();
  }

  /**
   * Gets the regular file at the given path, creating it if it doesn't exist and the given options
   * specify that it should be created.
   */
  public File getOrCreateRegularFile(JimfsPath path,
      Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    checkNotNull(path);
    LinkHandling linkHandling = LinkHandling.fromOptions(options);
    boolean createNew = options.contains(CREATE_NEW);
    boolean create = createNew || options.contains(CREATE);

    // assume no file exists at the path if CREATE_NEW was specified
    if (!createNew) {
      // otherwise try to just lookup with a read lock so as to avoid unnecessary write lock
      // while it could make sense to just look up with a write lock if we're in CREATE mode to
      // avoid the chance of needing to do 2 lookups, the fact that all calls to openOutputStream
      // that don't provide any options are automatically in CREATE mode make me not want to
      readLock().lock();
      try {
        LookupResult result = lookup(path, linkHandling);
        if (result.found()) {
          File file = result.file();
          if (!file.isRegularFile()) {
            throw new FileSystemException(path.toString(), null, "not a regular file");
          }

          return truncateIfNeeded(file, options);
        } else if (!create) {
          // if we aren't in create mode and no file was found, throw
          throw new NoSuchFileException(path.toString());
        }
      } finally {
        readLock().unlock();
      }
    }

    // otherwise, we're in create mode and there was no file, so try to create the file, using the
    // value of createNew to tell the create method whether or not it should allow returning the
    // existing key if the file already exists when it tries to create it
    writeLock().lock();
    try {
      File file = createFile(path, store.regularFileSupplier(attrs), !createNew);
      // the file already existed but was not a regular file
      if (!file.isRegularFile()) {
        throw new FileSystemException(path.toString(), null, "not a regular file");
      }
      return truncateIfNeeded(file, options);
    } finally {
      writeLock().unlock();
    }
  }

  private static File truncateIfNeeded(File regularFile, Set<? extends OpenOption> options) {
    if (options.contains(TRUNCATE_EXISTING)) {
      ByteStore store = regularFile.content();
      store.truncate(0);
    }

    return regularFile;
  }

  /**
   * Creates a hard link at the given link path to the file at the given existing path in the
   * given file tree. The existing file must exist and must be a regular file. The given file tree
   * must be in the same file system as this tree.
   */
  public void link(
      JimfsPath link, FileTree existingTree, JimfsPath existing) throws IOException {
    checkNotNull(link);
    checkNotNull(existingTree);
    checkNotNull(existing);

    if (!isSameFileSystem(existingTree)) {
      throw new FileSystemException(link.toString(), existing.toString(),
          "can't link: source and target are in different file system instances");
    }

    Name linkName = link.name();

    // targetTree is in the same file system, so the lock applies for both trees
    writeLock().lock();
    try {
      // we do want to follow links when finding the existing file
      File existingFile = existingTree.lookup(existing, FOLLOW_LINKS)
          .requireFound(existing)
          .file();
      if (!existingFile.isRegularFile()) {
        throw new FileSystemException(link.toString(), existing.toString(),
            "can't link: not a regular file");
      }

      File linkParent = lookup(link, NOFOLLOW_LINKS)
          .requireParentFound(link)
          .requireNotFound(link)
          .parent();

      DirectoryTable linkParentTable = linkParent.content();
      linkParentTable.link(linkName, existingFile);
      linkParent.updateModifiedTime();
    } finally {
      writeLock().unlock();
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
    Name name = path.name();

    writeLock().lock();
    try {
      File parent = lookup(path, NOFOLLOW_LINKS)
          .requireFound(path)
          .parent();

      delete(parent, name, deleteMode, path);
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Deletes the file with the given name and key from the given parent directory.
   */
  private void delete(File parent, Name name,
      DeleteMode deleteMode, JimfsPath pathForException) throws IOException {
    DirectoryTable parentTable = parent.content();
    File file = parentTable.get(name);
    assert file != null;

    checkDeletable(file, deleteMode, pathForException);
    parentTable.unlink(name);
    parent.updateModifiedTime();
    if (file.isDirectory()) {
      unlinkSelfAndParent(file);
    }
  }

  /**
   * Mode for deleting. Determines what types of files can be deleted.
   */
  public enum DeleteMode {
    /** Delete any file. */
    ANY,
    /** Only delete non-directory files. */
    NON_DIRECTORY_ONLY,
    /** Only delete directory files. */
    DIRECTORY_ONLY
  }

  /**
   * Checks that the given file can be deleted, throwing an exception if it can't.
   */
  private void checkDeletable(
      File file, DeleteMode mode, Path pathForException) throws IOException {
    if (file.isRootDirectory()) {
      throw new FileSystemException(
          pathForException.toString(), null, "can't delete root directory");
    } if (file.isDirectory()) {
      if (mode == DeleteMode.NON_DIRECTORY_ONLY) {
        throw new FileSystemException(
            pathForException.toString(), null, "can't delete: is a directory");
      }

      checkEmpty(file, pathForException);
    } else if (mode == DeleteMode.DIRECTORY_ONLY) {
      throw new FileSystemException(
          pathForException.toString(), null, "can't delete: is not a directory");
    }
  }

  /**
   * Checks that the directory identified by the given key is empty, throwing
   * {@link DirectoryNotEmptyException} if it isn't.
   */
  private void checkEmpty(File file, Path pathForException) throws FileSystemException {
    DirectoryTable table = file.content();
    if (!table.isEmpty()) {
      throw new DirectoryNotEmptyException(pathForException.toString());
    }
  }

  /**
   * Moves the file at the given source path in this tree to the given dest path in the given tree.
   */
  public void moveFile(JimfsPath source, FileTree destTree, JimfsPath dest,
      Set<CopyOption> options) throws IOException {
    copy(source, destTree, dest, true, options);
  }

  /**
   * Copies the file at the given source path to the given dest path.
   */
  public void copyFile(JimfsPath source, FileTree destTree, JimfsPath dest,
      Set<CopyOption> options) throws IOException {
    if (options.contains(ATOMIC_MOVE)) {
      throw new UnsupportedOperationException("ATOMIC_MOVE");
    }
    copy(source, destTree, dest, false, options);
  }

  /**
   * Copies or moves the file at the given source path to the given dest path.
   */
  private void copy(JimfsPath source, FileTree destTree, JimfsPath dest, boolean move,
      Set<CopyOption> options) throws IOException {
    checkNotNull(source);
    checkNotNull(destTree);
    checkNotNull(dest);
    checkNotNull(options);

    boolean sameFileSystem = isSameFileSystem(destTree);

    LinkHandling linkHandling = move ? NOFOLLOW_LINKS : LinkHandling.fromOptions(options);

    Name sourceName = source.name();
    Name destName = dest.name();

    lockBoth(writeLock(), destTree.writeLock());
    try {
      LookupResult sourceLookup = lookup(source, linkHandling)
          .requireFound(source);
      LookupResult destLookup = destTree.lookup(dest, NOFOLLOW_LINKS)
          .requireParentFound(dest);

      File sourceParent = sourceLookup.parent();
      DirectoryTable sourceParentTable = sourceLookup.parent().content();
      File sourceFile = sourceLookup.file();

      File destParent = destLookup.parent();
      DirectoryTable destParentTable = destParent.content();

      if (move && sourceFile.isDirectory()) {
        if (sameFileSystem) {
          checkMovable(sourceFile, source);
          checkNotAncestor(sourceFile, destParent, destTree);
        } else {
          // move to another file system is accomplished by copy-then-delete, so the source file
          // must be deletable to be moved
          checkDeletable(sourceFile, DeleteMode.ANY, source);
        }
      }

      if (destLookup.found()) {
        // identity because files from 2 file system instances could have the same ID
        // TODO(cgdecker): consider changing this to make the IDs unique per VM
        if (destLookup.file() == sourceFile) {
          return;
        } else if (options.contains(REPLACE_EXISTING)) {
          destTree.delete(destParent, destName, DeleteMode.ANY, dest);
        } else {
          throw new FileAlreadyExistsException(dest.toString());
        }
      }

      // can only do an actual move within one file system instance
      // otherwise we have to copy and delete
      if (move && sameFileSystem) {
        sourceParentTable.unlink(sourceName);
        sourceParent.updateModifiedTime();

        destParentTable.link(destName, sourceFile);
        destParent.updateModifiedTime();

        if (sourceFile.isDirectory()) {
          unlinkSelfAndParent(sourceFile);
          linkSelfAndParent(sourceFile, destParent);
        }
      } else {
        // copy
        File copy = destTree.store.copy(sourceFile);
        destParentTable.link(destName, copy);
        destParent.updateModifiedTime();

        if (copy.isDirectory()) {
          linkSelfAndParent(copy, destParent);
        }

        if (move) {
          copyBasicAttributes(sourceFile, destTree, copy);
          delete(sourceParent, sourceName, DeleteMode.ANY, source);
        }
      }
    } finally {
      destTree.writeLock().unlock();
      writeLock().unlock();
    }
  }

  private void checkMovable(File file, JimfsPath path) throws FileSystemException {
    if (file.isRootDirectory()) {
      throw new FileSystemException(path.toString(), null, "can't move root directory");
    }
  }

  private void copyBasicAttributes(File source, FileTree destTree, File dest) throws IOException {
    BasicFileAttributes sourceAttributes =
        store.readAttributes(source, BasicFileAttributes.class);
    BasicFileAttributeView destAttributeView = destTree.store.getFileAttributeView(
        FileProvider.ofFile(dest), BasicFileAttributeView.class);

    assert destAttributeView != null;

    destAttributeView.setTimes(
        sourceAttributes.lastModifiedTime(),
        sourceAttributes.lastAccessTime(),
        sourceAttributes.creationTime());
  }

  /**
   * Acquires both write locks in a way that attempts to avoid the possibility of deadlock. Note
   * that typically (when only one file system instance is involved), both locks will actually be
   * the same lock and there will be no issue at all.
   */
  private static void lockBoth(Lock sourceWriteLock, Lock destWriteLock) {
    while (true) {
      sourceWriteLock.lock();
      if (destWriteLock.tryLock()) {
        return;
      }
      sourceWriteLock.unlock();
    }
  }

  /**
   * Checks that source is not an ancestor of dest, throwing an exception if it is.
   */
  private void checkNotAncestor(
      File source, File destParent, FileTree destTree) throws IOException {
    // if dest is not in the same file system, it couldn't be in source's subdirectories
    if (!isSameFileSystem(destTree)) {
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
        DirectoryTable table = current.content();
        current = table.parent();
      }
    }
  }

  /**
   * Returns a file attribute view for the given path in this tree.
   */
  public <V extends FileAttributeView> V getFileAttributeView(
      JimfsPath path, Class<V> type, LinkHandling linkHandling) {
    return store.getFileAttributeView(lookupProvider(path, linkHandling), type);
  }

  /**
   * Reads attributes of the file located by the given path in this tree as an object.
   */
  public <A extends BasicFileAttributes> A readAttributes(
      JimfsPath path, Class<A> type, LinkHandling linkHandling) throws IOException {
    File file = lookup(path, linkHandling)
        .requireFound(path)
        .file();
    return store.readAttributes(file, type);
  }

  /**
   * Reads attributes of the file located by the given path in this tree as a map.
   */
  public Map<String, Object> readAttributes(
      JimfsPath path, String attributes, LinkHandling linkHandling) throws IOException {
    File file = lookup(path, linkHandling)
        .requireFound(path)
        .file();
    return store.readAttributes(file, attributes);
  }

  /**
   * Sets the given attribute to the given value on the file located by the given path in this tree.
   */
  public void setAttribute(JimfsPath path, String attribute, Object value,
      LinkHandling linkHandling) throws IOException {
    File file = lookup(path, linkHandling)
        .requireFound(path)
        .file();
    store.setAttribute(file, attribute, value);
  }
}
