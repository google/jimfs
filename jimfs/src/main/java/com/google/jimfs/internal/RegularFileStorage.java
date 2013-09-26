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

import com.google.jimfs.Storage;

/**
 * Factory for creating new byte stores for regular files.
 *
 * @author Colin Decker
 */
abstract class RegularFileStorage {

  /**
   * Returns a {@link RegularFileStorage} instance matching the given configuration.
   */
  public static RegularFileStorage from(Storage configuration) {
    if (configuration.isBlock()) {
      int blockSize = configuration.getBlockSize();
      if (configuration.isDirect()) {
        return new DirectDisk(blockSize);
      } else {
        return new HeapDisk(blockSize);
      }
    } else {
      if (configuration.isDirect()) {
        return DirectByteStore.factory();
      } else {
        return ArrayByteStore.factory();
      }
    }
  }

  /**
   * Creates a new, empty byte store.
   */
  public abstract ByteStore createByteStore();

  /**
   * Returns the current total space in this storage.
   */
  public abstract long getTotalSpace();

  /**
   * Returns the current unallocated space in this storage.
   */
  public abstract long getUnallocatedSpace();
}
