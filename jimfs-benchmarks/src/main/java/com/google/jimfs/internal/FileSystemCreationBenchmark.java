package com.google.jimfs.internal;

import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;
import com.google.jimfs.AttributeViews;
import com.google.jimfs.Jimfs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;

/**
 * @author Colin Decker
 */
public class FileSystemCreationBenchmark {

  private final JimfsFileSystemProvider provider = new JimfsFileSystemProvider();
  private final URI uri = URI.create("jimfs://foo");

  @Param
  private Config config;

  @Benchmark
  public int createFileSystem(int reps) throws IOException {
    int result = 0;
    for (int i = 0; i < reps; i++) {
      FileSystem fs = JimfsFileSystems.newFileSystem(provider, uri, config.createConfiguration());
      result ^= fs.hashCode();
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(FileSystemCreationBenchmark.class, args);
  }

  @SuppressWarnings("unused")
  private enum Config {
    UNIX_DEFAULT {
      @Override
      Jimfs.Configuration createConfiguration() {
        return Jimfs.newUnixLikeConfiguration();
      }
    },

    WINDOWS_DEFAULT {
      @Override
      Jimfs.Configuration createConfiguration() {
        return Jimfs.newWindowsLikeConfiguration();
      }
    },

    UNIX_FULL_ATTRIBUTES {
      @Override
      Jimfs.Configuration createConfiguration() {
        return Jimfs.newUnixLikeConfiguration()
            .setAttributeViews(AttributeViews.unix());
      }
    },

    WINDOWS_FULL_ATTRIBUTES {
      @Override
      Jimfs.Configuration createConfiguration() {
        return Jimfs.newWindowsLikeConfiguration()
            .setAttributeViews(AttributeViews.windows());
      }
    };

    abstract Jimfs.Configuration createConfiguration();
  }
}
