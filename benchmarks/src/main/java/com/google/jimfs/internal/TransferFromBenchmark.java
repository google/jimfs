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

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.Random;

/**
 * Benchmark comparing the speed of transferring bytes from a source channel such as a socket or
 * file channel to different types of ByteSources using FileChannel.transferFrom.
 *
 * @author Colin Decker
 */
public class TransferFromBenchmark {

  @Param({"1000", "100000", "10000000"})
  private int size;

  @Param
  private ByteStoreType storeType;

  @Param
  private SourceType sourceType;

  private ChannelProvider channelProvider;

  @BeforeExperiment
  public void setUp() throws IOException, InterruptedException {
    byte[] bytes = new byte[size];
    new Random(19827398712389L).nextBytes(bytes);

    channelProvider = sourceType.channelProvider(bytes);
    channelProvider.setUp();
  }

  @Benchmark
  public int transferFrom(int reps) throws Throwable {
    ReadableByteChannel channel = channelProvider.getReadableChannel();
    int pos = 0;
    for (int i = 0; i < reps; i++) {
      ByteStore store = storeType.createByteStore();
      channelProvider.beforeRep();
      pos = 0;
      while (pos < size) {
        pos += store.transferFrom(channel, pos, size - pos);
      }
      channelProvider.afterRep();
      store.delete();
    }
    return pos;
  }

  @AfterExperiment
  public void tearDown() throws IOException {
    channelProvider.tearDown();
  }

  public static void main(String[] args) {
    CaliperMain.main(TransferFromBenchmark.class, args);
  }

  @SuppressWarnings("unused")
  private enum ByteStoreType {
    HEAP_DISK_EMPTY {
      @Override
      public ByteStore createByteStore() {
        return new HeapMemoryDisk().createByteStore();
      }
    },

    DIRECT_DISK_EMPTY {
      @Override
      public ByteStore createByteStore() {
        return new DirectMemoryDisk().createByteStore();
      }
    },

    HEAP_DISK_ALREADY_ALLOCATED {
      private final MemoryDisk disk = preAllocate(new HeapMemoryDisk(), 10000000);

      @Override
      public ByteStore createByteStore() {
        return disk.createByteStore();
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

  @SuppressWarnings("unused")
  private enum SourceType {
    SOCKET {
      @Override
      protected ChannelProvider channelProvider(byte[] bytes) {
        return ChannelProvider.newSocketChannelProvider(bytes);
      }
    },

    FILE {
      @Override
      protected ChannelProvider channelProvider(byte[] bytes) {
        return ChannelProvider.newFileChannelProvider(bytes);
      }
    };

    protected abstract ChannelProvider channelProvider(byte[] bytes);
  }
}
