package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.jimfs.ExceptionHelpers.requireExistsParentDir;
import static com.google.common.io.jimfs.ExceptionHelpers.requireNonNull;
import static com.google.common.io.jimfs.LinkHandling.FOLLOW_LINKS;
import static com.google.common.io.jimfs.LinkHandling.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSortedSet;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import javax.annotation.Nullable;

/**
 * Structure used for lookups and modification of the file hierarchy. A file tree has a base
 * directory identified by a file key. All operations on the tree that are given a <i>relative</i>
 * path resolve that path relative to that actual directory, even if the directory has been moved.
 * Note that iff the directory is deleted, however, all operations will throw an exception when
 * given a relative path.
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
@SuppressWarnings("unused")
final class FileTree {

  private final JimfsFileSystem fs;
  private final LookupService lookupService;
  private final FileStorage storage;

  private final FileKey baseKey;
  private final JimfsPath basePath;

  /**
   * Creates a new file storage with the given root directories. The given supplier is used to
   * generate new file keys for the directories.
   */
  public FileTree(JimfsFileSystem fs, FileKey baseKey, JimfsPath basePath) {
    this.fs = checkNotNull(fs);
    this.storage = fs.getFileStorage();
    this.lookupService = new LookupService(this);
    this.baseKey = checkNotNull(baseKey);
    this.basePath = checkNotNull(basePath);
  }

  /**
   * Returns the read lock.
   */
  public Lock readLock() {
    return storage().readLock();
  }

  /**
   * Returns the write lock.
   */
  public Lock writeLock() {
    return storage().writeLock();
  }

  /**
   * Returns the file system this tree belongs to.
   */
  public JimfsFileSystem getFileSystem() {
    return fs;
  }

  /**
   * Returns the path of the directory at the base of this tree at the time the tree was created.
   * Does not reflect changes to the path of the tree caused by the directory being moved.
   */
  public JimfsPath getBasePath() {
    return basePath;
  }

  /**
   * Returns whether or not this tree is the super root of its file system.
   */
  public boolean isSuperRoot() {
    return this == fs.getSuperRoot();
  }

  /**
   * Returns the super root tree for the file system.
   */
  public FileTree getSuperRoot() {
    return fs.getSuperRoot();
  }

  /**
   * Returns whether or not this tree and the given tree are in the same file system.
   */
  private boolean isSameFileSystem(FileTree other) {
    return fs.equals(other.fs);
  }

  /**
   * Returns the file storage for the file system.
   */
  public FileStorage storage() {
    return storage;
  }

  /**
   * Returns the lookup service for this tree.
   */
  public LookupService getLookupService() {
    return lookupService;
  }

  /**
   * Returns the key of the directory that is the root of this tree.
   */
  public FileKey getBaseKey() {
    return baseKey;
  }

  /**
   * Returns the directory that is the base of this file tree, throwing {@link NoSuchFileException}
   * if it does not exist. Should only be called when holding a lock.
   */
  public DirectoryTable getBaseDir(Path pathForException) throws NoSuchFileException {
    return requireExistsParentDir(storage.getFile(baseKey), pathForException);
  }

  /**
   * Gets the file at the given path, or {@code null} if none exists.
   */
  @Nullable
  public File lookupFile(JimfsPath path, LinkHandling linkHandling) throws IOException {
    readLock().lock();
    try {
      LookupResult result = lookupService.lookup(path, linkHandling);
      return result.isFileFound() ? result.getFile(storage) : null;
    } finally {
      readLock().unlock();
    }
  }

  /**
   * Gets the byte store for the file at the given path, throwing an exception if the file isn't
   * a regular file.
   */
  public ByteStore getByteStore(
      JimfsPath path, Set<? extends OpenOption> options) throws IOException {
    File file = getOrCreateRegularFile(path, options);
    if (!file.isRegularFile()) {
      throw new FileSystemException(path.toString(), null, "not a regular file");
    }

    return file.content();
  }

  /**
   * Creates a new secure directory stream for the directory located by the given path.
   */
  public JimfsSecureDirectoryStream newSecureDirectoryStream(JimfsPath dir,
      DirectoryStream.Filter<? super Path> filter, LinkHandling linkHandling) throws IOException {
    File file = requireNonNull(lookupFile(dir, linkHandling), dir);
    if (!file.isDirectory()) {
      throw new NotDirectoryException(dir.toString());
    }

    JimfsPath newBasePath = dir.isAbsolute() ? dir : basePath.resolve(dir);
    return new JimfsSecureDirectoryStream(new FileTree(fs, file.key(), newBasePath), filter);
  }

  /**
   * Returns a snapshot of the entries in the directory located by the given path.
   */
  public ImmutableSortedSet<Path> snapshotEntries(JimfsPath dir) throws IOException {
    ImmutableSortedSet<String> names;
    readLock().lock();
    try {
      File file = requireNonNull(lookupFile(dir, LinkHandling.NOFOLLOW_LINKS), dir);
      if (!file.isDirectory()) {
        throw new NotDirectoryException(dir.toString());
      }

      DirectoryTable table = file.content();
      names = table.snapshot();
    } finally {
      readLock().unlock();
    }

    ImmutableSortedSet.Builder<Path> builder = ImmutableSortedSet.naturalOrder();
    for (String name : names) {
      builder.add(JimfsPath.name(fs, name));
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
      FileKey key = lookupService.lookup(path, FOLLOW_LINKS).orNull();
      FileKey key2 = tree2.lookupService.lookup(path2, FOLLOW_LINKS).orNull();
      return key != null && Objects.equal(key, key2);
    } finally {
      readLock().unlock();
    }
  }

  /**
   * Creates a new file at the given path if possible, using the given factory to create and
   * store the file. Returns the key of the new file. If {@code allowExisting} is {@code true} and
   * a file already exists at the given path, returns the key of that file. Otherwise, throws
   * {@link FileAlreadyExistsException}.
   */
  public FileKey createFile(
      JimfsPath path, FileFactory fileFactory, boolean allowExisting) throws IOException {
    checkNotNull(path);
    checkNotNull(fileFactory);

    String name = getName(path);

    writeLock().lock();
    try {
      LookupResult result = lookupService.lookup(path, NOFOLLOW_LINKS)
          .requireParentFound(path);

      if (result.isFileFound()) {
        if (allowExisting) {
          // currently can only happen if getOrCreateFile doesn't find the file with the read lock
          // and then the file is created between when it releases the read lock and when it
          // acquires the write lock; so, very unlikely
          return result.getFileKey();
        } else {
          throw new FileAlreadyExistsException(path.toString());
        }
      }

      FileKey parentKey = result.getParentKey();
      DirectoryTable parentTable = storage.getFile(parentKey).content();

      FileKey key = fileFactory.createAndStoreFile();
      parentTable.link(name, key);

      File file = storage.getFile(key);
      linkParent(parentKey, file);
      return key;
    } finally {
      writeLock().unlock();
    }
  }

  private void linkParent(FileKey parentKey, File file) {
    if (file.isDirectory()) {
      DirectoryTable table = file.content();
      table.linkParent(parentKey);
    }
  }

  private void unlinkParent(FileKey key) {
    if (key.type() == FileType.DIRECTORY) {
      File file = storage.getFile(key);
      DirectoryTable table = file.content();
      table.unlinkParent();
    }
  }

  private static String getName(Path path) {
    return path.getFileName() == null
        ? path.getRoot().toString()
        : path.getFileName().toString();
  }

  /**
   * Gets the regular file at the given path, creating it if it doesn't exist and the given options
   * specify that it should be created.
   */
  public File getOrCreateRegularFile(
      JimfsPath path, Set<? extends OpenOption> options) throws IOException {
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
        LookupResult result = lookupService.lookup(path, linkHandling);
        if (result.isFileFound()) {
          File file = result.getFile(storage);
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
      FileKey key = createFile(path, storage.regularFileFactory(), !createNew);
      // the file already existed but was not a regular file
      if (!key.isRegularFile()) {
        throw new FileSystemException(path.toString(), null, "not a regular file");
      }
      return truncateIfNeeded(storage.getFile(key), options);
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

    String linkName = getName(link);

    // targetTree is in the same file system, so the lock applies for both trees
    writeLock().lock();
    try {
      // we do want to follow links when finding the existing file
      FileKey existingKey = existingTree.lookupService.lookup(existing, FOLLOW_LINKS)
          .requireFileFound(existing)
          .getFileKey();
      File existingFile = storage.getFile(existingKey);
      if (!existingFile.isRegularFile()) {
        throw new FileSystemException(link.toString(), existing.toString(),
            "can't link: not a regular file");
      }

      FileKey linkParentKey = lookupService.lookup(link, NOFOLLOW_LINKS)
          .requireParentFound(link)
          .requireNotFound(link)
          .getParentKey();

      DirectoryTable linkParentTable = storage().getFile(linkParentKey).content();
      linkParentTable.link(linkName, existingKey);
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
    String name = getName(path);

    writeLock().lock();
    try {
      FileKey parentKey = lookupService.lookup(path, NOFOLLOW_LINKS)
          .requireFileFound(path)
          .getParentKey();

      DirectoryTable parentTable = storage.getFile(parentKey).content();
      delete(parentTable, name, deleteMode, path);
    } finally {
      writeLock().unlock();
    }
  }

  /**
   * Deletes the file with the given name and key from the given parent directory.
   */
  private void delete(DirectoryTable parentTable, String name,
      DeleteMode deleteMode, JimfsPath pathForException) throws IOException {
    FileKey key = parentTable.get(name);
    checkDeletable(key, deleteMode, pathForException);
    parentTable.unlink(name);
    unlinkParent(key);
    storage().unlinked(key);
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
      FileKey key, DeleteMode mode, Path pathForException) throws IOException {
    if (key.type() == FileType.DIRECTORY) {
      if (mode == DeleteMode.NON_DIRECTORY_ONLY) {
        throw new FileSystemException(
            pathForException.toString(), null, "can't delete: is a directory");
      }

      if (fs.getRootKeys().contains(key)) {
        throw new FileSystemException(
            pathForException.toString(), null, "can't delete root directory");
      }

      checkEmpty(key, pathForException);
    } else if (mode == DeleteMode.DIRECTORY_ONLY) {
      throw new FileSystemException(
          pathForException.toString(), null, "can't delete: is not a directory");
    }
  }

  /**
   * Checks that the directory identified by the given key is empty, throwing
   * {@link DirectoryNotEmptyException} if it isn't.
   */
  private void checkEmpty(FileKey key, Path pathForException) throws FileSystemException {
    // an empty directory has 2 links to it; one from its parent, one from itself (".")
    // any further links are from
    if (key.links() > 2) {
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

    JimfsPath sourceName = source.getFileName();
    JimfsPath destName = dest.getFileName();

    lockBoth(writeLock(), destTree.writeLock());
    try {
      LookupResult sourceLookup = lookupService.lookup(source, linkHandling)
          .requireFileFound(source);
      LookupResult destLookup = destTree.lookupService.lookup(dest, NOFOLLOW_LINKS)
          .requireParentFound(dest);

      DirectoryTable sourceParent = storage.getFile(sourceLookup.getParentKey()).content();
      FileKey sourceKey = sourceLookup.getFileKey();
      File sourceFile = storage.getFile(sourceKey);

      FileKey destParentKey = destLookup.getParentKey();
      DirectoryTable destParent = destTree.storage.getFile(destParentKey).content();

      if (move && sourceFile.isDirectory()) {
        if (sameFileSystem) {
          checkMovable(sourceKey, source);
          checkNotAncestor(sourceKey, destParent, destTree);
        } else {
          // move to another file system is accomplished by copy-then-delete, so the source file
          // must be deletable to be moved
          checkDeletable(sourceKey, DeleteMode.ANY, source);
        }
      }

      if (destLookup.isFileFound()) {
        // identity because keys from 2 file system instances could have the same value equality
        // TODO(cgdecker): consider changing this to make the keys unique per VM
        if (destLookup.getFileKey() == sourceKey) {
          return;
        } else if (options.contains(REPLACE_EXISTING)) {
          destTree.delete(destParent, destName.toString(), DeleteMode.ANY, dest);
        } else {
          throw new FileAlreadyExistsException(dest.toString());
        }
      }

      // can only do an actual move within one file system instance
      // otherwise we have to copy and delete
      if (move && sameFileSystem) {
        sourceParent.unlink(sourceName.toString());
        destParent.link(destName.toString(), sourceKey);

        unlinkParent(sourceKey);
        linkParent(destParent.key(), sourceFile);
      } else {
        // copy
        FileKey copyKey = destTree.storage().copy(sourceFile);
        destParent.link(destName.toString(), copyKey);

        File copy = destTree.storage().getFile(copyKey);
        linkParent(destParentKey, copy);

        if (move) {
          copyBasicAttributes(sourceFile, destTree, copy);
          delete(sourceParent, sourceName.toString(), DeleteMode.ANY, source);
        }
      }
    } finally {
      destTree.writeLock().unlock();
      writeLock().unlock();
    }
  }

  private void checkMovable(FileKey fileKey, JimfsPath path) throws FileSystemException {
    if (fs.getRootKeys().contains(fileKey)) {
      throw new FileSystemException(path.toString(), null, "can't move root directory");
    }
  }

  private void copyBasicAttributes(File source, FileTree destTree, File dest) throws IOException {
    AttributeManager sourceAttributeManager = fs.getAttributeManager();
    AttributeManager destAttributeManager = destTree.fs.getAttributeManager();

    BasicFileAttributes sourceAttributes = sourceAttributeManager
        .readAttributes(source, BasicFileAttributes.class);
    BasicFileAttributeView destAttributeView = destAttributeManager
        .getFileAttributeView(FileProvider.ofFile(dest), BasicFileAttributeView.class);

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
      FileKey sourceKey, DirectoryTable dest, FileTree destTree) throws IOException {
    // if dest is not in the same file system, it couldn't be in source's subdirectories
    if (!isSameFileSystem(destTree)) {
      return;
    }

    DirectoryTable current = dest;
    while (true) {
      FileKey currentKey = current.key();
      if (currentKey.equals(sourceKey)) {
        throw new IOException(
            "invalid argument: can't move directory into a subdirectory of itself");
      }

      FileKey parentKey = current.parent();
      if (currentKey.equals(parentKey)) {
        return;
      } else {
        File parentFile = destTree.storage().getFile(parentKey);
        assert parentFile != null;
        current = parentFile.content();
      }
    }
  }

  /**
   * Returns a file attribute view for the given path in this tree.
   */
  public <V extends FileAttributeView> V getFileAttributeView(
      JimfsPath path, Class<V> type, LinkHandling linkHandling) {
    FileProvider fileProvider = FileProvider.lookup(this, path, linkHandling);
    return fs.getAttributeManager()
        .getFileAttributeView(fileProvider, type);
  }

  /**
   * Reads attributes of the file located by the given path in this tree as an object.
   */
  public <A extends BasicFileAttributes> A readAttributes(
      JimfsPath path, Class<A> type, LinkHandling linkHandling) throws IOException {
    File file = requireNonNull(lookupFile(path, linkHandling), path);
    return fs.getAttributeManager().readAttributes(file, type);
  }

  /**
   * Reads attributes of the file located by the given path in this tree as a map.
   */
  public Map<String, Object> readAttributes(
      JimfsPath path, String attributes, LinkHandling linkHandling) throws IOException {
    File file = requireNonNull(lookupFile(path, linkHandling), path);
    return fs.getAttributeManager().readAttributes(file, attributes);
  }

  /**
   * Sets the given attribute to the given value on the file located by the given path in this tree.
   */
  public void setAttribute(JimfsPath path, String attribute, Object value,
      LinkHandling linkHandling) throws IOException {
    File file = requireNonNull(lookupFile(path, linkHandling), path);
    fs.getAttributeManager()
        .setAttribute(file, attribute, value, AttributeManager.SetMode.NORMAL);
  }
}
