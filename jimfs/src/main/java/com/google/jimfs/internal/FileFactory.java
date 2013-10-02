package com.google.jimfs.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Supplier;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for creating new files and copying files.
 *
 * @author Colin Decker
 */
final class FileFactory {

  private final AtomicLong idGenerator = new AtomicLong();

  private final RegularFileStorage storage;

  /**
   * Creates a new file factory using the given storage for regular files.
   */
  public FileFactory(RegularFileStorage storage) {
    this.storage = checkNotNull(storage);
  }

  private long nextFileId() {
    return idGenerator.getAndIncrement();
  }

  /**
   * Creates a new directory and stores it. Returns the key of the new file.
   */
  public File createDirectory() {
    return new File(nextFileId(), new DirectoryTable());
  }

  /**
   * Creates a new regular file and stores it. Returns the key of the new file.
   */
  public File createRegularFile() {
    return new File(nextFileId(), storage.createByteStore());
  }

  /**
   * Creates a new symbolic link referencing the given target path and stores it. Returns the key of
   * the new file.
   */
  public File createSymbolicLink(JimfsPath target) {
    return new File(nextFileId(), target);
  }

  /**
   * Creates copies of the given file metadata and content and stores them. Returns the key of the
   * new file.
   */
  public File copy(File file) {
    return new File(nextFileId(), file.content().copy());
  }

  // suppliers to act as file creation callbacks

  /**
   * Directory supplier instance.
   */
  private final Supplier<File> directorySupplier = new DirectorySupplier();

  /**
   * Regular file supplier instance.
   */
  private final Supplier<File> regularFileSupplier = new RegularFileSupplier();

  /**
   * Returns a supplier that creates directories and sets the given attributes.
   */
  public Supplier<File> directorySupplier() {
    return directorySupplier;
  }

  /**
   * Returns a supplier that creates a regular files and sets the given attributes.
   */
  public Supplier<File> regularFileSupplier() {
    return regularFileSupplier;
  }

  /**
   * Returns a supplier that creates a symbolic links to the given path and sets the given
   * attributes.
   */
  public Supplier<File> symbolicLinkSupplier(JimfsPath target) {
    return new SymbolicLinkSupplier(target);
  }

  private final class DirectorySupplier implements Supplier<File> {

    @Override
    public File get() {
      return createDirectory();
    }
  }

  private final class RegularFileSupplier implements Supplier<File> {

    @Override
    public File get() {
      return createRegularFile();
    }
  }

  private final class SymbolicLinkSupplier implements Supplier<File> {

    private final JimfsPath target;

    protected SymbolicLinkSupplier(JimfsPath target) {
      this.target = checkNotNull(target);
    }

    @Override
    public File get() {
      return createSymbolicLink(target);
    }
  }
}
