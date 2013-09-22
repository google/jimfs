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
@VmOptions({"-Xmx8G"})
public class ByteStoreWriteBenchmark {

  @Param({"100000", "1000000", "10000000"})
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

  /**
   * Only benchmarking 8K buffered write because A) it's probably about the norm, and B) previous
   * benchmarking has shown that there isn't actually much difference between that and writing the
   * whole array in one call.
   */
  @Macrobenchmark
  public int write() {
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

    DISK_EMPTY_8KB_BLOCKS {
      @Override
      public ByteStore createByteStore() {
        return new PrivateArrayDisk().createByteStore();
      }
    },

    DISK_EMPTY_32KB_BLOCKS {
      @Override
      public ByteStore createByteStore() {
        return new PrivateArrayDisk(32 * 1024).createByteStore();
      }
    },

    DISK_ALREADY_ALLOCATED {
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
    };

    public abstract ByteStore createByteStore();
  }
}
