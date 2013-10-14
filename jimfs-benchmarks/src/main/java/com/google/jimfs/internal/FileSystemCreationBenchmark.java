package com.google.jimfs.internal;

import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;
import com.google.jimfs.AttributeConfiguration;
import com.google.jimfs.Configuration;

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
      Configuration createConfiguration() {
        return Configuration.unix();
      }
    },

    WINDOWS_DEFAULT {
      @Override
      Configuration createConfiguration() {
        return Configuration.windows();
      }
    },

    UNIX_FULL_ATTRIBUTES {
      @Override
      Configuration createConfiguration() {
        return Configuration.unix()
            .setAttributeConfiguration(AttributeConfiguration.unix());
      }
    },

    WINDOWS_FULL_ATTRIBUTES {
      @Override
      Configuration createConfiguration() {
        return Configuration.windows()
            .setAttributeConfiguration(AttributeConfiguration.windows());
      }
    };

    abstract Configuration createConfiguration();
  }
}
