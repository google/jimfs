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

import com.google.common.math.IntMath;

import java.math.RoundingMode;

/**
 * @author Colin Decker
 */
public final class BenchmarkUtils {

  private BenchmarkUtils() {}

  /**
   * Pre-allocates enough bytes in the given disk to hold up to maxSize bytes.
   */
  public static MemoryDisk preAllocate(MemoryDisk disk, int maxSize) {
    disk.allocateMoreBlocks(IntMath.divide(maxSize, disk.blockSize(), RoundingMode.UP));
    return disk;
  }
}
