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

package com.google.common.jimfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableCollection;

/**
 * Miscellaneous static utility methods.
 *
 * @author Colin Decker
 * @author Austin Appleby
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

  private static final int C1 = 0xcc9e2d51;
  private static final int C2 = 0x1b873593;

  /*
   * This method was rewritten in Java from an intermediate step of the Murmur hash function in
   * http://code.google.com/p/smhasher/source/browse/trunk/MurmurHash3.cpp, which contained the
   * following header:
   *
   * MurmurHash3 was written by Austin Appleby, and is placed in the public domain. The author
   * hereby disclaims copyright to this source code.
   */
  static int smearHash(int hashCode) {
    return C2 * Integer.rotateLeft(hashCode * C1, 15);
  }

  private static final int ARRAY_LEN = 8192;
  private static final byte[] ZERO_ARRAY = new byte[ARRAY_LEN];
  private static final byte[][] NULL_ARRAY = new byte[ARRAY_LEN][];

  /**
   * Zeroes all bytes between off (inclusive) and off + len (exclusive) in the given array.
   */
  static void zero(byte[] bytes, int off, int len) {
    // this is significantly faster than looping or Arrays.fill (which loops), particularly when
    // the length of the slice to be zeroed is <= to ARRAY_LEN (in that case, it's faster by a
    // factor of 2)
    int remaining = len;
    while (remaining > ARRAY_LEN) {
      System.arraycopy(ZERO_ARRAY, 0, bytes, off, ARRAY_LEN);
      off += ARRAY_LEN;
      remaining -= ARRAY_LEN;
    }

    System.arraycopy(ZERO_ARRAY, 0, bytes, off, remaining);
  }

  /**
   * Clears (sets to null) all blocks between off (inclusive) and off + len (exclusive) in the
   * given array.
   */
  static void clear(byte[][] blocks, int off, int len) {
    // this is significantly faster than looping or Arrays.fill (which loops), particularly when
    // the length of the slice to be cleared is <= to ARRAY_LEN (in that case, it's faster by a
    // factor of 2)
    int remaining = len;
    while (remaining > ARRAY_LEN) {
      System.arraycopy(NULL_ARRAY, 0, blocks, off, ARRAY_LEN);
      off += ARRAY_LEN;
      remaining -= ARRAY_LEN;
    }

    System.arraycopy(NULL_ARRAY, 0, blocks, off, remaining);
  }
}
