package com.google.common.io.jimfs;

/**
 * Factory that creates files and stores them in {@link FileStorage}.
 *
 * @author Colin Decker
 */
interface FileFactory {

  /**
   * Creates a new file and stores it, returning the key of the new file.
   */
  FileKey createAndStoreFile();
}
