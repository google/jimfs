package com.google.jimfs.internal;

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Param;
import com.google.caliper.api.AfterRep;
import com.google.caliper.api.Macrobenchmark;
import com.google.caliper.api.VmOptions;
import com.google.caliper.runner.CaliperMain;

import java.util.Arrays;

/**
 * @author Colin Decker
 */
@VmOptions("-Xmx8G")
public class ArrayAllocationBenchmark {

  /** 8192 * 1024 = 4 MB */
  private static final int TOTAL_SIZE = 8388608;

  /**
   * Various smaller block sizes, as well as allocating the whole thing as one array.
   */
  @Param({"1024", "8192", "32768", "8388608"})
  private int blockSize;

  private int numBlocks;
  private byte[][] holder;

  @BeforeExperiment
  public void setUp() {
    numBlocks = TOTAL_SIZE / blockSize;
    holder = new byte[numBlocks][];
  }

  @AfterExperiment
  public void tearDown() {
    Arrays.fill(holder, null);
  }

  @Macrobenchmark
  public int allocate() {
    int result = 0;
    for (int i = 0; i < numBlocks; i++) {
      byte[] block = new byte[blockSize];
      holder[i] = block;
      result ^= block.length;
    }
    return result;
  }

  @AfterRep
  public void afterRep() {
    Arrays.fill(holder, null);
  }

  public static void main(String[] args) {
    CaliperMain.main(ArrayAllocationBenchmark.class, args);
  }
}
