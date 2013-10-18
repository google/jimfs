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

package com.google.jimfs.internal;

import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;
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
        return Configuration.unix().toBuilder()
            .setAttributeViews("basic", "owner", "posix", "unix")
            .build();
      }
    },

    WINDOWS_FULL_ATTRIBUTES {
      @Override
      Configuration createConfiguration() {
        return Configuration.windows().toBuilder()
            .setAttributeViews("basic", "owner", "dos", "acl", "user")
            .build();
      }
    };

    abstract Configuration createConfiguration();
  }
}
