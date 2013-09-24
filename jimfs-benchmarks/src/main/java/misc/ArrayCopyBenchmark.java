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

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;

import java.util.Random;

/**
 * Benchmark comparing the use of System.arraycopy to byte[].clone() when copying an array.
 *
 * @author Colin Decker
 */
public class ArrayCopyBenchmark {

  @Param({"1000", "10000", "100000"})
  private int size;

  private byte[] bytes;

  @BeforeExperiment
  public void setUp() {
    bytes = new byte[size];
    new Random(198234983739824L).nextBytes(bytes);
  }

  @Benchmark
  public int arraycopy(int reps) {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      byte[] copy = new byte[size];
      System.arraycopy(bytes, 0, copy, 0, size);
      result ^= copy[0];
    }
    return result;
  }

  @Benchmark
  public int clone(int reps) {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      byte[] copy = bytes.clone();
      result ^= copy[0];
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(ArrayCopyBenchmark.class, args);
  }
}
