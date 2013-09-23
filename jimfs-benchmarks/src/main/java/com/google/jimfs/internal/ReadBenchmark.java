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

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.api.VmOptions;
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * @author Colin Decker
 */
@VmOptions({"-Xmx8G"})
public class ReadBenchmark {

  @Param({"1000", "100000", "10000000"})
  private int size;

  @Param
  private ByteStoreType type;

  private ByteStore store;
  private Path file;

  private byte[] readTarget;

  @BeforeExperiment
  public void setUp() throws IOException {
    store = type.createByteStore();

    byte[] bytes = new byte[size];
    new Random(19827398712389L).nextBytes(bytes);

    int pos = 0;
    while (pos < size) {
      pos += store.write(pos, bytes, pos, size - pos);
    }

    readTarget = new byte[size];

    file = Files.createTempFile("ByteStoreReadBenchmark", "tmp");
    Files.write(file, bytes);
  }

  @AfterExperiment
  public void tearDown() throws IOException {
    Files.deleteIfExists(file);
  }

  /**
   * Using 8K buffered reading because it seems like approximately the most common way to read and
   * previous benchmarking has shown me that it really doesn't make a significant difference what
   * kind of read is done: 8k buffered, 32k buffered or passing in a full array at once.
   */
  @Benchmark
  public int read(int reps) {
    int pos = 0;
    for (int i = 0; i < reps; i++) {
      pos = 0;
      while (pos < size) {
        pos += store.read(pos, readTarget, pos, Math.min(8192, size - pos));
      }
    }
    return pos;
  }

  public static void main(String[] args) {
    CaliperMain.main(ReadBenchmark.class, args);
  }

  @SuppressWarnings("unused")
  private enum ByteStoreType {
    ARRAY_BYTE_STORE {
      @Override
      public ByteStore createByteStore() {
        return new ArrayByteStore();
      }
    },

    HEAP_DISK {
      @Override
      public ByteStore createByteStore() {
        return new HeapDisk().createByteStore();
      }
    },

    DIRECT_DISK {
      @Override
      public ByteStore createByteStore() {
        return new DirectDisk().createByteStore();
      }
    };

    public abstract ByteStore createByteStore();
  }
}
