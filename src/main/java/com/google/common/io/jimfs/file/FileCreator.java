package com.google.common.io.jimfs.file;

/**
 * Callback for creating new files.
 *
 * @author Colin Decker
 */
public interface FileCreator {

  /**
   * Creates and returns a new file.
   */
  File createFile();
}
