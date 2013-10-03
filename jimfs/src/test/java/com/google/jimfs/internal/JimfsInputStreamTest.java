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

import static com.google.jimfs.testing.TestUtils.bytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;

import java.io.IOException;

/**
 * Tests for {@link JimfsInputStream}.
 *
 * @author Colin Decker
 */
public class JimfsInputStreamTest {

  @Test
  public void testRead_singleByte() throws IOException {
    JimfsInputStream in = newInputStream(2);
    ASSERT.that(in.read()).is(2);
    assertEmpty(in);
  }

  @Test
   public void testRead_wholeArray() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
    byte[] bytes = new byte[8];
    ASSERT.that(in.read(bytes)).is(8);
    assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8), bytes);
    assertEmpty(in);
  }

  @Test
  public void testRead_wholeArray_arrayLarger() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
    byte[] bytes = new byte[12];
    ASSERT.that(in.read(bytes)).is(8);
    assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), bytes);
    assertEmpty(in);
  }

  @Test
  public void testRead_wholeArray_arraySmaller() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
    byte[] bytes = new byte[6];
    ASSERT.that(in.read(bytes)).is(6);
    assertArrayEquals(bytes(1, 2, 3, 4, 5, 6), bytes);
    bytes = new byte[6];
    ASSERT.that(in.read(bytes)).is(2);
    assertArrayEquals(bytes(7, 8, 0, 0, 0, 0), bytes);
    assertEmpty(in);
  }

  @Test
  public void testRead_partialArray() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
    byte[] bytes = new byte[12];
    ASSERT.that(in.read(bytes, 0, 8)).is(8);
    assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), bytes);
    assertEmpty(in);
  }

  @Test
  public void testRead_partialArray_sliceLarger() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
    byte[] bytes = new byte[12];
    ASSERT.that(in.read(bytes, 0, 10)).is(8);
    assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), bytes);
    assertEmpty(in);
  }

  @Test
  public void testRead_partialArray_sliceSmaller() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
    byte[] bytes = new byte[12];
    ASSERT.that(in.read(bytes, 0, 6)).is(6);
    assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 0, 0, 0, 0, 0, 0), bytes);
    ASSERT.that(in.read(bytes, 6, 6)).is(2);
    assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), bytes);
    assertEmpty(in);
  }

  @Test
  public void testRead_partialArray_invalidInput() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5);

    try {
      in.read(new byte[3], -1, 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }

    try {
      in.read(new byte[3], 0, 4);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }

    try {
      in.read(new byte[3], 1, 3);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  @Test
  public void testAvailable() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
    ASSERT.that(in.available()).is(8);
    ASSERT.that(in.read()).is(1);
    ASSERT.that(in.available()).is(7);
    ASSERT.that(in.read(new byte[3])).is(3);
    ASSERT.that(in.available()).is(4);
    ASSERT.that(in.read(new byte[10], 1, 2)).is(2);
    ASSERT.that(in.available()).is(2);
    ASSERT.that(in.read(new byte[10])).is(2);
    ASSERT.that(in.available()).is(0);
  }

  @Test
  public void testSkip() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
    ASSERT.that(in.skip(0)).is(0);
    ASSERT.that(in.skip(-10)).is(0);
    ASSERT.that(in.skip(2)).is(2);
    ASSERT.that(in.read()).is(3);
    ASSERT.that(in.skip(3)).is(3);
    ASSERT.that(in.read()).is(7);
    ASSERT.that(in.skip(10)).is(1);
    assertEmpty(in);
    ASSERT.that(in.skip(10)).is(0);
    assertEmpty(in);
  }

  @Test
  public void testFullyReadInputStream_doesNotChangeStateWhenStoreChanges() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3, 4, 5);
    ASSERT.that(in.read(new byte[5])).is(5);
    assertEmpty(in);

    in.file.asByteStore().append(new byte[10]); // append more bytes to file
    assertEmpty(in);
  }

  @Test
  public void testMark_unsupported() {
    JimfsInputStream in = newInputStream(1, 2, 3);
    ASSERT.that(in.markSupported()).isFalse();

    // mark does nothing
    in.mark(1);

    try {
      // reset throws IOException when unsupported
      in.reset();
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void testClosedInputStream_throwsException() throws IOException {
    JimfsInputStream in = newInputStream(1, 2, 3);
    in.close();

    try {
      in.read();
      fail();
    } catch (IOException expected) {
    }

    try {
      in.read(new byte[3]);
      fail();
    } catch (IOException expected) {
    }

    try {
      in.read(new byte[10], 0, 2);
      fail();
    } catch (IOException expected) {
    }

    try {
      in.skip(10);
      fail();
    } catch (IOException expected) {
    }

    try {
      in.available();
      fail();
    } catch (IOException expected) {
    }

    in.close(); // does nothing
  }

  private static JimfsInputStream newInputStream(int... bytes) {
    byte[] b = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      b[i] = (byte) bytes[i];
    }

    ByteStore store = new ArrayByteStore();
    store.append(b);
    return new JimfsInputStream(new File(1, store));
  }

  private static void assertEmpty(JimfsInputStream in) throws IOException {
    ASSERT.that(in.read()).is(-1);
    ASSERT.that(in.read(new byte[3])).is(-1);
    ASSERT.that(in.read(new byte[10], 1, 5)).is(-1);
    ASSERT.that(in.available()).is(0);
  }
}
