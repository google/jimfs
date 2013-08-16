package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.io.jimfs.FileType.DIRECTORY;
import static com.google.common.io.jimfs.FileType.REGULAR_FILE;
import static com.google.common.io.jimfs.FileType.SYMBOLIC_LINK;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Storage for all file objects and content for the file system.
 *
 * @author Colin Decker
 */
final class FileStorage {

  private final AtomicLong keyGenerator = new AtomicLong();

  private final ConcurrentMap<FileKey, File> files = new ConcurrentHashMap<>();

  private final AttributeManager attributeManager;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private final boolean caseSensitive;

  FileStorage(AttributeManager attributeManager, boolean caseSensitive) {
    this.attributeManager = checkNotNull(attributeManager);
    this.caseSensitive = caseSensitive;
  }

  /**b
   * Returns the read lock for this file storage.
   */
  public Lock readLock() {
    return lock.readLock();
  }

  /**
   * Returns the write lock for this file storage.
   */
  public Lock writeLock() {
    return lock.writeLock();
  }

  private FileKey nextFileKey(FileType type) {
    return new FileKey(keyGenerator.getAndIncrement(), type);
  }

  private FileKey storeFile(FileKey key, FileContent content) {
    File file = new File(key, content);
    attributeManager.setInitialAttributes(file);
    files.put(key, file);
    return key;
  }

  /**
   * Creates a new directory and stores it. Returns the key of the new file.
   */
  public FileKey createDirectory() {
    FileKey key = nextFileKey(DIRECTORY);
    return storeFile(key, new DirectoryTable(key, caseSensitive));
  }

  /**
   * Creates a new regular file and stores it. Returns the key of the new file.
   */
  public FileKey createRegularFile() {
    return storeFile(nextFileKey(REGULAR_FILE), new ArrayByteStore());
  }

  /**
   * Creates a new symbolic link referencing the given target path and stores it. Returns the key
   * of the new file.
   */
  public FileKey createSymbolicLink(JimfsPath target) {
    return storeFile(nextFileKey(SYMBOLIC_LINK), target);
  }

  /**
   * Creates copies of the given file metadata and content and stores them. Returns the key of the
   * new file.
   */
  public FileKey copy(File file) {
    FileKey newKey = nextFileKey(file.type());
    return storeFile(newKey, file.content().copy(newKey));
  }

  /**
   * Returns whether or not a file exists for the given key.
   */
  public boolean exists(FileKey key) {
    return files.containsKey(key);
  }

  /**
   * Gets the file for the given key.
   */
  public File getFile(FileKey key) {
    File file = files.get(key);
    checkState(file != null, "no file found for key %s", key);
    return file;
  }

  /**
   * Called when the file with the given key is unlinked from a directory. Deletes the file if it
   * is no longer linked in any directory.
   */
  public void unlinked(FileKey key) {
    if (key.links() == 0) {
      files.remove(key);
    }
  }

  /**
   * Returns a {@link FileFactory} for creating directories in this storage.
   */
  public FileFactory directoryFactory() {
    return directoryFactory;
  }

  /**
   * Returns a {@link FileFactory} for creating regular files in this storage.
   */
  public FileFactory regularFileFactory() {
    return regularFileFactory;
  }

  /**
   * Returns a {@link FileFactory} for creating symbolic links pointing to the given {@code Path}
   * in this storage.
   */
  public FileFactory symbolicLinkFactory(final JimfsPath path) {
    return new FileFactory() {
      @Override
      public FileKey createAndStoreFile() {
        return createSymbolicLink(path);
      }
    };
  }

  private final FileFactory directoryFactory = new FileFactory() {
    @Override
    public FileKey createAndStoreFile() {
      return createDirectory();
    }
  };

  private final FileFactory regularFileFactory = new FileFactory() {
    @Override
    public FileKey createAndStoreFile() {
      return createRegularFile();
    }
  };
}
