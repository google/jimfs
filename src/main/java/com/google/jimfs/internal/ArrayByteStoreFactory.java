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
 * Factory for array byte stores.
 *
 * @author Colin Decker
 */
final class ArrayByteStoreFactory implements RegularFileStorage {

  @Override
  public ByteStore createByteStore() {
    return new ArrayByteStore();
  }

  @Override
  public long getTotalSpace() {
    return Integer.MAX_VALUE;
  }

  @Override
  public long getUnallocatedSpace() {
    return Integer.MAX_VALUE;
  }
}
