package com.google.jimfs.internal;

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;

import java.util.Arrays;

/**
 * Benchmark testing a number of different ways of zeroing a byte array.
 *
 * @author Colin Decker
 */
public class ZeroArrayBenchmark {

  @Param({"100", "1000", "100000", "1000000"})
  private int size;

  private byte[] bytes;

  @BeforeExperiment
  public void setUp() {
    bytes = new byte[size];
  }

  @Benchmark
  public int fill(int reps) {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      Arrays.fill(bytes, (byte) 0);
      result ^= i;
    }
    return result;
  }

  @Benchmark
  public int loop(int reps) {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      for (int j = 0; j < bytes.length; j++) {
        bytes[j] = (byte) 0;
      }
      result ^= i;
    }
    return result;
  }

  @Benchmark
  public int zero(int reps) {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      Util.zero(bytes);
      result ^= i;
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(ZeroArrayBenchmark.class, args);
  }
}
