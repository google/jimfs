package com.google.jimfs.internal;

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Param;
import com.google.caliper.api.AfterRep;
import com.google.caliper.api.BeforeRep;
import com.google.caliper.api.Macrobenchmark;
import com.google.caliper.api.VmOptions;
import com.google.caliper.runner.CaliperMain;

import java.util.Random;

/**
 * @author Colin Decker
 */
@VmOptions({"-Xmx12G"})
public class ByteStoreWriteBenchmark {

  @Param({/*"1000", */"100000", "1000000", "10000000"})
  private int size;

  @Param
  private ByteStoreType type;

  private byte[] bytes;

  @BeforeExperiment
  public void setUp() {
    bytes = new byte[size];
    new Random(19827398712389L).nextBytes(bytes);
  }

  private ByteStore store;

  @BeforeRep
  public void beforeRep() {
    store = type.createByteStore();
  }

  @Macrobenchmark
  public int timeBufferedWrite() {
    int pos = 0;
    while (pos < size) {
      pos += store.write(pos, bytes, pos, Math.min(8192, size - pos));
    }
    return pos;
  }

  @AfterRep
  public void afterRep() {
    store.delete();
  }

  /*@Benchmark
  public int timeBufferedWrite(int reps) {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      // must create a new store each rep to ensure state is fresh
      ByteStore store = type.createByteStore();
      int pos = 0;
      while (pos < size) {
        pos += store.write(pos, bytes, pos, Math.min(8192, size - pos));
        result ^= pos;
      }
      store.truncate(0); // ensure disk stores return their blocks to the disk for reuse
    }
    return result;
  }*/

  /*@Benchmark
  public int timeWriteWholeArray(int reps) {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      // must create a new store each rep to ensure state is fresh
      ByteStore store = type.createByteStore();
      int pos = 0;
      while (pos < size) {
        pos += store.write(pos, bytes);
        result ^= pos;
      }
      store.truncate(0); // ensure disk stores return their blocks to the disk for reuse
    }
    return result;
  }*/

  public static void main(String[] args) {
    CaliperMain.main(ByteStoreWriteBenchmark.class, args);
  }

  @SuppressWarnings("unused")
  private enum ByteStoreType {
    ARRAY_BYTE_STORE {
      @Override
      public ByteStore createByteStore() {
        return new ArrayByteStore();
      }
    },

    PRIVATE_ARRAY_DISK_EMPTY {
      @Override
      public ByteStore createByteStore() {
        return new PrivateArrayDisk().createByteStore();
      }
    },

    PRIVATE_ARRAY_DISK_ALREADY_ALLOCATED {
      private final Disk disk = new PrivateArrayDisk();
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

    SHARED_ARRAY_DISK_EMPTY {
      @Override
      public ByteStore createByteStore() {
        return new SharedArrayDisk().createByteStore();
      }
    },

    SHARED_ARRAY_DISK_ALREADY_ALLOCATED {
      private final Disk disk = new SharedArrayDisk();
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
