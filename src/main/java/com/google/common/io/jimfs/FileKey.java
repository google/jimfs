package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.primitives.Longs;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unique (per file system instance) identifier for a single file object.
 *
 * @author Colin Decker
 */
final class FileKey {

  // TODO(cgdecker): Give each file system a unique ID number and include that here to make these
  // keys unique per VM?

  private final long id;
  private final FileType type;
  private final AtomicInteger links = new AtomicInteger();

  FileKey(long id, FileType type) {
    this.id = id;
    this.type = checkNotNull(type);
  }

  /**
   * Returns the type of file this key references.
   */
  public FileType type() {
    return type;
  }

  /**
   * Returns the file ID for this key.
   */
  public long id() {
    return id;
  }

  /**
   * Returns {@code true} if this is the key for a directory.
   */
  public boolean isDirectory() {
    return type == FileType.DIRECTORY;
  }

  /**
   * Returns {@code true} if this is the key for a regular file.
   */
  public boolean isRegularFile() {
    return type == FileType.REGULAR_FILE;
  }

  /**
   * Returns {@code true} if this is the key for a symbolic link.
   */
  public boolean isSymbolicLink() {
    return type == FileType.SYMBOLIC_LINK;
  }

  /**
   * Returns the current number of links to this key.
   */
  public int links() {
    return links.get();
  }

  /**
   * Called when a link is created to this key. Returns the new count of links to the key.
   */
  public int linked() {
    return links.incrementAndGet();
  }

  /**
   * Called when a link to this key is deleted. Returns the new count of links to the key.
   */
  public int unlinked() {
    return links.decrementAndGet();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof FileKey
        && id == ((FileKey) obj).id;
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(id);
  }

  @Override
  public String toString() {
    return String.valueOf(id);
  }
}
