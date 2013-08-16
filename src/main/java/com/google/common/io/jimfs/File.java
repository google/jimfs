package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A single file object. Similar in concept to an <i>inode</i>, in that it mostly stores file
 * metadata, but also keeps a reference to the file's content.
 *
 * @author Colin Decker
 */
final class File {

  /** The unique key for identifying this file and locating its content. */
  private final FileKey key;

  /** Maps attribute keys to values. */
  private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

  /** The content of the file. */
  private final FileContent content;

  File(FileKey key, FileContent content) {
    this.key = checkNotNull(key);
    this.content = checkNotNull(content);
  }

  /**
   * Returns the unique key for the file.
   */
  public FileKey key() {
    return key;
  }

  /**
   * Returns the type of the file.
   */
  public FileType type() {
    return key.type();
  }

  /**
   * Returns whether or not this file is a directory.
   */
  public boolean isDirectory() {
    return type() == FileType.DIRECTORY;
  }

  /**
   * Returns whether or not this file is a regular file.
   */
  public boolean isRegularFile() {
    return type() == FileType.REGULAR_FILE;
  }

  /**
   * Returns whether or not this file is a symbolic link.
   */
  public boolean isSymbolicLink() {
    return type() == FileType.SYMBOLIC_LINK;
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
    return key().links();
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
  public String toString() {
    return Objects.toStringHelper(this)
        .add("key", key)
        .add("type", type())
        .toString();
  }
}
