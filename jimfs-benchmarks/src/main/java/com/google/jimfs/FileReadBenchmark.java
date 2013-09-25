package com.google.jimfs;

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Benchmark comparing time to read files between JIMFS and the default file system. Reads are done
 * as 8k buffered reads and the results of reading are simply ignored.
 *
 * @author Colin Decker
 */
public class FileReadBenchmark {

  @Param({"1000", "100000", "10000000"})
  private int size;

  @Param
  private FileSystemImpl impl;

  private FileSystemBenchmarkHelper helper;

  private Path file;

  @BeforeExperiment
  public void setUp() throws IOException {
    helper = new FileSystemBenchmarkHelper(impl);
    file = helper.getTempDir().resolve("file");

    byte[] bytes = new byte[size];
    new Random(3984893212984L).nextBytes(bytes);

    Files.write(file, bytes);
  }

  @AfterExperiment
  public void tearDown() throws IOException {
    helper.tearDown();
  }

  @Benchmark
  public int readInputStreamBuffered(int reps) throws IOException {
    int result = 0;
    byte[] buf = new byte[8192];
    for (int i = 0; i < reps; i++) {
      try (InputStream in = Files.newInputStream(file)) {
        int read = 0;
        while (read != -1) {
          read = in.read(buf);
        }
        result ^= read;
      }
    }
    return result;
  }

  @Benchmark
  public int readFileChannelBuffered(int reps) throws IOException {
    int result = 0;
    ByteBuffer buf = ByteBuffer.allocate(8192);
    for (int i = 0; i < reps; i++) {
      try (FileChannel in = FileChannel.open(file)) {
        int read = 0;
        while (read != -1) {
          read = in.read(buf);
          buf.clear();
        }
        result ^= read;
      }
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(FileReadBenchmark.class, args);
  }
}
