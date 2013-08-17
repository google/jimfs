package com.google.common.io.jimfs.file;

/**
 * Marker interface for implementations of content for different types of files.
 *
 * @author Colin Decker
 */
public interface FileContent {

  /**
   * Creates a copy of this content.
   */
  FileContent copy();

  /**
   * Returns the size, in bytes, of this content.
   */
  int size();
}
