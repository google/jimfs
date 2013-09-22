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

package com.google.jimfs.common;

import java.io.IOException;

/**
 * Supplier that may throw {@link IOException}.
 *
 * @author Colin Decker
 */
public abstract class IoSupplier<T> {

  /**
   * Gets an object, throwing an exception if the object can't be retrieved for any reason.
   */
  public abstract T get() throws IOException;

  /**
   * Returns a supplier that always returns the given instance.
   */
  public static <T> IoSupplier<T> of(final T instance) {
    return new IoSupplier<T>() {
      @Override
      public T get() {
        return instance;
      }
    };
  }
}
