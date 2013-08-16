package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;

/**
 * A directory entry, which is a named link between a directory and a file. Each directory entry
 * has a {@linkplain #name() name}, the key of the {@linkplain #directory() directory} containing
 * the entry and the key of the {@linkplain #file() file} of the file that the entry links to.
 * A file is considered to have one link for each directory entry containing it. For directories,
 * this includes the entry (named ".") that references the directory from itself.
 *
 * <p>When a directory entry is created, the link count of the linked file is incremented. When the
 * entry is destroyed, the {@link #unlink()} method is called to decrement the link count of the
 * file.
 *
 * @author Colin Decker
 */
final class DirEntry {

  private final String name;
  private final FileKey dir;
  private final FileKey file;

  public DirEntry(String name, FileKey dir, FileKey file) {
    this.name = checkNotNull(name);
    this.dir = checkNotNull(dir);
    this.file = checkNotNull(file);

    file.linked();
  }

  /**
   * Returns the file name for this entry.
   */
  public String name() {
    return name;
  }

  /**
   * Returns the key of the directory containing this entry.
   */
  public FileKey directory() {
    return dir;
  }

  /**
   * Returns the key of the file this entry references.
   */
  public FileKey file() {
    return file;
  }

  /**
   * Called when this entry is unlinked in the containing directory.
   */
  public void unlink() {
    file.unlinked();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof DirEntry) {
      DirEntry other = (DirEntry) obj;
      return name.equals(other.name)
          && dir.equals(other.dir)
          && file.equals(other.file);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, dir, file);
  }

  @Override
  public String toString() {
    return name;
  }
}
