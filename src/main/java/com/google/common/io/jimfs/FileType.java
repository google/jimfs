package com.google.common.io.jimfs;

/**
 * Enum defining the types of files. Also defines a method for checking if a path points to a file
 * of the correct type, for use in testing.
 *
 * @author Colin Decker
 */
enum FileType {
  REGULAR_FILE,
  DIRECTORY,
  SYMBOLIC_LINK
}
