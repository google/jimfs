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

import com.google.common.collect.ImmutableList;
import com.google.jimfs.testing.ByteBufferChannel;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static com.google.jimfs.testing.TestUtils.buffer;
import static com.google.jimfs.testing.TestUtils.buffers;
import static com.google.jimfs.testing.TestUtils.bytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Base tests for {@link ByteStore} implementations.
 *
 * @author Colin Decker
 */
public abstract class AbstractByteStoreTest {

  private ByteStore store;

  protected abstract ByteStore createByteStore();

  @Before
  public void setUp() {
    store = createByteStore();
  }

  private void fillContent(String fill) {
    store.write(0, buffer(fill));
  }

  @Test
  public void testEmpty() {
    assertEquals(0, store.sizeInBytes());
    assertContentEquals("", store);
  }

  @Test
  public void testEmpty_read_singleByte() {
    assertEquals(-1, store.read(0));
    assertEquals(-1, store.read(1));
  }

  @Test
  public void testEmpty_read_byteArray() {
    byte[] array = new byte[10];
    assertEquals(-1, store.read(0, array));
    assertEquals(-1, store.read(0, array, 0, array.length));
    assertArrayEquals(bytes("0000000000"), array);
  }

  @Test
  public void testEmpty_read_singleBuffer() {
    ByteBuffer buffer = ByteBuffer.allocate(10);
    int read = store.read(0, buffer);
    assertEquals(-1, read);
    assertEquals(0, buffer.position());
  }

  @Test
  public void testEmpty_read_multipleBuffers() {
    ByteBuffer buf1 = ByteBuffer.allocate(5);
    ByteBuffer buf2 = ByteBuffer.allocate(5);
    int read = store.read(0, ImmutableList.of(buf1, buf2));
    assertEquals(-1, read);
    assertEquals(0, buf1.position());
    assertEquals(0, buf2.position());
  }

  @Test
  public void testEmpty_write_singleByte_atStart() {
    store.write(0, (byte) 1);
    assertContentEquals("1", store);
  }

  @Test
  public void testEmpty_append_singleByte() {
    store.append((byte) 1);
    assertContentEquals("1", store);
  }

  @Test
  public void testEmpty_write_byteArray_atStart() {
    byte[] bytes = bytes("111111");
    store.write(0, bytes);
    assertContentEquals(bytes, store);
  }

  @Test
  public void testEmpty_append_byteArray() {
    byte[] bytes = bytes("111111");
    store.append(bytes);
    assertContentEquals(bytes, store);
  }

  @Test
  public void testEmpty_write_partialByteArray_atStart() {
    byte[] bytes = bytes("2211111122");
    store.write(0, bytes, 2, 6);
    assertContentEquals("111111", store);
  }

  @Test
  public void testEmpty_append_ByteArray() {
    byte[] bytes = bytes("2211111122");
    store.append(bytes, 2, 6);
    assertContentEquals("111111", store);
  }

  @Test
  public void testEmpty_write_singleBuffer_atStart() {
    store.write(0, buffer("111111"));
    assertContentEquals("111111", store);
  }

  @Test
  public void testEmpty_append_singleBuffer() {
    store.append(buffer("111111"));
    assertContentEquals("111111", store);
  }

  @Test
  public void testEmpty_write_multipleBuffers_atStart() {
    store.write(0, buffers("111", "111"));
    assertContentEquals("111111", store);
  }

  @Test
  public void testEmpty_append_multipleBuffers() {
    store.append(buffers("111", "111"));
    assertContentEquals("111111", store);
  }

  @Test
  public void testEmpty_write_singleByte_atNonZeroPosition() {
    store.write(5, (byte) 1);
    assertContentEquals("000001", store);
  }

  @Test
  public void testEmpty_write_byteArray_atNonZeroPosition() {
    byte[] bytes = bytes("111111");
    store.write(5, bytes);
    assertContentEquals("00000111111", store);
  }

  @Test
  public void testEmpty_write_partialByteArray_atNonZeroPosition() {
    byte[] bytes = bytes("2211111122");
    store.write(5, bytes, 2, 6);
    assertContentEquals("00000111111", store);
  }

  @Test
  public void testEmpty_write_singleBuffer_atNonZeroPosition() {
    store.write(5, buffer("111"));
    assertContentEquals("00000111", store);
  }

  @Test
  public void testEmpty_write_multipleBuffers_atNonZeroPosition() {
    store.write(5, buffers("111", "222"));
    assertContentEquals("00000111222", store);
  }

  @Test
  public void testEmpty_transferFrom_fromStart_countEqualsSrcSize() throws IOException {
    long transferred = store.transferFrom(new ByteBufferChannel(buffer("111111")), 0, 6);
    assertEquals(6, transferred);
    assertContentEquals("111111", store);
  }

  @Test
  public void testEmpty_transferFrom_fromStart_countLessThanSrcSize() throws IOException {
    long transferred = store.transferFrom(new ByteBufferChannel(buffer("111111")), 0, 3);
    assertEquals(3, transferred);
    assertContentEquals("111", store);
  }

  @Test
  public void testEmpty_transferFrom_fromStart_countGreaterThanSrcSize() throws IOException {
    long transferred = store.transferFrom(new ByteBufferChannel(buffer("111111")), 0, 12);
    assertEquals(6, transferred);
    assertContentEquals("111111", store);
  }

  @Test
  public void testEmpty_transferFrom_fromBeyondStart_countEqualsSrcSize() throws IOException {
    long transferred = store.transferFrom(new ByteBufferChannel(buffer("111111")), 4, 6);
    assertEquals(6, transferred);
    assertContentEquals("0000111111", store);
  }

  @Test
  public void testEmpty_transferFrom_fromBeyondStart_countLessThanSrcSize() throws IOException {
    long transferred = store.transferFrom(new ByteBufferChannel(buffer("111111")), 4, 3);
    assertEquals(3, transferred);
    assertContentEquals("0000111", store);
  }

  @Test
  public void testEmpty_transferFrom_fromBeyondStart_countGreaterThanSrcSize() throws IOException {
    long transferred = store.transferFrom(new ByteBufferChannel(buffer("111111")), 4, 12);
    assertEquals(6, transferred);
    assertContentEquals("0000111111", store);
  }

  @Test
  public void testEmpty_transferTo() throws IOException {
    ByteBufferChannel channel = new ByteBufferChannel(100);
    assertEquals(0, store.transferTo(0, 100, channel));
  }

  @Test
  public void testEmpty_copy() {
    ByteStore copy = store.copy();
    assertContentEquals("", copy);
  }

  @Test
  public void testEmpty_truncate_toZero() {
    store.truncate(0);
    assertContentEquals("", store);
  }

  @Test
  public void testEmpty_truncate_sizeUp() {
    store.truncate(10);
    assertContentEquals("", store);
  }

  @Test
  public void testNonEmpty() {
    fillContent("222222");
    assertContentEquals("222222", store);
  }

  @Test
  public void testNonEmpty_read_singleByte() {
    fillContent("123456");
    assertEquals(1, store.read(0));
    assertEquals(2, store.read(1));
    assertEquals(6, store.read(5));
    assertEquals(-1, store.read(6));
    assertEquals(-1, store.read(100));
  }

  @Test
  public void testNonEmpty_read_all_byteArray() {
    fillContent("222222");
    byte[] array = new byte[6];
    assertEquals(6, store.read(0, array));
    assertArrayEquals(bytes("222222"), array);
  }

  @Test
  public void testNonEmpty_read_all_singleBuffer() {
    fillContent("222222");
    ByteBuffer buffer = ByteBuffer.allocate(6);
    assertEquals(6, store.read(0, buffer));
    assertBufferEquals("222222", 0, buffer);
  }

  @Test
  public void testNonEmpty_read_all_multipleBuffers() {
    fillContent("223334");
    ByteBuffer buf1 = ByteBuffer.allocate(3);
    ByteBuffer buf2 = ByteBuffer.allocate(3);
    assertEquals(6, store.read(0, ImmutableList.of(buf1, buf2)));
    assertBufferEquals("223", 0, buf1);
    assertBufferEquals("334", 0, buf2);
  }

  @Test
  public void testNonEmpty_read_all_byteArray_largerThanContent() {
    fillContent("222222");
    byte[] array = new byte[10];
    assertEquals(6, store.read(0, array));
    assertArrayEquals(bytes("2222220000"), array);
    array = new byte[10];
    assertEquals(6, store.read(0, array, 2, 6));
    assertArrayEquals(bytes("0022222200"), array);
  }

  @Test
  public void testNonEmpty_read_all_singleBuffer_largerThanContent() {
    fillContent("222222");
    ByteBuffer buffer = ByteBuffer.allocate(16);
    assertBufferEquals("0000000000000000", 16, buffer);
    assertEquals(6, store.read(0, buffer));
    assertBufferEquals("2222220000000000", 10, buffer);
  }

  @Test
  public void testNonEmpty_read_all_multipleBuffers_largerThanContent() {
    fillContent("222222");
    ByteBuffer buf1 = ByteBuffer.allocate(4);
    ByteBuffer buf2 = ByteBuffer.allocate(8);
    assertEquals(6, store.read(0, ImmutableList.of(buf1, buf2)));
    assertBufferEquals("2222", 0, buf1);
    assertBufferEquals("22000000", 6, buf2);
  }

  @Test
  public void testNonEmpty_read_all_multipleBuffers_extraBuffers() {
    fillContent("222222");
    ByteBuffer buf1 = ByteBuffer.allocate(4);
    ByteBuffer buf2 = ByteBuffer.allocate(8);
    ByteBuffer buf3 = ByteBuffer.allocate(4);
    assertEquals(6, store.read(0, ImmutableList.of(buf1, buf2, buf3)));
    assertBufferEquals("2222", 0, buf1);
    assertBufferEquals("22000000", 6, buf2);
    assertBufferEquals("0000", 4, buf3);
  }

  @Test
  public void testNonEmpty_read_partial_fromStart_byteArray() {
    fillContent("222222");
    byte[] array = new byte[3];
    assertEquals(3, store.read(0, array));
    assertArrayEquals(bytes("222"), array);
    array = new byte[10];
    assertEquals(3, store.read(0, array, 1, 3));
    assertArrayEquals(bytes("0222000000"), array);
  }

  @Test
  public void testNonEmpty_read_partial_fromMiddle_byteArray() {
    fillContent("22223333");
    byte[] array = new byte[3];
    assertEquals(3, store.read(3, array));
    assertArrayEquals(bytes("233"), array);
    array = new byte[10];
    assertEquals(3, store.read(3, array, 1, 3));
    assertArrayEquals(bytes("0233000000"), array);
  }

  @Test
  public void testNonEmpty_read_partial_fromEnd_byteArray() {
    fillContent("2222222222");
    byte[] array = new byte[3];
    assertEquals(2, store.read(8, array));
    assertArrayEquals(bytes("220"), array);
    array = new byte[10];
    assertEquals(2, store.read(8, array, 1, 3));
    assertArrayEquals(bytes("0220000000"), array);
  }

  @Test
  public void testNonEmpty_read_partial_fromStart_singleBuffer() {
    fillContent("222222");
    ByteBuffer buffer = ByteBuffer.allocate(3);
    assertEquals(3, store.read(0, buffer));
    assertBufferEquals("222", 0, buffer);
  }

  @Test
  public void testNonEmpty_read_partial_fromMiddle_singleBuffer() {
    fillContent("22223333");
    ByteBuffer buffer = ByteBuffer.allocate(3);
    assertEquals(3, store.read(3, buffer));
    assertBufferEquals("233", 0, buffer);
  }

  @Test
  public void testNonEmpty_read_partial_fromEnd_singleBuffer() {
    fillContent("2222222222");
    ByteBuffer buffer = ByteBuffer.allocate(3);
    assertEquals(2, store.read(8, buffer));
    assertBufferEquals("220", 1, buffer);
  }

  @Test
  public void testNonEmpty_read_partial_fromStart_multipleBuffers() {
    fillContent("12345678");
    ByteBuffer buf1 = ByteBuffer.allocate(2);
    ByteBuffer buf2 = ByteBuffer.allocate(2);
    assertEquals(4, store.read(0, ImmutableList.of(buf1, buf2)));
    assertBufferEquals("12", 0, buf1);
    assertBufferEquals("34", 0, buf2);
  }

  @Test
  public void testNonEmpty_read_partial_fromMiddle_multipleBuffers() {
    fillContent("12345678");
    ByteBuffer buf1 = ByteBuffer.allocate(2);
    ByteBuffer buf2 = ByteBuffer.allocate(2);
    assertEquals(4, store.read(3, ImmutableList.of(buf1, buf2)));
    assertBufferEquals("45", 0, buf1);
    assertBufferEquals("67", 0, buf2);
  }

  @Test
  public void testNonEmpty_read_partial_fromEnd_multipleBuffers() {
    fillContent("123456789");
    ByteBuffer buf1 = ByteBuffer.allocate(2);
    ByteBuffer buf2 = ByteBuffer.allocate(2);
    assertEquals(3, store.read(6, ImmutableList.of(buf1, buf2)));
    assertBufferEquals("78", 0, buf1);
    assertBufferEquals("90", 1, buf2);
  }

  @Test
  public void testNonEmpty_read_fromPastEnd_byteArray() {
    fillContent("123");
    byte[] array = new byte[3];
    assertEquals(-1, store.read(3, array));
    assertArrayEquals(bytes("000"), array);
    assertEquals(-1, store.read(3, array, 0, 2));
    assertArrayEquals(bytes("000"), array);
  }

  @Test
  public void testNonEmpty_read_fromPastEnd_singleBuffer() {
    fillContent("123");
    ByteBuffer buffer = ByteBuffer.allocate(3);
    store.read(3, buffer);
    assertBufferEquals("000", 3, buffer);
  }

  @Test
  public void testNonEmpty_read_fromPastEnd_multipleBuffers() {
    fillContent("123");
    ByteBuffer buf1 = ByteBuffer.allocate(2);
    ByteBuffer buf2 = ByteBuffer.allocate(2);
    assertEquals(-1, store.read(6, ImmutableList.of(buf1, buf2)));
    assertBufferEquals("00", 2, buf1);
    assertBufferEquals("00", 2, buf2);
  }

  @Test
  public void testNonEmpty_write_partial_fromStart_singleByte() {
    fillContent("222222");
    assertEquals(1, store.write(0, (byte) 1));
    assertContentEquals("122222", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromMiddle_singleByte() {
    fillContent("222222");
    assertEquals(1, store.write(3, (byte) 1));
    assertContentEquals("222122", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromEnd_singleByte() {
    fillContent("222222");
    assertEquals(1, store.write(6, (byte) 1));
    assertContentEquals("2222221", store);
  }

  @Test
  public void testNonEmpty_append_singleByte() {
    fillContent("222222");
    assertEquals(1, store.append((byte) 1));
    assertContentEquals("2222221", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromStart_byteArray() {
    fillContent("222222");
    assertEquals(3, store.write(0, bytes("111")));
    assertContentEquals("111222", store);
    assertEquals(2, store.write(0, bytes("333333"), 0, 2));
    assertContentEquals("331222", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromMiddle_byteArray() {
    fillContent("22222222");
    assertEquals(3, store.write(3, buffer("111")));
    assertContentEquals("22211122", store);
    assertEquals(2, store.write(5, bytes("333333"), 1, 2));
    assertContentEquals("22211332", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromBeforeEnd_byteArray() {
    fillContent("22222222");
    assertEquals(3, store.write(6, bytes("111")));
    assertContentEquals("222222111", store);
    assertEquals(2, store.write(8, bytes("333333"), 2, 2));
    assertContentEquals("2222221133", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromEnd_byteArray() {
    fillContent("222222");
    assertEquals(3, store.write(6, bytes("111")));
    assertContentEquals("222222111", store);
    assertEquals(2, store.write(9, bytes("333333"), 3, 2));
    assertContentEquals("22222211133", store);
  }

  @Test
  public void testNonEmpty_append_byteArray() {
    fillContent("222222");
    assertEquals(3, store.append(bytes("111")));
    assertContentEquals("222222111", store);
    assertEquals(2, store.append(bytes("333333"), 3, 2));
    assertContentEquals("22222211133", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromPastEnd_byteArray() {
    fillContent("222222");
    assertEquals(3, store.write(8, bytes("111")));
    assertContentEquals("22222200111", store);
    assertEquals(2, store.write(13, bytes("333333"), 4, 2));
    assertContentEquals("222222001110033", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromStart_singleBuffer() {
    fillContent("222222");
    assertEquals(3, store.write(0, buffer("111")));
    assertContentEquals("111222", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromMiddle_singleBuffer() {
    fillContent("22222222");
    assertEquals(3, store.write(3, buffer("111")));
    assertContentEquals("22211122", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromBeforeEnd_singleBuffer() {
    fillContent("22222222");
    assertEquals(3, store.write(6, buffer("111")));
    assertContentEquals("222222111", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromEnd_singleBuffer() {
    fillContent("222222");
    assertEquals(3, store.write(6, buffer("111")));
    assertContentEquals("222222111", store);
  }

  @Test
  public void testNonEmpty_append_singleBuffer() {
    fillContent("222222");
    assertEquals(3, store.append(buffer("111")));
    assertContentEquals("222222111", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromPastEnd_singleBuffer() {
    fillContent("222222");
    assertEquals(3, store.write(8, buffer("111")));
    assertContentEquals("22222200111", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromStart_multipleBuffers() {
    fillContent("222222");
    assertEquals(4, store.write(0, buffers("11", "33")));
    assertContentEquals("113322", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromMiddle_multipleBuffers() {
    fillContent("22222222");
    assertEquals(4, store.write(2, buffers("11", "33")));
    assertContentEquals("22113322", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromBeforeEnd_multipleBuffers() {
    fillContent("22222222");
    assertEquals(6, store.write(6, buffers("111", "333")));
    assertContentEquals("222222111333", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromEnd_multipleBuffers() {
    fillContent("222222");
    assertEquals(6, store.write(6, buffers("111", "333")));
    assertContentEquals("222222111333", store);
  }

  @Test
  public void testNonEmpty_append_multipleBuffers() {
    fillContent("222222");
    assertEquals(6, store.append(buffers("111", "333")));
    assertContentEquals("222222111333", store);
  }

  @Test
  public void testNonEmpty_write_partial_fromPastEnd_multipleBuffers() {
    fillContent("222222");
    assertEquals(4, store.write(10, buffers("11", "33")));
    assertContentEquals("22222200001133", store);
  }

  @Test
  public void testNonEmpty_write_overwrite_sameLength() {
    fillContent("2222");
    assertEquals(4, store.write(0, buffer("1234")));
    assertContentEquals("1234", store);
  }

  @Test
  public void testNonEmpty_write_overwrite_greaterLength() {
    fillContent("2222");
    assertEquals(8, store.write(0, buffer("12345678")));
    assertContentEquals("12345678", store);
  }

  @Test
  public void testNonEmpty_transferTo_fromStart_countEqualsSize() throws IOException {
    fillContent("123456");
    ByteBufferChannel channel = new ByteBufferChannel(10);
    assertEquals(6, store.transferTo(0, 6, channel));
    assertBufferEquals("1234560000", 4, channel.buffer());
  }

  @Test
  public void testNonEmpty_transferTo_fromStart_countLessThanSize() throws IOException {
    fillContent("123456");
    ByteBufferChannel channel = new ByteBufferChannel(10);
    assertEquals(4, store.transferTo(0, 4, channel));
    assertBufferEquals("1234000000", 6, channel.buffer());
  }

  @Test
  public void testNonEmpty_transferTo_fromMiddle_countEqualsSize() throws IOException {
    fillContent("123456");
    ByteBufferChannel channel = new ByteBufferChannel(10);
    assertEquals(2, store.transferTo(4, 6, channel));
    assertBufferEquals("5600000000", 8, channel.buffer());
  }

  @Test
  public void testNonEmpty_transferTo_fromMiddle_countLessThanSize() throws IOException {
    fillContent("12345678");
    ByteBufferChannel channel = new ByteBufferChannel(10);
    assertEquals(4, store.transferTo(3, 4, channel));
    assertBufferEquals("4567000000", 6, channel.buffer());
  }

  @Test
  public void testNonEmpty_transferFrom_toStart_countEqualsSrcSize() throws IOException {
    fillContent("22222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("11111"));
    assertEquals(5, store.transferFrom(channel, 0, 5));
    assertContentEquals("11111222", store);
  }

  @Test
  public void testNonEmpty_transferFrom_toStart_countLessThanSrcSize() throws IOException {
    fillContent("22222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("11111"));
    assertEquals(3, store.transferFrom(channel, 0, 3));
    assertContentEquals("11122222", store);
  }

  @Test
  public void testNonEmpty_transferFrom_toStart_countGreaterThanSrcSize() throws IOException {
    fillContent("22222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("11111"));
    assertEquals(5, store.transferFrom(channel, 0, 10));
    assertContentEquals("11111222", store);
  }

  @Test
  public void testNonEmpty_transferFrom_toMiddle_countEqualsSrcSize() throws IOException {
    fillContent("22222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("1111"));
    assertEquals(4, store.transferFrom(channel, 2, 4));
    assertContentEquals("22111122", store);
  }

  @Test
  public void testNonEmpty_transferFrom_toMiddle_countLessThanSrcSize() throws IOException {
    fillContent("22222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("11111"));
    assertEquals(3, store.transferFrom(channel, 2, 3));
    assertContentEquals("22111222", store);
  }

  @Test
  public void testNonEmpty_transferFrom_toMiddle_countGreaterThanSrcSize() throws IOException {
    fillContent("22222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("1111"));
    assertEquals(4, store.transferFrom(channel, 2, 100));
    assertContentEquals("22111122", store);
  }

  @Test
  public void testNonEmpty_transferFrom_toMiddle_transferGoesBeyondContentSize()
      throws IOException {
    fillContent("222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("111111"));
    assertEquals(6, store.transferFrom(channel, 4, 6));
    assertContentEquals("2222111111", store);
  }

  @Test
  public void testNonEmpty_transferFrom_toEnd() throws IOException {
    fillContent("222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("111111"));
    assertEquals(6, store.transferFrom(channel, 6, 6));
    assertContentEquals("222222111111", store);
  }

  @Test
  public void testNonEmpty_transferFrom_toPastEnd() throws IOException {
    fillContent("222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("111111"));
    assertEquals(6, store.transferFrom(channel, 10, 6));
    assertContentEquals("2222220000111111", store);
  }

  @Test
  public void testNonEmpty_transferFrom_hugeOverestimateCount() throws IOException {
    fillContent("222222");
    ByteBufferChannel channel = new ByteBufferChannel(buffer("111111"));
    assertEquals(6, store.transferFrom(channel, 6, 1024 * 1024 * 10));
    assertContentEquals("222222111111", store);
  }

  @Test
  public void testNonEmpty_copy() {
    fillContent("123456");
    ByteStore copy = store.copy();
    assertContentEquals("123456", copy);
  }

  @Test
  public void testNonEmpty_copy_multipleTimes() {
    /*
    This test exposes a bug where the position of the new buffer in ByteBufferByteStore wasn't
    being reset to 0 after the old buffer's content was copied into it. If the buffer was then
    copied again, the copy would have all 0s because only the portion of the buffer before its
    position would have been written, while the portion of the buffer after the position would
    be copied.
     */
    fillContent("123456");
    ByteStore copy = store.copy().copy();
    assertContentEquals("123456", copy);
  }

  @Test
  public void testNonEmpty_truncate_toZero() {
    fillContent("123456");
    store.truncate(0);
    assertContentEquals("", store);
  }

  @Test
  public void testNonEmpty_truncate_partial() {
    fillContent("12345678");
    store.truncate(5);
    assertContentEquals("12345", store);
  }

  @Test
  public void testNonEmpty_truncate_sizeUp() {
    fillContent("123456");
    store.truncate(12);
    assertContentEquals("123456", store);
  }

  @Test
  public void testIllegalArguments() throws IOException {
    try {
      store.write(-1, ByteBuffer.allocate(10));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      store.write(-1, ImmutableList.of(ByteBuffer.allocate(10)));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      store.transferFrom(fakeChannel(), -1, 10);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      store.transferFrom(fakeChannel(), 10, -1);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      store.transferTo(-1, 10, fakeChannel());
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      store.transferTo(10, -1, fakeChannel());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  private static void assertBufferEquals(String expected, ByteBuffer actual) {
    assertEquals(expected.length(), actual.capacity());
    assertArrayEquals(bytes(expected), actual.array());
  }

  private static void assertBufferEquals(String expected, int remaining, ByteBuffer actual) {
    assertBufferEquals(expected, actual);
    assertEquals(remaining, actual.remaining());
  }

  private static void assertContentEquals(String expected, ByteStore actual) {
    assertContentEquals(bytes(expected), actual);
  }

  private static void assertContentEquals(byte[] expected, ByteStore actual) {
    assertEquals(expected.length, actual.sizeInBytes());
    byte[] actualBytes = new byte[actual.sizeInBytes()];
    actual.read(0, ByteBuffer.wrap(actualBytes));
    assertArrayEquals(expected, actualBytes);
  }

  private static FileChannel fakeChannel() {
    return new FileChannel() {
      @Override
      public int read(ByteBuffer dst) throws IOException {
        return 0;
      }

      @Override
      public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        return 0;
      }

      @Override
      public int write(ByteBuffer src) throws IOException {
        return 0;
      }

      @Override
      public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return 0;
      }

      @Override
      public long position() throws IOException {
        return 0;
      }

      @Override
      public FileChannel position(long newPosition) throws IOException {
        return null;
      }

      @Override
      public long size() throws IOException {
        return 0;
      }

      @Override
      public FileChannel truncate(long size) throws IOException {
        return null;
      }

      @Override
      public void force(boolean metaData) throws IOException {
      }

      @Override
      public long transferTo(long position, long count, WritableByteChannel target)
          throws IOException {
        return 0;
      }

      @Override
      public long transferFrom(ReadableByteChannel src, long position, long count)
          throws IOException {
        return 0;
      }

      @Override
      public int read(ByteBuffer dst, long position) throws IOException {
        return 0;
      }

      @Override
      public int write(ByteBuffer src, long position) throws IOException {
        return 0;
      }

      @Override
      public MappedByteBuffer map(MapMode mode, long position, long size)
          throws IOException {
        return null;
      }

      @Override
      public FileLock lock(long position, long size, boolean shared) throws IOException {
        return null;
      }

      @Override
      public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        return null;
      }

      @Override
      protected void implCloseChannel() throws IOException {
      }
    };
  }
}
