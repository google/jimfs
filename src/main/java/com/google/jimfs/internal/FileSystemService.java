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
import com.google.jimfs.common.IoSupplier;

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
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service implementing most operations for a file system. A file system service has two root
 * directories. The first, called the <i>super root</i> is the directory containing the actual root
 * directories of the file system. It acts as the base for operations on absolute paths. The second,
 * just called the <i>relative root</i>, is the base for operations on relative paths.
 *
 * <p>By default, a file system has one file system service. This service's relative root is the
 * <i>working directory</i> of the file system. In addition, a file system may have any number of
 * additional file system services for {@link SecureDirectoryStream} instances. These services have
 * the stream's directory as their relative root, allowing for operations relative to that directory
 * object.
 *
 * @author Colin Decker
 */
final class FileSystemService {

  private final File superRoot;
  private final File workingDirectory;
  private final JimfsPath workingDirectoryPath;

  private final JimfsFileStore fileStore;
  private final PathService pathService;

  private final LookupService lookupService;
  private final ResourceManager resourceManager;

  private final ReadWriteLock lock;

  /**
   * Creates a new file system service using the given services.
   */
  public FileSystemService(File superRoot, File workingDirectory, JimfsPath workingDirectoryPath,
      JimfsFileStore fileStore, PathService pathService) {
    this(superRoot, workingDirectory, workingDirectoryPath, fileStore, pathService,
        new LookupService(), new ResourceManager(), new ReentrantReadWriteLock());
  }

  /**
   * Creates a new file system service using the same super root and services as the given service,
   * but using the given working directory.
   */
  private FileSystemService(
      File workingDirectory, JimfsPath workingDirectoryPath, FileSystemService parent) {
    this(parent.superRoot, workingDirectory, workingDirectoryPath,
        parent.fileStore, parent.pathService, parent.lookupService,
        parent.resourceManager, parent.lock);
  }

  private FileSystemService(File superRoot, File workingDirectory, JimfsPath workingDirectoryPath,
      JimfsFileStore fileStore, PathService pathService, LookupService lookupService,
      ResourceManager resourceManager, ReadWriteLock lock) {
    this.superRoot = checkNotNull(superRoot);
    this.workingDirectory = checkNotNull(workingDirectory);
    this.workingDirectoryPath = checkNotNull(workingDirectoryPath);

    this.fileStore = checkNotNull(fileStore);
    this.pathService = checkNotNull(pathService);
    this.lookupService = checkNotNull(lookupService);
    this.resourceManager = checkNotNull(resourceManager);

    this.lock = checkNotNull(lock);
  }

  private Lock readLock() {
    return lock.readLock();
  }

  private Lock writeLock() {
    return lock.writeLock();
  }

  /**
   * Returns whether or not this service and the given service belong to the same file system.
   */
  private boolean isSameFileSystem(FileSystemService other) {
    return superRoot == other.superRoot;
  }

  /**
   * Returns the super root directory for the file system.
   */
  public File getSuperRoot() {
    return superRoot;
  }

  /**
   * Returns the working directory for this service.
   */
  public File getWorkingDirectory() {
    return workingDirectory;
  }

  /**
   * Returns the path of the working directory at the time this service was created. Does not
   * reflect changes to the path caused by the directory being moved.
   */
  public JimfsPath getWorkingDirectoryPath() {
    return workingDirectoryPath;
  }

  /**
   * Returns the path service for the file system.
   */
  public PathService paths() {
    return pathService;
  }

  /**
   * Returns the file store for the file system.
   */
  public JimfsFileStore fileStore() {
    return fileStore;
  }

  /**
   * Returns the resource manager for the file system.
   */
  public ResourceManager resourceManager() {
    return resourceManager;
  }

  /**
   * Attempt to lookup the file at the given path.
   */
  public LookupResult lookup(JimfsPath path, LinkHandling linkHandling) throws IOException {
    readLock().lock();
    try {
      return lookupInternal(path, linkHandling);
    } finally {
      readLock().unlock();
    }
  }

  /**
   * Looks up the file at the given path without locking.
   */
  private LookupResult lookupInternal(
      JimfsPath path, LinkHandling linkHandling) throws IOException {
    return lookupService.lookup(this, path, linkHandling);
  }

  /**
   * Returns a supplier that suppliers a file by looking up the given path in this service, using
   * the given link handling option.
   */
  public IoSupplier<File> lookupFileSupplier(
      final JimfsPath path, final LinkHandling linkHandling) {
    checkNotNull(path);
    checkNotNull(linkHandling);
    return new IoSupplier<File>() {
      @Override
      public File get() throws IOException {
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
   * {@code basePathForStream} is that base path that the returned stream will use. This will be the
   * same as {@code dir} except for streams created relative to another secure stream.
   */
  public JimfsSecureDirectoryStream newSecureDirectoryStream(JimfsPath dir,
      DirectoryStream.Filter<? super Path> filter, LinkHandling linkHandling,
      JimfsPath basePathForStream) throws IOException {
    File file = lookup(dir, linkHandling)
        .requireDirectory(dir)
        .file();

    FileSystemService service = new FileSystemService(file, basePathForStream, this);
    return new JimfsSecureDirectoryStream(service, filter);
  }

  /**
   * Returns a snapshot of the entries in the working directory of this service.
   */
  public ImmutableSortedSet<String> snapshotBaseEntries() {
    ImmutableSortedSet<Name> names;
    readLock().lock();
    try {
      DirectoryTable table = workingDirectory.content();
      names = table.snapshot();
      workingDirectory.updateAccessTime();
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
   * Returns a snapshot mapping the names of each file in the directory at the given path to the
   * last modified time of that file.
   */
  public ImmutableMap<Name, Long> snapshotModifiedTimes(JimfsPath path) throws IOException {
    Map<Name, Long> modifiedTimes = new HashMap<>();

    lock.readLock().lock();
    try {
      File dir = lookupInternal(path, LinkHandling.NOFOLLOW_LINKS)
          .requireDirectory(path)
          .file();

      DirectoryTable table = dir.content();
      for (Map.Entry<Name, File> entry : table.asMap().entrySet()) {
        Name name = entry.getKey();
        File file = entry.getValue();

        long modifiedTime = file.getLastModifiedTime();
        modifiedTimes.put(name, modifiedTime);
      }

      return ImmutableMap.copyOf(modifiedTimes);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns whether or not the two given paths locate the same file. The second path is located
   * using the given file service rather than this file service.
   */
  public boolean isSameFile(
      JimfsPath path, FileSystemService service2, JimfsPath path2) throws IOException {
    if (!isSameFileSystem(service2)) {
      return false;
    }

    readLock().lock();
    try {
      File file = lookupInternal(path, FOLLOW_LINKS).orNull();
      File file2 = service2.lookupInternal(path2, FOLLOW_LINKS).orNull();
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
      LookupResult lookupResult = lookupInternal(path, linkHandling)
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
        DirectoryTable superRootTable = superRoot.content();
        names.add(superRootTable.getName(file));
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
    return createFile(path, fileStore.directorySupplier(attrs), false);
  }

  public File createSymbolicLink(
      JimfsPath path, JimfsPath target, FileAttribute<?>... attrs) throws IOException {
    return createFile(path, fileStore.symbolicLinkSupplier(target, attrs), false);
  }

  /**
   * Creates a new file at the given path if possible, using the given factory to create and store
   * the file. Returns the key of the new file. If {@code allowExisting} is {@code true} and a file
   * already exists at the given path, returns the key of that file. Otherwise, throws {@link
   * FileAlreadyExistsException}.
   */
  public File createFile(
      JimfsPath path, Supplier<File> fileSupplier, boolean allowExisting) throws IOException {
    checkNotNull(path);
    checkNotNull(fileSupplier);

    Name name = path.name();

    writeLock().lock();
    try {
      LookupResult result = lookupInternal(path, NOFOLLOW_LINKS)
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
        LookupResult result = lookupInternal(path, linkHandling);
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
      File file = createFile(path, fileStore.regularFileSupplier(attrs), !createNew);
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
    if (options.contains(TRUNCATE_EXISTING) && options.contains(WRITE)) {
      ByteStore store = regularFile.content();
      store.writeLock().lock();
      try {
        store.truncate(0);
      } finally {
        store.writeLock().unlock();
      }
    }

    return regularFile;
  }

  /**
   * Creates a hard link at the given link path to the regular file at the given path. The existing
   * file must exist and must be a regular file. The given file system service must belong to the
   * same file system as this service.
   */
  public void link(
      JimfsPath link, FileSystemService existingService, JimfsPath existing) throws IOException {
    checkNotNull(link);
    checkNotNull(existingService);
    checkNotNull(existing);

    if (!isSameFileSystem(existingService)) {
      throw new FileSystemException(link.toString(), existing.toString(),
          "can't link: source and target are in different file system instances");
    }

    Name linkName = link.name();

    // existingService is in the same file system, so just one lock is needed
    writeLock().lock();
    try {
      // we do want to follow links when finding the existing file
      File existingFile = existingService.lookupInternal(existing, FOLLOW_LINKS)
          .requireFound(existing)
          .file();
      if (!existingFile.isRegularFile()) {
        throw new FileSystemException(link.toString(), existing.toString(),
            "can't link: not a regular file");
      }

      File linkParent = lookupInternal(link, NOFOLLOW_LINKS)
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
    writeLock().lock();
    try {
      LookupResult result = lookupInternal(path, NOFOLLOW_LINKS)
          .requireFound(path);

      File parent = result.parent();
      Name name = result.name();
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
      File file, DeleteMode mode, Path pathForException) throws IOException {
    if (file.isRootDirectory()) {
      throw new FileSystemException(
          pathForException.toString(), null, "can't delete root directory");
    }
    if (file.isDirectory()) {
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
   * Checks that the directory identified by the given key is empty, throwing {@link
   * DirectoryNotEmptyException} if it isn't.
   */
  private void checkEmpty(File file, Path pathForException) throws FileSystemException {
    DirectoryTable table = file.content();
    if (!table.isEmpty()) {
      throw new DirectoryNotEmptyException(pathForException.toString());
    }
  }

  /**
   * Moves the file at the given source path in this service to the given dest path in the given
   * service.
   */
  public void moveFile(JimfsPath source, FileSystemService destService, JimfsPath dest,
      Set<CopyOption> options) throws IOException {
    copy(source, destService, dest, true, options);
  }

  /**
   * Copies the file at the given source path to the given dest path.
   */
  public void copyFile(JimfsPath source, FileSystemService destService, JimfsPath dest,
      Set<CopyOption> options) throws IOException {
    if (options.contains(ATOMIC_MOVE)) {
      throw new UnsupportedOperationException("ATOMIC_MOVE");
    }
    copy(source, destService, dest, false, options);
  }

  /**
   * Copies or moves the file at the given source path to the given dest path.
   */
  private void copy(JimfsPath source, FileSystemService destService, JimfsPath dest, boolean move,
      Set<CopyOption> options) throws IOException {
    checkNotNull(source);
    checkNotNull(destService);
    checkNotNull(dest);
    checkNotNull(options);

    boolean sameFileSystem = isSameFileSystem(destService);

    LinkHandling linkHandling = move ? NOFOLLOW_LINKS : LinkHandling.fromOptions(options);

    Name sourceName = source.name();
    Name destName = dest.name();

    lockBoth(writeLock(), destService.writeLock());
    try {
      LookupResult sourceLookup = lookupInternal(source, linkHandling)
          .requireFound(source);
      LookupResult destLookup = destService.lookupInternal(dest, NOFOLLOW_LINKS)
          .requireParentFound(dest);

      File sourceParent = sourceLookup.parent();
      DirectoryTable sourceParentTable = sourceLookup.parent().content();
      File sourceFile = sourceLookup.file();

      File destParent = destLookup.parent();
      DirectoryTable destParentTable = destParent.content();

      if (move && sourceFile.isDirectory()) {
        if (sameFileSystem) {
          checkMovable(sourceFile, source);
          checkNotAncestor(sourceFile, destParent, destService);
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
          destService.delete(destParent, destName, DeleteMode.ANY, dest);
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
        boolean copyAttributes = options.contains(COPY_ATTRIBUTES) && !move;
        File copy = destService.fileStore.copy(sourceFile, copyAttributes);
        destParentTable.link(destName, copy);
        destParent.updateModifiedTime();

        if (copy.isDirectory()) {
          linkSelfAndParent(copy, destParent);
        }

        if (move) {
          fileStore.copyBasicAttributes(sourceFile, copy);
          delete(sourceParent, sourceName, DeleteMode.ANY, source);
        }
      }
    } finally {
      destService.writeLock().unlock();
      writeLock().unlock();
    }
  }

  private void checkMovable(File file, JimfsPath path) throws FileSystemException {
    if (file.isRootDirectory()) {
      throw new FileSystemException(path.toString(), null, "can't move root directory");
    }
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
      File source, File destParent, FileSystemService destService) throws IOException {
    // if dest is not in the same file system, it couldn't be in source's subdirectories
    if (!isSameFileSystem(destService)) {
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
   * Returns a file attribute view for the given path in this service.
   */
  public <V extends FileAttributeView> V getFileAttributeView(
      JimfsPath path, Class<V> type, LinkHandling linkHandling) {
    return fileStore.getFileAttributeView(lookupFileSupplier(path, linkHandling), type);
  }

  /**
   * Reads attributes of the file located by the given path in this service as an object.
   */
  public <A extends BasicFileAttributes> A readAttributes(
      JimfsPath path, Class<A> type, LinkHandling linkHandling) throws IOException {
    File file = lookup(path, linkHandling)
        .requireFound(path)
        .file();
    return fileStore.readAttributes(file, type);
  }

  /**
   * Reads attributes of the file located by the given path in this service as a map.
   */
  public Map<String, Object> readAttributes(
      JimfsPath path, String attributes, LinkHandling linkHandling) throws IOException {
    File file = lookup(path, linkHandling)
        .requireFound(path)
        .file();
    return fileStore.readAttributes(file, attributes);
  }

  /**
   * Sets the given attribute to the given value on the file located by the given path in this
   * service.
   */
  public void setAttribute(JimfsPath path, String attribute, Object value,
      LinkHandling linkHandling) throws IOException {
    File file = lookup(path, linkHandling)
        .requireFound(path)
        .file();
    fileStore.setAttribute(file, attribute, value);
  }
}
