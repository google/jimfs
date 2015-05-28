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

import static com.google.common.jimfs.TestUtils.buffer;
import static com.google.common.jimfs.TestUtils.buffers;
import static com.google.common.jimfs.TestUtils.bytes;
import static com.google.common.primitives.Bytes.concat;
import static org.junit.Assert.assertArrayEquals;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Tests for {@link RegularFile} and by extension for {@link HeapDisk}. These tests test files
 * created by a heap disk in a number of different states.
 *
 * @author Colin Decker
 */
public class RegularFileTest {

  /**
   * Returns a test suite for testing file methods with a variety of {@code HeapDisk}
   * configurations.
   */
  public static TestSuite suite() {
    TestSuite suite = new TestSuite();

    for (ReuseStrategy reuseStrategy : EnumSet.allOf(ReuseStrategy.class)) {
      TestSuite suiteForReuseStrategy = new TestSuite(reuseStrategy.toString());
      Set<List<Integer>> sizeOptions =
          Sets.cartesianProduct(ImmutableList.of(BLOCK_SIZES, CACHE_SIZES));
      for (List<Integer> options : sizeOptions) {
        int blockSize = options.get(0);
        int cacheSize = options.get(1);
        if (cacheSize > 0 && cacheSize < blockSize) {
          // skip cases where the cache size is not -1 (all) or 0 (none) but it is < blockSize,
          // because this is equivalent to a cache size of 0
          continue;
        }

        TestConfiguration state = new TestConfiguration(blockSize, cacheSize, reuseStrategy);
        TestSuite suiteForTest = new TestSuite(state.toString());
        for (Method method : TEST_METHODS) {
          RegularFileTestRunner tester = new RegularFileTestRunner(method.getName(), state);
          suiteForTest.addTest(tester);
        }
        suiteForReuseStrategy.addTest(suiteForTest);
      }
      suite.addTest(suiteForReuseStrategy);
    }

    return suite;
  }

  public static final ImmutableSet<Integer> BLOCK_SIZES = ImmutableSet.of(2, 8, 128, 8192);
  public static final ImmutableSet<Integer> CACHE_SIZES = ImmutableSet.of(0, 4, 16, 128, -1);

  private static final ImmutableList<Method> TEST_METHODS =
      FluentIterable.from(Arrays.asList(RegularFileTestRunner.class.getDeclaredMethods()))
          .filter(
              new Predicate<Method>() {
                @Override
                public boolean apply(Method method) {
                  return method.getName().startsWith("test")
                      && Modifier.isPublic(method.getModifiers())
                      && method.getParameterTypes().length == 0;
                }
              })
          .toList();

  /**
   * Different strategies for handling reuse of disks and/or files between tests, intended to
   * ensure that {@link HeapDisk} operates properly in a variety of usage states including newly
   * created, having created files that have not been deleted yet, having created files that have
   * been deleted, and having created files some of which have been deleted and some of which have
   * not.
   */
  public enum ReuseStrategy {
    /**
     * Creates a new disk for each test.
     */
    NEW_DISK,
    /**
     * Retains files after each test, forcing new blocks to be allocated.
     */
    KEEP_FILES,
    /**
     * Deletes files after each test, allowing caching to be used if enabled.
     */
    DELETE_FILES,
    /**
     * Randomly keeps or deletes a file after each test.
     */
    KEEP_OR_DELETE_FILES
  }

  /**
   * Configuration for a set of test cases.
   */
  public static final class TestConfiguration {

    private final int blockSize;
    private final int cacheSize;
    private final ReuseStrategy reuseStrategy;

    private HeapDisk disk;

    public TestConfiguration(int blockSize, int cacheSize, ReuseStrategy reuseStrategy) {
      this.blockSize = blockSize;
      this.cacheSize = cacheSize;
      this.reuseStrategy = reuseStrategy;

      if (reuseStrategy != ReuseStrategy.NEW_DISK) {
        this.disk = createDisk();
      }
    }

    private HeapDisk createDisk() {
      int maxCachedBlockCount = cacheSize == -1 ? Integer.MAX_VALUE : (cacheSize / blockSize);
      return new HeapDisk(blockSize, Integer.MAX_VALUE, maxCachedBlockCount);
    }

    public RegularFile createRegularFile() {
      if (reuseStrategy == ReuseStrategy.NEW_DISK) {
        disk = createDisk();
      }
      return RegularFile.create(0, disk);
    }

    public void tearDown(RegularFile file) {
      switch (reuseStrategy) {
        case DELETE_FILES:
          file.deleted();
          break;
        case KEEP_OR_DELETE_FILES:
          if (new Random().nextBoolean()) {
            file.deleted();
          }
          break;
        case KEEP_FILES:
          break;
        default:
          break;
      }
    }

    @Override
    public String toString() {
      return reuseStrategy + " [" + blockSize + ", " + cacheSize + "]";
    }
  }

  /**
   * Actual test cases for testing RegularFiles.
   */
  public static class RegularFileTestRunner extends TestCase {

    private final TestConfiguration configuration;

    protected RegularFile file;

    public RegularFileTestRunner(String methodName, TestConfiguration configuration) {
      super(methodName);
      this.configuration = configuration;
    }

    @Override
    public String getName() {
      return super.getName() + " [" + configuration + "]";
    }

    @Override
    public void setUp() {
      file = configuration.createRegularFile();
    }

    @Override
    public void tearDown() {
      configuration.tearDown(file);
    }

    private void fillContent(String fill) throws IOException {
      file.write(0, buffer(fill));
    }

    public void testEmpty() {
      assertEquals(0, file.size());
      assertContentEquals("", file);
    }

    public void testEmpty_read_singleByte() {
      assertEquals(-1, file.read(0));
      assertEquals(-1, file.read(1));
    }

    public void testEmpty_read_byteArray() {
      byte[] array = new byte[10];
      assertEquals(-1, file.read(0, array, 0, array.length));
      assertArrayEquals(bytes("0000000000"), array);
    }

    public void testEmpty_read_singleBuffer() {
      ByteBuffer buffer = ByteBuffer.allocate(10);
      int read = file.read(0, buffer);
      assertEquals(-1, read);
      assertEquals(0, buffer.position());
    }

    public void testEmpty_read_multipleBuffers() {
      ByteBuffer buf1 = ByteBuffer.allocate(5);
      ByteBuffer buf2 = ByteBuffer.allocate(5);
      long read = file.read(0, ImmutableList.of(buf1, buf2));
      assertEquals(-1, read);
      assertEquals(0, buf1.position());
      assertEquals(0, buf2.position());
    }

    public void testEmpty_write_singleByte_atStart() throws IOException {
      file.write(0, (byte) 1);
      assertContentEquals("1", file);
    }

    public void testEmpty_write_byteArray_atStart() throws IOException {
      byte[] bytes = bytes("111111");
      file.write(0, bytes, 0, bytes.length);
      assertContentEquals(bytes, file);
    }

    public void testEmpty_write_partialByteArray_atStart() throws IOException {
      byte[] bytes = bytes("2211111122");
      file.write(0, bytes, 2, 6);
      assertContentEquals("111111", file);
    }

    public void testEmpty_write_singleBuffer_atStart() throws IOException {
      file.write(0, buffer("111111"));
      assertContentEquals("111111", file);
    }

    public void testEmpty_write_multipleBuffers_atStart() throws IOException {
      file.write(0, buffers("111", "111"));
      assertContentEquals("111111", file);
    }

    public void testEmpty_write_singleByte_atNonZeroPosition() throws IOException {
      file.write(5, (byte) 1);
      assertContentEquals("000001", file);
    }

    public void testEmpty_write_byteArray_atNonZeroPosition() throws IOException {
      byte[] bytes = bytes("111111");
      file.write(5, bytes, 0, bytes.length);
      assertContentEquals("00000111111", file);
    }

    public void testEmpty_write_partialByteArray_atNonZeroPosition() throws IOException {
      byte[] bytes = bytes("2211111122");
      file.write(5, bytes, 2, 6);
      assertContentEquals("00000111111", file);
    }

    public void testEmpty_write_singleBuffer_atNonZeroPosition() throws IOException {
      file.write(5, buffer("111"));
      assertContentEquals("00000111", file);
    }

    public void testEmpty_write_multipleBuffers_atNonZeroPosition() throws IOException {
      file.write(5, buffers("111", "222"));
      assertContentEquals("00000111222", file);
    }

    public void testEmpty_write_noBytesArray_atStart() throws IOException {
      file.write(0, bytes(), 0, 0);
      assertContentEquals(bytes(), file);
    }

    public void testEmpty_write_noBytesArray_atNonZeroPosition() throws IOException {
      file.write(5, bytes(), 0, 0);
      assertContentEquals(bytes("00000"), file);
    }

    public void testEmpty_write_noBytesBuffer_atStart() throws IOException {
      file.write(0, buffer(""));
      assertContentEquals(bytes(), file);
    }

    public void testEmpty_write_noBytesBuffer_atNonZeroPosition() throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(0);
      file.write(5, buffer);
      assertContentEquals(bytes("00000"), file);
    }

    public void testEmpty_write_noBytesBuffers_atStart() throws IOException {
      file.write(0, ImmutableList.of(buffer(""), buffer(""), buffer("")));
      assertContentEquals(bytes(), file);
    }

    public void testEmpty_write_noBytesBuffers_atNonZeroPosition() throws IOException {
      file.write(5, ImmutableList.of(buffer(""), buffer(""), buffer("")));
      assertContentEquals(bytes("00000"), file);
    }

    public void testEmpty_transferFrom_fromStart_countEqualsSrcSize() throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("111111")), 0, 6);
      assertEquals(6, transferred);
      assertContentEquals("111111", file);
    }

    public void testEmpty_transferFrom_fromStart_countLessThanSrcSize() throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("111111")), 0, 3);
      assertEquals(3, transferred);
      assertContentEquals("111", file);
    }

    public void testEmpty_transferFrom_fromStart_countGreaterThanSrcSize() throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("111111")), 0, 12);
      assertEquals(6, transferred);
      assertContentEquals("111111", file);
    }

    public void testEmpty_transferFrom_fromBeyondStart_countEqualsSrcSize() throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("111111")), 4, 6);
      assertEquals(6, transferred);
      assertContentEquals("0000111111", file);
    }

    public void testEmpty_transferFrom_fromBeyondStart_countLessThanSrcSize() throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("111111")), 4, 3);
      assertEquals(3, transferred);
      assertContentEquals("0000111", file);
    }

    public void testEmpty_transferFrom_fromBeyondStart_countGreaterThanSrcSize()
        throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("111111")), 4, 12);
      assertEquals(6, transferred);
      assertContentEquals("0000111111", file);
    }

    public void testEmpty_transferFrom_fromStart_noBytes_countEqualsSrcSize() throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("")), 0, 0);
      assertEquals(0, transferred);
      assertContentEquals(bytes(), file);
    }

    public void testEmpty_transferFrom_fromStart_noBytes_countGreaterThanSrcSize()
        throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("")), 0, 10);
      assertEquals(0, transferred);
      assertContentEquals(bytes(), file);
    }

    public void testEmpty_transferFrom_fromBeyondStart_noBytes_countEqualsSrcSize()
        throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("")), 5, 0);
      assertEquals(0, transferred);
      assertContentEquals(bytes("00000"), file);
    }

    public void testEmpty_transferFrom_fromBeyondStart_noBytes_countGreaterThanSrcSize()
        throws IOException {
      long transferred = file.transferFrom(new ByteBufferChannel(buffer("")), 5, 10);
      assertEquals(0, transferred);
      assertContentEquals(bytes("00000"), file);
    }

    public void testEmpty_transferTo() throws IOException {
      ByteBufferChannel channel = new ByteBufferChannel(100);
      assertEquals(0, file.transferTo(0, 100, channel));
    }

    public void testEmpty_copy() throws IOException {
      RegularFile copy = file.copyWithoutContent(1);
      assertContentEquals("", copy);
    }

    public void testEmpty_truncate_toZero() throws IOException {
      file.truncate(0);
      assertContentEquals("", file);
    }

    public void testEmpty_truncate_sizeUp() throws IOException {
      file.truncate(10);
      assertContentEquals("", file);
    }

    public void testNonEmpty() throws IOException {
      fillContent("222222");
      assertContentEquals("222222", file);
    }

    public void testNonEmpty_read_singleByte() throws IOException {
      fillContent("123456");
      assertEquals(1, file.read(0));
      assertEquals(2, file.read(1));
      assertEquals(6, file.read(5));
      assertEquals(-1, file.read(6));
      assertEquals(-1, file.read(100));
    }

    public void testNonEmpty_read_all_byteArray() throws IOException {
      fillContent("222222");
      byte[] array = new byte[6];
      assertEquals(6, file.read(0, array, 0, array.length));
      assertArrayEquals(bytes("222222"), array);
    }

    public void testNonEmpty_read_all_singleBuffer() throws IOException {
      fillContent("222222");
      ByteBuffer buffer = ByteBuffer.allocate(6);
      assertEquals(6, file.read(0, buffer));
      assertBufferEquals("222222", 0, buffer);
    }

    public void testNonEmpty_read_all_multipleBuffers() throws IOException {
      fillContent("223334");
      ByteBuffer buf1 = ByteBuffer.allocate(3);
      ByteBuffer buf2 = ByteBuffer.allocate(3);
      assertEquals(6, file.read(0, ImmutableList.of(buf1, buf2)));
      assertBufferEquals("223", 0, buf1);
      assertBufferEquals("334", 0, buf2);
    }

    public void testNonEmpty_read_all_byteArray_largerThanContent() throws IOException {
      fillContent("222222");
      byte[] array = new byte[10];
      assertEquals(6, file.read(0, array, 0, array.length));
      assertArrayEquals(bytes("2222220000"), array);
      array = new byte[10];
      assertEquals(6, file.read(0, array, 2, 6));
      assertArrayEquals(bytes("0022222200"), array);
    }

    public void testNonEmpty_read_all_singleBuffer_largerThanContent() throws IOException {
      fillContent("222222");
      ByteBuffer buffer = ByteBuffer.allocate(16);
      assertBufferEquals("0000000000000000", 16, buffer);
      assertEquals(6, file.read(0, buffer));
      assertBufferEquals("2222220000000000", 10, buffer);
    }

    public void testNonEmpty_read_all_multipleBuffers_largerThanContent() throws IOException {
      fillContent("222222");
      ByteBuffer buf1 = ByteBuffer.allocate(4);
      ByteBuffer buf2 = ByteBuffer.allocate(8);
      assertEquals(6, file.read(0, ImmutableList.of(buf1, buf2)));
      assertBufferEquals("2222", 0, buf1);
      assertBufferEquals("22000000", 6, buf2);
    }

    public void testNonEmpty_read_all_multipleBuffers_extraBuffers() throws IOException {
      fillContent("222222");
      ByteBuffer buf1 = ByteBuffer.allocate(4);
      ByteBuffer buf2 = ByteBuffer.allocate(8);
      ByteBuffer buf3 = ByteBuffer.allocate(4);
      assertEquals(6, file.read(0, ImmutableList.of(buf1, buf2, buf3)));
      assertBufferEquals("2222", 0, buf1);
      assertBufferEquals("22000000", 6, buf2);
      assertBufferEquals("0000", 4, buf3);
    }

    public void testNonEmpty_read_partial_fromStart_byteArray() throws IOException {
      fillContent("222222");
      byte[] array = new byte[3];
      assertEquals(3, file.read(0, array, 0, array.length));
      assertArrayEquals(bytes("222"), array);
      array = new byte[10];
      assertEquals(3, file.read(0, array, 1, 3));
      assertArrayEquals(bytes("0222000000"), array);
    }

    public void testNonEmpty_read_partial_fromMiddle_byteArray() throws IOException {
      fillContent("22223333");
      byte[] array = new byte[3];
      assertEquals(3, file.read(3, array, 0, array.length));
      assertArrayEquals(bytes("233"), array);
      array = new byte[10];
      assertEquals(3, file.read(3, array, 1, 3));
      assertArrayEquals(bytes("0233000000"), array);
    }

    public void testNonEmpty_read_partial_fromEnd_byteArray() throws IOException {
      fillContent("2222222222");
      byte[] array = new byte[3];
      assertEquals(2, file.read(8, array, 0, array.length));
      assertArrayEquals(bytes("220"), array);
      array = new byte[10];
      assertEquals(2, file.read(8, array, 1, 3));
      assertArrayEquals(bytes("0220000000"), array);
    }

    public void testNonEmpty_read_partial_fromStart_singleBuffer() throws IOException {
      fillContent("222222");
      ByteBuffer buffer = ByteBuffer.allocate(3);
      assertEquals(3, file.read(0, buffer));
      assertBufferEquals("222", 0, buffer);
    }

    public void testNonEmpty_read_partial_fromMiddle_singleBuffer() throws IOException {
      fillContent("22223333");
      ByteBuffer buffer = ByteBuffer.allocate(3);
      assertEquals(3, file.read(3, buffer));
      assertBufferEquals("233", 0, buffer);
    }

    public void testNonEmpty_read_partial_fromEnd_singleBuffer() throws IOException {
      fillContent("2222222222");
      ByteBuffer buffer = ByteBuffer.allocate(3);
      assertEquals(2, file.read(8, buffer));
      assertBufferEquals("220", 1, buffer);
    }

    public void testNonEmpty_read_partial_fromStart_multipleBuffers() throws IOException {
      fillContent("12345678");
      ByteBuffer buf1 = ByteBuffer.allocate(2);
      ByteBuffer buf2 = ByteBuffer.allocate(2);
      assertEquals(4, file.read(0, ImmutableList.of(buf1, buf2)));
      assertBufferEquals("12", 0, buf1);
      assertBufferEquals("34", 0, buf2);
    }

    public void testNonEmpty_read_partial_fromMiddle_multipleBuffers() throws IOException {
      fillContent("12345678");
      ByteBuffer buf1 = ByteBuffer.allocate(2);
      ByteBuffer buf2 = ByteBuffer.allocate(2);
      assertEquals(4, file.read(3, ImmutableList.of(buf1, buf2)));
      assertBufferEquals("45", 0, buf1);
      assertBufferEquals("67", 0, buf2);
    }

    public void testNonEmpty_read_partial_fromEnd_multipleBuffers() throws IOException {
      fillContent("123456789");
      ByteBuffer buf1 = ByteBuffer.allocate(2);
      ByteBuffer buf2 = ByteBuffer.allocate(2);
      assertEquals(3, file.read(6, ImmutableList.of(buf1, buf2)));
      assertBufferEquals("78", 0, buf1);
      assertBufferEquals("90", 1, buf2);
    }

    public void testNonEmpty_read_fromPastEnd_byteArray() throws IOException {
      fillContent("123");
      byte[] array = new byte[3];
      assertEquals(-1, file.read(3, array, 0, array.length));
      assertArrayEquals(bytes("000"), array);
      assertEquals(-1, file.read(3, array, 0, 2));
      assertArrayEquals(bytes("000"), array);
    }

    public void testNonEmpty_read_fromPastEnd_singleBuffer() throws IOException {
      fillContent("123");
      ByteBuffer buffer = ByteBuffer.allocate(3);
      file.read(3, buffer);
      assertBufferEquals("000", 3, buffer);
    }

    public void testNonEmpty_read_fromPastEnd_multipleBuffers() throws IOException {
      fillContent("123");
      ByteBuffer buf1 = ByteBuffer.allocate(2);
      ByteBuffer buf2 = ByteBuffer.allocate(2);
      assertEquals(-1, file.read(6, ImmutableList.of(buf1, buf2)));
      assertBufferEquals("00", 2, buf1);
      assertBufferEquals("00", 2, buf2);
    }

    public void testNonEmpty_write_partial_fromStart_singleByte() throws IOException {
      fillContent("222222");
      assertEquals(1, file.write(0, (byte) 1));
      assertContentEquals("122222", file);
    }

    public void testNonEmpty_write_partial_fromMiddle_singleByte() throws IOException {
      fillContent("222222");
      assertEquals(1, file.write(3, (byte) 1));
      assertContentEquals("222122", file);
    }

    public void testNonEmpty_write_partial_fromEnd_singleByte() throws IOException {
      fillContent("222222");
      assertEquals(1, file.write(6, (byte) 1));
      assertContentEquals("2222221", file);
    }

    public void testNonEmpty_write_partial_fromStart_byteArray() throws IOException {
      fillContent("222222");
      assertEquals(3, file.write(0, bytes("111"), 0, 3));
      assertContentEquals("111222", file);
      assertEquals(2, file.write(0, bytes("333333"), 0, 2));
      assertContentEquals("331222", file);
    }

    public void testNonEmpty_write_partial_fromMiddle_byteArray() throws IOException {
      fillContent("22222222");
      assertEquals(3, file.write(3, buffer("111")));
      assertContentEquals("22211122", file);
      assertEquals(2, file.write(5, bytes("333333"), 1, 2));
      assertContentEquals("22211332", file);
    }

    public void testNonEmpty_write_partial_fromBeforeEnd_byteArray() throws IOException {
      fillContent("22222222");
      assertEquals(3, file.write(6, bytes("111"), 0, 3));
      assertContentEquals("222222111", file);
      assertEquals(2, file.write(8, bytes("333333"), 2, 2));
      assertContentEquals("2222221133", file);
    }

    public void testNonEmpty_write_partial_fromEnd_byteArray() throws IOException {
      fillContent("222222");
      assertEquals(3, file.write(6, bytes("111"), 0, 3));
      assertContentEquals("222222111", file);
      assertEquals(2, file.write(9, bytes("333333"), 3, 2));
      assertContentEquals("22222211133", file);
    }

    public void testNonEmpty_write_partial_fromPastEnd_byteArray() throws IOException {
      fillContent("222222");
      assertEquals(3, file.write(8, bytes("111"), 0, 3));
      assertContentEquals("22222200111", file);
      assertEquals(2, file.write(13, bytes("333333"), 4, 2));
      assertContentEquals("222222001110033", file);
    }

    public void testNonEmpty_write_partial_fromStart_singleBuffer() throws IOException {
      fillContent("222222");
      assertEquals(3, file.write(0, buffer("111")));
      assertContentEquals("111222", file);
    }

    public void testNonEmpty_write_partial_fromMiddle_singleBuffer() throws IOException {
      fillContent("22222222");
      assertEquals(3, file.write(3, buffer("111")));
      assertContentEquals("22211122", file);
    }

    public void testNonEmpty_write_partial_fromBeforeEnd_singleBuffer() throws IOException {
      fillContent("22222222");
      assertEquals(3, file.write(6, buffer("111")));
      assertContentEquals("222222111", file);
    }

    public void testNonEmpty_write_partial_fromEnd_singleBuffer() throws IOException {
      fillContent("222222");
      assertEquals(3, file.write(6, buffer("111")));
      assertContentEquals("222222111", file);
    }

    public void testNonEmpty_write_partial_fromPastEnd_singleBuffer() throws IOException {
      fillContent("222222");
      assertEquals(3, file.write(8, buffer("111")));
      assertContentEquals("22222200111", file);
    }

    public void testNonEmpty_write_partial_fromStart_multipleBuffers() throws IOException {
      fillContent("222222");
      assertEquals(4, file.write(0, buffers("11", "33")));
      assertContentEquals("113322", file);
    }

    public void testNonEmpty_write_partial_fromMiddle_multipleBuffers() throws IOException {
      fillContent("22222222");
      assertEquals(4, file.write(2, buffers("11", "33")));
      assertContentEquals("22113322", file);
    }

    public void testNonEmpty_write_partial_fromBeforeEnd_multipleBuffers() throws IOException {
      fillContent("22222222");
      assertEquals(6, file.write(6, buffers("111", "333")));
      assertContentEquals("222222111333", file);
    }

    public void testNonEmpty_write_partial_fromEnd_multipleBuffers() throws IOException {
      fillContent("222222");
      assertEquals(6, file.write(6, buffers("111", "333")));
      assertContentEquals("222222111333", file);
    }

    public void testNonEmpty_write_partial_fromPastEnd_multipleBuffers() throws IOException {
      fillContent("222222");
      assertEquals(4, file.write(10, buffers("11", "33")));
      assertContentEquals("22222200001133", file);
    }

    public void testNonEmpty_write_overwrite_sameLength() throws IOException {
      fillContent("2222");
      assertEquals(4, file.write(0, buffer("1234")));
      assertContentEquals("1234", file);
    }

    public void testNonEmpty_write_overwrite_greaterLength() throws IOException {
      fillContent("2222");
      assertEquals(8, file.write(0, buffer("12345678")));
      assertContentEquals("12345678", file);
    }

    public void testNonEmpty_transferTo_fromStart_countEqualsSize() throws IOException {
      fillContent("123456");
      ByteBufferChannel channel = new ByteBufferChannel(10);
      assertEquals(6, file.transferTo(0, 6, channel));
      assertBufferEquals("1234560000", 4, channel.buffer());
    }

    public void testNonEmpty_transferTo_fromStart_countLessThanSize() throws IOException {
      fillContent("123456");
      ByteBufferChannel channel = new ByteBufferChannel(10);
      assertEquals(4, file.transferTo(0, 4, channel));
      assertBufferEquals("1234000000", 6, channel.buffer());
    }

    public void testNonEmpty_transferTo_fromMiddle_countEqualsSize() throws IOException {
      fillContent("123456");
      ByteBufferChannel channel = new ByteBufferChannel(10);
      assertEquals(2, file.transferTo(4, 6, channel));
      assertBufferEquals("5600000000", 8, channel.buffer());
    }

    public void testNonEmpty_transferTo_fromMiddle_countLessThanSize() throws IOException {
      fillContent("12345678");
      ByteBufferChannel channel = new ByteBufferChannel(10);
      assertEquals(4, file.transferTo(3, 4, channel));
      assertBufferEquals("4567000000", 6, channel.buffer());
    }

    public void testNonEmpty_transferFrom_toStart_countEqualsSrcSize() throws IOException {
      fillContent("22222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("11111"));
      assertEquals(5, file.transferFrom(channel, 0, 5));
      assertContentEquals("11111222", file);
    }

    public void testNonEmpty_transferFrom_toStart_countLessThanSrcSize() throws IOException {
      fillContent("22222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("11111"));
      assertEquals(3, file.transferFrom(channel, 0, 3));
      assertContentEquals("11122222", file);
    }

    public void testNonEmpty_transferFrom_toStart_countGreaterThanSrcSize() throws IOException {
      fillContent("22222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("11111"));
      assertEquals(5, file.transferFrom(channel, 0, 10));
      assertContentEquals("11111222", file);
    }

    public void testNonEmpty_transferFrom_toMiddle_countEqualsSrcSize() throws IOException {
      fillContent("22222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("1111"));
      assertEquals(4, file.transferFrom(channel, 2, 4));
      assertContentEquals("22111122", file);
    }

    public void testNonEmpty_transferFrom_toMiddle_countLessThanSrcSize() throws IOException {
      fillContent("22222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("11111"));
      assertEquals(3, file.transferFrom(channel, 2, 3));
      assertContentEquals("22111222", file);
    }

    public void testNonEmpty_transferFrom_toMiddle_countGreaterThanSrcSize() throws IOException {
      fillContent("22222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("1111"));
      assertEquals(4, file.transferFrom(channel, 2, 100));
      assertContentEquals("22111122", file);
    }

    public void testNonEmpty_transferFrom_toMiddle_transferGoesBeyondContentSize()
        throws IOException {
      fillContent("222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("111111"));
      assertEquals(6, file.transferFrom(channel, 4, 6));
      assertContentEquals("2222111111", file);
    }

    public void testNonEmpty_transferFrom_toEnd() throws IOException {
      fillContent("222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("111111"));
      assertEquals(6, file.transferFrom(channel, 6, 6));
      assertContentEquals("222222111111", file);
    }

    public void testNonEmpty_transferFrom_toPastEnd() throws IOException {
      fillContent("222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("111111"));
      assertEquals(6, file.transferFrom(channel, 10, 6));
      assertContentEquals("2222220000111111", file);
    }

    public void testNonEmpty_transferFrom_hugeOverestimateCount() throws IOException {
      fillContent("222222");
      ByteBufferChannel channel = new ByteBufferChannel(buffer("111111"));
      assertEquals(6, file.transferFrom(channel, 6, 1024 * 1024 * 10));
      assertContentEquals("222222111111", file);
    }

    public void testNonEmpty_copy() throws IOException {
      fillContent("123456");
      RegularFile copy = file.copyWithoutContent(1);
      file.copyContentTo(copy);
      assertContentEquals("123456", copy);
    }

    public void testNonEmpty_copy_multipleTimes() throws IOException {
      fillContent("123456");
      RegularFile copy = file.copyWithoutContent(1);
      file.copyContentTo(copy);
      RegularFile copy2 = copy.copyWithoutContent(2);
      copy.copyContentTo(copy2);
      assertContentEquals("123456", copy);
    }

    public void testNonEmpty_truncate_toZero() throws IOException {
      fillContent("123456");
      file.truncate(0);
      assertContentEquals("", file);
    }

    public void testNonEmpty_truncate_partial() throws IOException {
      fillContent("12345678");
      file.truncate(5);
      assertContentEquals("12345", file);
    }

    public void testNonEmpty_truncate_sizeUp() throws IOException {
      fillContent("123456");
      file.truncate(12);
      assertContentEquals("123456", file);
    }

    public void testDeletedStoreRemainsUsableWhileOpen() throws IOException {
      byte[] bytes = bytes("1234567890");
      file.write(0, bytes, 0, bytes.length);

      file.opened();
      file.opened();

      file.deleted();

      assertContentEquals(bytes, file);

      byte[] moreBytes = bytes("1234");
      file.write(bytes.length, moreBytes, 0, 4);

      byte[] totalBytes = concat(bytes, bytes("1234"));
      assertContentEquals(totalBytes, file);

      file.closed();

      assertContentEquals(totalBytes, file);

      file.closed();

      // don't check anything else; no guarantee of what if anything will happen once the file is
      // deleted and completely closed
    }

    private static void assertBufferEquals(String expected, ByteBuffer actual) {
      assertEquals(expected.length(), actual.capacity());
      assertArrayEquals(bytes(expected), actual.array());
    }

    private static void assertBufferEquals(String expected, int remaining, ByteBuffer actual) {
      assertBufferEquals(expected, actual);
      assertEquals(remaining, actual.remaining());
    }

    private static void assertContentEquals(String expected, RegularFile actual) {
      assertContentEquals(bytes(expected), actual);
    }

    protected static void assertContentEquals(byte[] expected, RegularFile actual) {
      assertEquals(expected.length, actual.sizeWithoutLocking());
      byte[] actualBytes = new byte[(int) actual.sizeWithoutLocking()];
      actual.read(0, ByteBuffer.wrap(actualBytes));
      assertArrayEquals(expected, actualBytes);
    }
  }
}
