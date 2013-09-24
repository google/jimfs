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

package misc;

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Param;
import com.google.caliper.api.AfterRep;
import com.google.caliper.api.Macrobenchmark;
import com.google.caliper.runner.CaliperMain;

import java.util.Arrays;

/**
 * Benchmark comparing allocating a single large array vs. allocating multiple smaller arrays of
 * different sizes that add up to the same total size.
 *
 * @author Colin Decker
 */
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
