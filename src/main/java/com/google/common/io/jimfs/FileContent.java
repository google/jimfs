package com.google.common.io.jimfs;

/**
 * Marker interface for implementations of content for different types of files.
 *
 * @author Colin Decker
 */
interface FileContent {

  /**
   * Creates a copy of this content. The copy will have the given key, if it needs that.
   */
  FileContent copy(FileKey newKey);

  /**
   * Returns the size, in bytes, of this content.
   */
  int size();
}
