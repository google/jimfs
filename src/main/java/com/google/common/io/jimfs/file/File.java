package com.google.common.io.jimfs.file;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.common.io.jimfs.bytestore.ByteStore;
import com.google.common.io.jimfs.path.JimfsPath;
import com.google.common.primitives.Longs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single file object. Similar in concept to an <i>inode</i> in that it mostly stores file
 * metadata, but also keeps a reference to the file's content.
 *
 * @author Colin Decker
 */
public final class File {

  private final long id;
  private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();
  private final FileContent content;
  private final AtomicInteger links = new AtomicInteger();

  File(long id, FileContent content) {
    this.id = id;
    this.content = checkNotNull(content);
  }

  /**
   * Returns the ID of this file.
   */
  public long id() {
    return id;
  }

  /**
   * Returns whether or not this file is a directory.
   */
  public boolean isDirectory() {
    return content instanceof DirectoryTable;
  }

  /**
   * Returns whether or not this file is a regular file.
   */
  public boolean isRegularFile() {
    return content instanceof ByteStore;
  }

  /**
   * Returns whether or not this file is a symbolic link.
   */
  public boolean isSymbolicLink() {
    return content instanceof JimfsPath;
  }

  /**
   * Returns whether or not this file is a root directory of the file system.
   */
  public boolean isRootDirectory() {
    // only root directories have their parent link pointing to themselves
    return isDirectory() && equals(((DirectoryTable) content()).parent());
  }

  /**
   * Returns the file content, with a cast to allow the type to be inferred at the call site.
   */
  @SuppressWarnings("unchecked")
  public <C extends FileContent> C content() {
    return (C) content;
  }

  /**
   * Returns the current count of links to this file.
   */
  public int links() {
    return links.get();
  }

  /**
   * Increments the link count.
   */
  public void linked() {
    links.incrementAndGet();
  }

  /**
   * Decrements and returns the link count.
   */
  public int unlinked() {
    return links.decrementAndGet();
  }

  /**
   * Returns the attribute keys contained in the attributes map for this file.
   */
  public Iterable<String> getAttributeKeys() {
    return attributes.keySet();
  }

  /**
   * Gets the value of the attribute with the given key.
   */
  public Object getAttribute(String key) {
    return attributes.get(key);
  }

  /**
   * Sets the attribute with the given key to the given value.
   */
  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  /**
   * Deletes the attribute with the given key.
   */
  public void deleteAttribute(String key) {
    attributes.remove(key);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof File) {
      File other = (File) obj;
      return id == other.id;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(id);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("id", id)
        .toString();
  }
}
