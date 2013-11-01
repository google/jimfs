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
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

/**
 * Benchmark comparing the speed of transferring bytes to a target channel such as a socket or
 * file channel from different types of ByteSources using FileChannel.transferTo.
 *
 * @author Colin Decker
 */
public class TransferToBenchmark {

  @Param({"1000", "100000", "10000000"})
  private int size;

  @Param
  private ByteStoreType storeType;

  @Param
  private TargetType targetType;

  private ByteStore store;
  private ChannelProvider channelProvider;

  @BeforeExperiment
  public void setUp() throws IOException, InterruptedException {
    store = storeType.createByteStore();

    byte[] bytes = new byte[size];
    new Random(19827398712389L).nextBytes(bytes);

    int pos = 0;
    while (pos < size) {
      pos += store.write(pos, bytes, pos, size - pos);
    }

    channelProvider = targetType.channelProvider(size);
    channelProvider.setUp();
  }

  @Benchmark
  public int transferTo(int reps) throws Throwable {
    WritableByteChannel channel = channelProvider.getWritableChannel();
    int pos = 0;
    for (int i = 0; i < reps; i++) {
      channelProvider.beforeRep();
      pos = 0;
      while (pos < size) {
        pos += store.transferTo(pos, size - pos, channel);
      }
      channelProvider.afterRep();
    }
    return pos;
  }

  @AfterExperiment
  public void tearDown() throws IOException {
    channelProvider.tearDown();
  }

  public static void main(String[] args) {
    CaliperMain.main(TransferToBenchmark.class, args);
  }

  @SuppressWarnings("unused")
  private enum ByteStoreType {
    HEAP_DISK {
      @Override
      public ByteStore createByteStore() {
        return new HeapMemoryDisk().createByteStore();
      }
    },

    DIRECT_DISK {
      @Override
      public ByteStore createByteStore() {
        return new DirectMemoryDisk().createByteStore();
      }
    };

    public abstract ByteStore createByteStore();
  }

  @SuppressWarnings("unused")
  private enum TargetType {
    SOCKET {
      @Override
      protected ChannelProvider channelProvider(int size) {
        return ChannelProvider.newSocketChannelProvider(size);
      }
    },

    FILE {
      @Override
      protected ChannelProvider channelProvider(int size) {
        return ChannelProvider.newFileChannelProvider();
      }
    };

    protected abstract ChannelProvider channelProvider(int size);
  }
}
