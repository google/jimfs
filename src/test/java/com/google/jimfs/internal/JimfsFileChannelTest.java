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

import static com.google.jimfs.testing.TestUtils.buffer;
import static com.google.jimfs.testing.TestUtils.bytes;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import com.google.jimfs.testing.ByteBufferChannel;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.OpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Most of the behavior of {@link JimfsFileChannel} is handled by the {@link ByteStore}
 * implementations, so the thorough tests of that are in {@link AbstractByteStoreTest}. This mostly
 * tests interactions with the store and channel positions.
 *
 * @author Colin Decker
 */
public class JimfsFileChannelTest {

  private static FileChannel channel(ByteStore store, OpenOption... options)
      throws IOException {
    ImmutableSet<OpenOption> opts = ImmutableSet.copyOf(options);
    return new JimfsFileChannel(new File(-1, store), opts);
  }

  private static StubByteStore store(int size) throws IOException {
    return new StubByteStore(size);
  }

  @Test
  public void testPosition() throws IOException {
    FileChannel channel = channel(store(10), READ);
    assertEquals(0, channel.position());
    assertSame(channel, channel.position(100));
    assertEquals(100, channel.position());
  }

  @Test
  public void testSize() throws IOException {
    StubByteStore store = store(10);
    FileChannel channel = channel(store, READ);

    assertEquals(10, channel.size());

    store.setSize(100);
    assertEquals(100, channel.size());
  }

  @Test
  public void testRead() throws IOException {
    StubByteStore store = store(20);
    FileChannel channel = channel(store, READ);
    assertEquals(0, channel.position());

    ByteBuffer buf = buffer("1234567890");
    ByteBuffer buf2 = buffer("123457890");
    assertEquals(10, channel.read(buf));
    assertEquals(10, channel.position());

    buf.flip();
    assertEquals(10, channel.read(new ByteBuffer[]{buf, buf2}));
    assertEquals(20, channel.position());

    buf.flip();
    buf2.flip();
    store.setSize(30);
    assertEquals(10, channel.read(new ByteBuffer[]{buf, buf2}, 0, 2));
    assertEquals(30, channel.position());

    buf.flip();
    assertEquals(10, channel.read(buf, 5));
    assertEquals(30, channel.position());

    buf.flip();
    assertEquals(-1, channel.read(buf));
    assertEquals(30, channel.position());
  }

  @Test
  public void testWrite() throws IOException {
    ByteStore store = store(0);
    FileChannel channel = channel(store, WRITE);
    assertEquals(0, channel.position());

    ByteBuffer buf = buffer("1234567890");
    ByteBuffer buf2 = buffer("1234567890");
    assertEquals(10, channel.write(buf));
    assertEquals(10, channel.position());

    buf.flip();
    assertEquals(20, channel.write(new ByteBuffer[]{buf, buf2}));
    assertEquals(30, channel.position());

    buf.flip();
    buf2.flip();
    assertEquals(20, channel.write(new ByteBuffer[]{buf, buf2}, 0, 2));
    assertEquals(50, channel.position());

    buf.flip();
    assertEquals(10, channel.write(buf, 5));
    assertEquals(50, channel.position());
  }

  @Test
  public void testAppend() throws IOException {
    ByteStore store = store(0);
    FileChannel channel = channel(store, WRITE, APPEND);
    assertEquals(0, channel.position());

    ByteBuffer buf = buffer("1234567890");
    ByteBuffer buf2 = buffer("1234567890");

    assertEquals(10, channel.write(buf));
    assertEquals(10, channel.position());

    buf.flip();
    channel.position(0);
    assertEquals(20, channel.write(new ByteBuffer[]{buf, buf2}));
    assertEquals(30, channel.position());

    buf.flip();
    buf2.flip();
    channel.position(0);
    assertEquals(20, channel.write(new ByteBuffer[]{buf, buf2}, 0, 2));
    assertEquals(50, channel.position());

    buf.flip();
    channel.position(0);
    assertEquals(10, channel.write(buf, 5));
    assertEquals(60, channel.position());

    buf.flip();
    channel.position(0);
    assertEquals(10, channel.transferFrom(new ByteBufferChannel(buf), 0, 10));
    assertEquals(70, channel.position());
  }

  @Test
  public void testTransferTo() throws IOException {
    ByteStore store = store(10);
    FileChannel channel = channel(store, READ);

    ByteBufferChannel writeChannel = new ByteBufferChannel(buffer("1234567890"));
    assertEquals(10, channel.transferTo(0, 100, writeChannel));
    assertEquals(0, channel.position());
  }

  @Test
  public void testTransferFrom() throws IOException {
    ByteStore store = store(0);
    FileChannel channel = channel(store, WRITE);

    ByteBufferChannel readChannel = new ByteBufferChannel(buffer("1234567890"));
    assertEquals(10, channel.transferFrom(readChannel, 0, 100));
    assertEquals(0, channel.position());
  }

  @Test
  public void testTruncate() throws IOException {
    ByteStore store = store(10);
    FileChannel channel = channel(store, WRITE);

    channel.truncate(10); // no resize, >= size
    assertEquals(10, store.sizeInBytes());
    channel.truncate(11); // no resize, > size
    assertEquals(10, store.sizeInBytes());
    channel.truncate(5); // resize down to 5
    assertEquals(5, store.sizeInBytes());

    channel.position(20);
    channel.truncate(10);
    assertEquals(10, channel.position());
    channel.truncate(2);
    assertEquals(2, channel.position());
  }

  @Test
  public void testClose() throws IOException {
    FileChannel channel = channel(store(0), READ, WRITE);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    assertTrue(channel.isOpen());
    channel.close();
    assertFalse(channel.isOpen());

    try {
      channel.position();
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.position(0);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.lock();
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.lock(0, 10, true);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.tryLock();
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.tryLock(0, 10, true);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.force(true);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.write(buffer("111"));
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.write(buffer("111"), 10);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.write(new ByteBuffer[]{buffer("111"), buffer("111")});
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.write(new ByteBuffer[]{buffer("111"), buffer("111")}, 0, 2);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.transferFrom(new ByteBufferChannel(bytes("1111")), 0, 4);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.truncate(0);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.read(buffer("111"));
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.read(buffer("111"), 10);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.read(new ByteBuffer[]{buffer("111"), buffer("111")});
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.read(new ByteBuffer[]{buffer("111"), buffer("111")}, 0, 2);
      fail();
    } catch (ClosedChannelException expected) {
    }

    try {
      channel.transferTo(0, 10, new ByteBufferChannel(buffer("111")));
      fail();
    } catch (ClosedChannelException expected) {
    }

    executor.shutdown();
  }

  @Test
  public void testWritesInReadOnlyMode() throws IOException {
    FileChannel channel = channel(store(0), READ);

    try {
      channel.write(buffer("111"));
      fail();
    } catch (NonWritableChannelException expected) {
    }

    try {
      channel.write(buffer("111"), 10);
      fail();
    } catch (NonWritableChannelException expected) {
    }

    try {
      channel.write(new ByteBuffer[]{buffer("111"), buffer("111")});
      fail();
    } catch (NonWritableChannelException expected) {
    }

    try {
      channel.write(new ByteBuffer[]{buffer("111"), buffer("111")}, 0, 2);
      fail();
    } catch (NonWritableChannelException expected) {
    }

    try {
      channel.transferFrom(new ByteBufferChannel(bytes("1111")), 0, 4);
      fail();
    } catch (NonWritableChannelException expected) {
    }

    try {
      channel.truncate(0);
      fail();
    } catch (NonWritableChannelException expected) {
    }

    try {
      channel.lock(0, 10, false);
    } catch (NonWritableChannelException expected) {
    }
  }

  @Test
  public void testReadsInWriteOnlyMode() throws IOException {
    FileChannel channel = channel(store(0), WRITE);

    try {
      channel.read(buffer("111"));
      fail();
    } catch (NonReadableChannelException expected) {
    }

    try {
      channel.read(buffer("111"), 10);
      fail();
    } catch (NonReadableChannelException expected) {
    }

    try {
      channel.read(new ByteBuffer[]{buffer("111"), buffer("111")});
      fail();
    } catch (NonReadableChannelException expected) {
    }

    try {
      channel.read(new ByteBuffer[]{buffer("111"), buffer("111")}, 0, 2);
      fail();
    } catch (NonReadableChannelException expected) {
    }

    try {
      channel.transferTo(0, 10, new ByteBufferChannel(buffer("111")));
      fail();
    } catch (NonReadableChannelException expected) {
    }

    try {
      channel.lock(0, 10, true);
    } catch (NonReadableChannelException expected) {
    }
  }

  @Test
  public void testPositionNegative() throws IOException {
    FileChannel channel = channel(store(0), READ, WRITE);

    try {
      channel.position(-1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testTruncateNegative() throws IOException {
    FileChannel channel = channel(store(0), READ, WRITE);

    try {
      channel.truncate(-1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testWriteNegative() throws IOException {
    FileChannel channel = channel(store(0), READ, WRITE);

    try {
      channel.write(buffer("111"), -1);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ByteBuffer[] bufs = {buffer("111"), buffer("111")};
    try {
      channel.write(bufs, -1, 10);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }

    try {
      channel.write(bufs, 0, -1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  @Test
  public void testReadNegative() throws IOException {
    FileChannel channel = channel(store(0), READ, WRITE);

    try {
      channel.read(buffer("111"), -1);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ByteBuffer[] bufs = {buffer("111"), buffer("111")};
    try {
      channel.read(bufs, -1, 10);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }

    try {
      channel.read(bufs, 0, -1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  @Test
  public void testTransferToNegative() throws IOException {
    FileChannel channel = channel(store(0), READ, WRITE);

    try {
      channel.transferTo(-1, 0, new ByteBufferChannel(10));
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      channel.transferTo(0, -1, new ByteBufferChannel(10));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testTransferFromNegative() throws IOException {
    FileChannel channel = channel(store(0), READ, WRITE);

    try {
      channel.transferFrom(new ByteBufferChannel(10), -1, 0);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      channel.transferFrom(new ByteBufferChannel(10), 0, -1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testLockNegative() throws IOException {
    FileChannel channel = channel(store(0), READ, WRITE);

    try {
      channel.lock(-1, 10, true);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      channel.lock(0, -1, true);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      channel.tryLock(-1, 10, true);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      channel.tryLock(0, -1, true);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testNullPointerExceptions() throws IOException {
    FileChannel channel = channel(store(100), READ, WRITE);

    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicInstanceMethods(channel);
  }

  @Test
  public void testLock() throws IOException {
    FileChannel channel = channel(store(10), READ, WRITE);

    assertNotNull(channel.lock());
    assertNotNull(channel.lock(0, 10, false));
    assertNotNull(channel.lock(0, 10, true));

    assertNotNull(channel.tryLock());
    assertNotNull(channel.tryLock(0, 10, false));
    assertNotNull(channel.tryLock(0, 10, true));

    FileLock lock = channel.lock();
    assertTrue(lock.isValid());
    lock.release();
    assertFalse(lock.isValid());
  }
}
