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

package com.google.jimfs;

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;

/**
 * Benchmark comparing the times to read and set attributes between JIMFS and the default file
 * system.
 *
 * @author Colin Decker
 */
public class FileAttributeBenchmark {

  private static final FileTime TIME = FileTime.fromMillis(0);

  @Param
  private FileSystemImpl impl;

  private FileSystemBenchmarkHelper helper;

  private Path file;

  @BeforeExperiment
  public void setUp() throws IOException {
    helper = new FileSystemBenchmarkHelper(impl);
    file = helper.getTempDir().resolve("file");

    Files.write(file, new byte[1000]);
  }

  @AfterExperiment
  public void tearDown() throws IOException {
    helper.tearDown();
  }

  @Benchmark
  public int readAttributes(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
      result ^= attributes.size();
    }
    return result;
  }

  @Benchmark
  public int readMap(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      Map<String, Object> attributes = Files.readAttributes(file, "basic:*");
      result ^= attributes.size();
    }
    return result;
  }

  @Benchmark
  public int setThroughView(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      BasicFileAttributeView view = Files.getFileAttributeView(file, BasicFileAttributeView.class);
      view.setTimes(null, TIME, null);
      result ^= view.hashCode();
    }
    return result;
  }

  @Benchmark
  public int setByName(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      Files.setAttribute(file, "lastAccessTime", TIME);
      result ^= i;
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(FileAttributeBenchmark.class, args);
  }
}
