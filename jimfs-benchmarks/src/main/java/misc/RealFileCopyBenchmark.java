package misc;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.google.caliper.AfterExperiment;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Benchmark comparing copying files using buffered copying vs. transferTo vs. transferFrom.
 *
 * @author Colin Decker
 */
public class RealFileCopyBenchmark {

  @Param({"1000", "100000", "10000000"})
  private int size;

  private final ByteBuffer buffer8k = ByteBuffer.allocateDirect(8192);
  private final ByteBuffer buffer32k = ByteBuffer.allocateDirect(32768);

  private Path source;
  private Path target;

  @BeforeExperiment
  public void setUp() throws IOException {
    byte[] bytes = new byte[size];
    new Random(98234897298123L).nextBytes(bytes);

    source = Files.createTempFile("RealFileCopyBenchmark-source", "tmp");
    target = Files.createTempFile("RealFileCopyBenchmark-target", "tmp");

    Files.write(source, bytes);
  }

  @AfterExperiment
  public void tearDown() throws IOException {
    try {
      if (source != null) {
        Files.deleteIfExists(source);
      }
    } finally {
      if (target != null) {
        Files.deleteIfExists(target);
      }
    }
  }

  @Benchmark
  public int bufferedCopy8k(int reps) throws IOException {
    return bufferedCopy(reps, buffer8k);
  }

  @Benchmark
  public int bufferedCopy32k(int reps) throws IOException {
    return bufferedCopy(reps, buffer32k);
  }

  private int bufferedCopy(int reps, ByteBuffer buffer) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      try (FileChannel in = FileChannel.open(source, READ);
           FileChannel out = FileChannel.open(target, WRITE, TRUNCATE_EXISTING)) {
        int read;
        buffer.clear();
        while ((read = in.read(buffer)) != -1) {
          buffer.flip();
          out.write(buffer);
          buffer.compact();
          result ^= read;
        }
      }
    }
    return result;
  }

  @Benchmark
  public int transferTo(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      try (FileChannel in = FileChannel.open(source, READ);
           FileChannel out = FileChannel.open(target, WRITE, TRUNCATE_EXISTING)) {
        long pos = 0;
        long size = in.size();
        while (pos < size) {
          pos += in.transferTo(pos, size - pos, out);
        }
      }
    }
    return result;
  }

  @Benchmark
  public int transferFrom(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      try (FileChannel in = FileChannel.open(source, READ);
           FileChannel out = FileChannel.open(target, WRITE, TRUNCATE_EXISTING)) {
        long pos = 0;
        long size = in.size();
        while (pos < size) {
          pos += out.transferFrom(in, pos, size - pos);
        }
      }
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(RealFileCopyBenchmark.class, args);
  }
}
