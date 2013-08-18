package com.google.common.io.jimfs.util;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;

import javax.annotation.Nullable;

/**
 * Helper methods for checking conditions that may require throwing {@link IOException}s.
 *
 * @author Colin Decker
 */
public final class ExceptionHelpers {

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
   * Throws {@link ProviderMismatchException} for the given path.
   */
  public static ProviderMismatchException throwProviderMismatch(Path path) {
    throw new ProviderMismatchException(path + " is not associated with this file system");
  }
}
