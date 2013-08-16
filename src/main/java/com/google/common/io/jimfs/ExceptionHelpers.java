package com.google.common.io.jimfs;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;

import javax.annotation.Nullable;

/**
 * Helper methods for checking conditions that may require throwing {@link IOException}s.
 *
 * @author Colin Decker
 */
final class ExceptionHelpers {

  private ExceptionHelpers() {}

  /**
   * Throws {@link NoSuchFileException} if the given object is null.
   */
  public static <T> T requireNonNull(@Nullable T obj, Path path) throws NoSuchFileException {
    if (obj == null) {
      throw new NoSuchFileException(path.toString());
    }
    return obj;
  }

  /**
   * Throws {@link NoSuchFileException} if the given file is not a directory. Primarily for use
   * when doing something with a file (such as deleting it) that requires finding its parent
   * directory first; if the parent is not a directory, {@link NoSuchFileException} should be
   * thrown rather than {@link NotDirectoryException}.
   */
  public static DirectoryTable requireExistsParentDir(
      @Nullable File file, Path path) throws NoSuchFileException {
    if (file == null || !file.isDirectory()) {
      throw new NoSuchFileException(path.toString());
    }
    return file.content();
  }

  /**
   * Throws {@link ProviderMismatchException} for the given path.
   */
  public static ProviderMismatchException throwProviderMismatch(Path path) {
    throw new ProviderMismatchException(path + " is not associated with this file system");
  }
}
