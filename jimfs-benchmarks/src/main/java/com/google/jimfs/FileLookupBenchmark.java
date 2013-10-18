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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmark comparing the time to look up the file at a somewhat complex path. Lookup time is
 * tested by opening a directory stream, which should (probably) be a relatively lightweight
 * operation.
 *
 * @author Colin Decker
 */
public class FileLookupBenchmark {

  @Param
  private FileSystemImpl impl;

  private FileSystemBenchmarkHelper helper;
  private Path file;

  @BeforeExperiment
  protected void setUp() throws Exception {
    helper = new FileSystemBenchmarkHelper(impl);
    Path tempDir = helper.getTempDir();

    Files.createDirectories(tempDir.resolve("foo/bar/baz"));
    Files.createDirectories(tempDir.resolve("a/b/c/d/e/f/g"));
    Files.createSymbolicLink(tempDir.resolve("a/b/c/d/e/link"),
        tempDir.toAbsolutePath().resolve("foo/bar"));
    Files.createSymbolicLink(tempDir.resolve("foo/bar/baz/link"),
        helper.getFileSystem().getPath("../../../..", tempDir.getFileName().toString(), "a/b"));

    file = tempDir.resolve("a/b/c/d/../../c/d/e/link/../../foo/bar/../bar/baz/link/c/file");
    Files.createDirectory(file);
  }

  @AfterExperiment
  protected void tearDown() throws Exception {
    helper.tearDown();
  }

  @Benchmark
  public int lookup(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(file)) {
        result ^= stream.hashCode();
      }
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(FileLookupBenchmark.class, args);
  }
}
