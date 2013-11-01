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

import static com.google.jimfs.internal.BenchmarkUtils.preAllocate;

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Benchmark for writing to a store from either heap or native memory.
 *
 * <p>Uses 8K buffered writing because it seems like approximately the most common way to write and
 * previous benchmarking has shown me that it really doesn't make a significant difference what
 * kind of write is done: 8k buffered, 32k buffered or passing in a full array at once.
 *
 * @author Colin Decker
 */
public class WriteBenchmark {

  @Param({"1000", "100000", "10000000"})
  private int size;

  @Param
  private ByteStoreType storeType;

  private byte[] heapSource;
  private ByteBuffer directSource;

  @BeforeExperiment
  public void setUp() throws IOException {
    heapSource = new byte[size];
    new Random(19827398712389L).nextBytes(heapSource);

    directSource = ByteBuffer.allocateDirect(size);
    directSource.put(heapSource);
    directSource.clear();
  }

  @Benchmark
  public int writeFromByteArray(int reps) {
    int pos = 0;
    for (int i = 0; i < reps; i++) {
      ByteStore store = storeType.createByteStore();
      pos = 0;
      while (pos < size) {
        pos += store.write(pos, heapSource, pos, Math.min(8192, size - pos));
      }
      store.delete();
    }
    return pos;
  }

  @Benchmark
  public int writeFromDirectBuffer(int reps) {
    int pos = 0;
    for (int i = 0; i < reps; i++) {
      ByteStore store = storeType.createByteStore();
      directSource.clear();
      pos = 0;
      while (directSource.hasRemaining()) {
        directSource.limit(pos + Math.min(8192, size - pos));
        pos += store.write(pos, directSource);
      }
      store.delete();
    }
    return pos;
  }

  public static void main(String[] args) {
    CaliperMain.main(WriteBenchmark.class, args);
  }

  @SuppressWarnings("unused")
  private enum ByteStoreType {
    HEAP_DISK_EMPTY {
      @Override
      public ByteStore createByteStore() {
        return new HeapMemoryDisk().createByteStore();
      }
    },

    HEAP_DISK_ALREADY_ALLOCATED {
      private final MemoryDisk disk = preAllocate(new HeapMemoryDisk(), 10000000);

      @Override
      public ByteStore createByteStore() {
        return disk.createByteStore();
      }
    },

    DIRECT_DISK_EMPTY {
      @Override
      public ByteStore createByteStore() {
        return new DirectMemoryDisk().createByteStore();
      }
    },

    DIRECT_DISK_ALREADY_ALLOCATED {
      private final MemoryDisk disk = preAllocate(new DirectMemoryDisk(), 10000000);

      @Override
      public ByteStore createByteStore() {
        return disk.createByteStore();
      }
    };

    public abstract ByteStore createByteStore();
  }
}
