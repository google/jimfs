/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
