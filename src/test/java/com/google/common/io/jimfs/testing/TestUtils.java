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

package com.google.common.io.jimfs.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.io.jimfs.path.JimfsPath;
import com.google.common.io.jimfs.path.Name;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Colin Decker
 */
public final class TestUtils {

  private TestUtils() {}

  /**
   * Returns a path with no file system, no root and no names. Can't be used for much of anything
   * other than just being an instance of {@link JimfsPath}.
   */
  public static JimfsPath fakePath() {
    return new JimfsPath(null, null, ImmutableList.<Name>of());
  }

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

  public static byte[] concat(byte[]... byteArrays) {
    int totalLength = 0;
    for (byte[] byteArray : byteArrays) {
      totalLength += byteArray.length;
    }
    byte[] result = new byte[totalLength];
    int pos = 0;
    for (byte[] array : byteArrays) {
      System.arraycopy(array, 0, result, pos, array.length);
      pos += array.length;
    }
    return result;
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
}
