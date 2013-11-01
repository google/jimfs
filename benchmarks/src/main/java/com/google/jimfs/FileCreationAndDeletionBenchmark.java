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
import java.nio.file.attribute.FileAttribute;

/**
 * Benchmark comparing the times to create and delete a file between JimFS and the default file
 * system.
 *
 * @author Colin Decker
 */
public class FileCreationAndDeletionBenchmark {

  private static final FileAttribute<?>[] NO_ATTRS = {};

  @Param
  private FileSystemImpl impl;

  private FileSystemBenchmarkHelper helper;

  private Path file;

  @BeforeExperiment
  public void setUp() throws IOException {
    helper = new FileSystemBenchmarkHelper(impl);
    file = helper.getTempDir().resolve("file");
  }

  @Benchmark
  public void createAndDeleteDirectory(int reps) throws IOException {
    for (int i = 0; i < reps; i++) {
      Files.createDirectory(file, NO_ATTRS);
      Files.deleteIfExists(file);
    }
  }

  @Benchmark
  public void createAndDeleteRegularFile(int reps) throws IOException {
    for (int i = 0; i < reps; i++) {
      Files.createFile(file, NO_ATTRS);
      Files.deleteIfExists(file);
    }
  }

  @Benchmark
  public void createAndDeleteSymbolicLink(int reps) throws IOException {
    for (int i = 0; i < reps; i++) {
      Files.createSymbolicLink(file, file, NO_ATTRS);
      Files.deleteIfExists(file);
    }
  }

  @AfterExperiment
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  public static void main(String[] args) {
    CaliperMain.main(FileCreationAndDeletionBenchmark.class, args);
  }
}
