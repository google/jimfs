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
    int highestOneBit = Integer.highestOneBit(n);
    return highestOneBit == n ? n : highestOneBit << 1;
  }
}
