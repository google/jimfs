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

package com.google.common.jimfs;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.junit.Assert.assertFalse;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Colin Decker
 */
public final class TestUtils {

  private TestUtils() {}

  public static byte[] bytes(int... bytes) {
    byte[] result = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      result[i] = (byte) bytes[i];
    }
    return result;
  }

  public static byte[] bytes(String bytes) {
    byte[] result = new byte[bytes.length()];
    for (int i = 0; i < bytes.length(); i++) {
      String digit = bytes.substring(i, i + 1);
      result[i] = Byte.parseByte(digit);
    }
    return result;
  }

  public static byte[] preFilledBytes(int length, int fillValue) {
    byte[] result = new byte[length];
    Arrays.fill(result, (byte) fillValue);
    return result;
  }

  public static byte[] preFilledBytes(int length) {
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte) i;
    }
    return bytes;
  }

  public static ByteBuffer buffer(String bytes) {
    return ByteBuffer.wrap(bytes(bytes));
  }

  public static Iterable<ByteBuffer> buffers(String... bytes) {
    List<ByteBuffer> result = new ArrayList<>();
    for (String b : bytes) {
      result.add(buffer(b));
    }
    return result;
  }

  /**
   * Returns a number of permutations of the given path that should all locate the same file.
   */
  public static Iterable<Path> permutations(Path path) throws IOException {
    Path workingDir =
        path.getFileSystem().getPath("").toRealPath();
    boolean directory = Files.isDirectory(path);

    Set<Path> results = new HashSet<>();
    results.add(path);
    if (path.isAbsolute()) {
      results.add(workingDir.relativize(path));
    } else {
      results.add(workingDir.resolve(path));
    }
    if (directory) {
      for (Path p : ImmutableList.copyOf(results)) {
        results.add(p.resolve("."));
        results.add(p.resolve(".").resolve("."));
        Path fileName = p.getFileName();
        if (fileName != null
            && !fileName.toString().equals(".")
            && !fileName.toString().equals("..")) {
          results.add(p.resolve("..").resolve(fileName));
          results.add(
              p.resolve("..").resolve(".").resolve(fileName));
          results.add(
              p.resolve("..").resolve(".").resolve(fileName).resolve("."));
          results.add(
              p.resolve(".").resolve("..").resolve(".").resolve(fileName));
        }
      }

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
        for (Path child : stream) {
          if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
            Path childName = child.getFileName();
            for (Path p : ImmutableList.copyOf(results)) {
              results.add(p.resolve(childName).resolve(".."));
              results.add(
                  p.resolve(childName).resolve(".").resolve(".").resolve(".."));
              results.add(
                  p.resolve(childName).resolve("..").resolve("."));
              results.add(
                  p.resolve(childName).resolve("..").resolve(childName).resolve(".").resolve(".."));
            }
            break; // no need to add more than one child
          }
        }
      }
    }
    return results;
  }

  // equivalent to the Junit 4.11 method.
  public static void assertNotEquals(Object unexpected, Object actual) {
    assertFalse(
        "Values should be different. Actual: " + actual, Objects.equals(unexpected, actual));
  }

  static RegularFile regularFile(int size) {
    RegularFile file = RegularFile.create(0, new HeapDisk(8096, 1000, 1000));
    try {
      file.write(0, new byte[size], 0, size);
      return file;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
