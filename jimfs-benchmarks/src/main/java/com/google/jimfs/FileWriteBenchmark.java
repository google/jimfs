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

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Benchmark comparing time to write files between JimFS and the default file system. Writes in
 * this benchmark are done as 8k buffered writes on a file that is truncated before writing.
 *
 * @author Colin Decker
 */
public class FileWriteBenchmark {

  @Param({"1000", "100000", "10000000"})
  private int size;

  @Param
  private FileSystemImpl impl;

  private FileSystemBenchmarkHelper helper;

  private Path file;
  private byte[] bytes;

  @BeforeExperiment
  public void setUp() throws IOException {
    helper = new FileSystemBenchmarkHelper(impl);
    file = helper.getTempDir().resolve("file");

    bytes = new byte[size];
    new Random(3984893212984L).nextBytes(bytes);
  }

  @AfterExperiment
  public void tearDown() throws IOException {
    helper.tearDown();
  }

  @Benchmark
  public int writeOutputStreamBuffered(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      try (OutputStream out = Files.newOutputStream(file)) {
        int written = 0;
        while (written < size) {
          int len = Math.min(8192, size - written);
          out.write(bytes, written, len);
          written += len;
        }
        result ^= written;
      }
    }
    return result;
  }

  @Benchmark
  public int writeFileChannelBuffered(int reps) throws IOException {
    int result = 0;
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    for (int i = 0; i < reps; i++) {
      try (FileChannel out = FileChannel.open(file, CREATE, TRUNCATE_EXISTING, WRITE)) {
        while (buf.hasRemaining()) {
          buf.limit(buf.position() + Math.min(8192, buf.remaining()));
          out.write(buf);
          buf.limit(buf.capacity());
        }
        result ^= buf.remaining();
      }
      buf.clear();
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(FileWriteBenchmark.class, args);
  }
}
