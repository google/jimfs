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

import static java.nio.file.StandardOpenOption.READ;

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Param;
import com.google.caliper.api.AfterRep;
import com.google.caliper.api.BeforeRep;
import com.google.caliper.api.Macrobenchmark;
import com.google.caliper.api.VmOptions;
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * @author Colin Decker
 */
@VmOptions({"-Xmx8G"})
public class WriteBenchmark {

  @Param({"100000", "1000000", "10000000"})
  private int size;

  @Param
  private ByteStoreType type;

  private byte[] bytes;
  private Path file;

  @BeforeExperiment
  public void setUp() throws IOException {
    bytes = new byte[size];
    new Random(19827398712389L).nextBytes(bytes);

    file = Files.createTempFile("ByteStoreWriteBenchmark", "");
    Files.write(file, bytes);
  }

  @AfterExperiment
  public void tearDown() throws IOException {
    Files.deleteIfExists(file);
  }

  private ByteStore store;
  private FileChannel channel;

  @BeforeRep
  public void beforeRep() throws IOException {
    store = type.createByteStore();
    channel = FileChannel.open(file, READ);
  }

  /**
   * Using 8K buffered writing because it seems like approximately the most common way to write and
   * previous benchmarking has shown me that it really doesn't make a significant difference what
   * kind of write is done: 8k buffered, 32k buffered or passing in a full array at once.
   */
  @Macrobenchmark
  public int write() {
    int pos = 0;
    while (pos < size) {
      pos += store.write(pos, bytes, pos, Math.min(8192, size - pos));
    }
    return pos;
  }

  /**
   * Test transferring an actual file to a store. This is where the difference between direct and
   * heap buffers should be most pronounced. It would probably also have some impact on writing to
   * a store from a socket channel.
   */
  @Macrobenchmark
  public int transferFromFile() throws IOException {
    int pos = 0;
    while (pos < size) {
      pos += store.transferFrom(channel, pos, size - pos);
    }
    return pos;
  }

  @AfterRep
  public void afterRep() throws IOException {
    store.delete();
    channel.close();
  }

  public static void main(String[] args) {
    CaliperMain.main(WriteBenchmark.class, args);
  }

  @SuppressWarnings("unused")
  private enum ByteStoreType {
    ARRAY_BYTE_STORE {
      @Override
      public ByteStore createByteStore() {
        return new ArrayByteStore();
      }
    },

    HEAP_DISK_EMPTY {
      @Override
      public ByteStore createByteStore() {
        return new HeapDisk().createByteStore();
      }
    },

    HEAP_DISK_ALREADY_ALLOCATED {
      private final Disk disk = new HeapDisk();
      {
        while (disk.getTotalSpace() < 10000000) {
          disk.allocateMoreBlocks();
        }
      }

      @Override
      public ByteStore createByteStore() {
        return disk.createByteStore();
      }
    },

    DIRECT_DISK_EMPTY {
      @Override
      public ByteStore createByteStore() {
        return new DirectDisk().createByteStore();
      }
    },

    DIRECT_DISK_ALREADY_ALLOCATED {
      private final Disk disk = new DirectDisk();
      {
        while (disk.getTotalSpace() < 10000000) {
          disk.allocateMoreBlocks();
        }
      }

      @Override
      public ByteStore createByteStore() {
        return disk.createByteStore();
      }
    };

    public abstract ByteStore createByteStore();
  }
}
