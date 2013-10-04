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

package com.google.jimfs.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableCollection;

/**
 * Miscellaneous static utility methods.
 *
 * @author Colin Decker
 */
final class Util {

  private Util() {}

  /**
   * Returns the next power of 2 >= n.
   */
  public static int nextPowerOf2(int n) {
    if (n == 0) {
      return 1;
    }
    int b = Integer.highestOneBit(n);
    return b == n ? n : b << 1;
  }

  /**
   * Checks that the given number is not negative, throwing IAE if it is. The given description
   * describes the number in the exception message.
   */
  static void checkNotNegative(long n, String description) {
    checkArgument(n >= 0, "%s must not be negative: %s", description, n);
  }

  /**
   * Checks that no element in the given iterable is null, throwing NPE if any is.
   */
  static void checkNoneNull(Iterable<?> objects) {
    if (!(objects instanceof ImmutableCollection)) {
      for (Object o : objects) {
        checkNotNull(o);
      }
    }
  }
}
