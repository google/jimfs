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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.jimfs.Jimfs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Tests for {@link PollingWatchService}.
 *
 * @author Colin Decker
 */
public class PollingWatchServiceTest {

  private JimfsFileSystem fs;
  private PollingWatchService watcher;

  @Before
  public void setUp() {
    fs = (JimfsFileSystem) Jimfs.newUnixLikeFileSystem();
    watcher = new PollingWatchService(fs, 4, MILLISECONDS);
  }

  @After
  public void tearDown() {
    watcher.close();
  }

  @Test
  public void testNewWatcher() {
    ASSERT.that(watcher.isOpen()).isTrue();
    ASSERT.that(watcher.isPolling()).isFalse();
  }

  @Test
  public void testRegister() throws IOException {
    Key key = watcher.register(createDirectory(), ImmutableList.of(ENTRY_CREATE));
    ASSERT.that(key.isValid()).isTrue();

    ASSERT.that(watcher.isPolling()).isTrue();
  }

  @Test
  public void testRegister_fileDoesNotExist() throws IOException {
    try {
      watcher.register(fs.getPath("/a/b/c"), ImmutableList.of(ENTRY_CREATE));
      fail();
    } catch (NoSuchFileException expected) {
    }
  }

  @Test
  public void testRegister_fileIsNotDirectory() throws IOException {
    Path path = fs.getPath("/a.txt");
    Files.createFile(path);
    try {
      watcher.register(path, ImmutableList.of(ENTRY_CREATE));
      fail();
    } catch (NotDirectoryException expected) {
    }
  }

  @Test
  public void testCancellingLastKeyStopsPolling() throws IOException {
    Key key = watcher.register(createDirectory(), ImmutableList.of(ENTRY_CREATE));
    key.cancel();
    ASSERT.that(key.isValid()).isFalse();

    ASSERT.that(watcher.isPolling()).isFalse();

    Key key2 = watcher.register(createDirectory(), ImmutableList.of(ENTRY_CREATE));
    Key key3 = watcher.register(createDirectory(), ImmutableList.of(ENTRY_DELETE));

    ASSERT.that(watcher.isPolling()).isTrue();

    key2.cancel();

    ASSERT.that(watcher.isPolling()).isTrue();

    key3.cancel();

    ASSERT.that(watcher.isPolling()).isFalse();
  }

  @Test
  public void testCloseCancelsAllKeysAndStopsPolling() throws IOException {
    Key key1 = watcher.register(createDirectory(), ImmutableList.of(ENTRY_CREATE));
    Key key2 = watcher.register(createDirectory(), ImmutableList.of(ENTRY_DELETE));

    ASSERT.that(key1.isValid());
    ASSERT.that(key2.isValid());
    ASSERT.that(watcher.isPolling()).isTrue();

    watcher.close();

    ASSERT.that(key1.isValid()).isFalse();
    ASSERT.that(key2.isValid()).isFalse();
    ASSERT.that(watcher.isPolling()).isFalse();
  }

  @Test(timeout = 100)
  public void testWatchForOneEventType() throws IOException, InterruptedException {
    JimfsPath path = createDirectory();
    watcher.register(path, ImmutableList.of(ENTRY_CREATE));

    Files.createFile(path.resolve("foo"));

    assertWatcherHasEvents(watcher, new Event<>(ENTRY_CREATE, 1, fs.getPath("foo")));

    Files.createFile(path.resolve("bar"));
    Files.createFile(path.resolve("baz"));

    assertWatcherHasEvents(watcher,
        new Event<>(ENTRY_CREATE, 1, fs.getPath("bar")),
        new Event<>(ENTRY_CREATE, 1, fs.getPath("baz")));
  }

  @Test(timeout = 100)
  public void testWatchForMultipleEventTypes() throws IOException, InterruptedException {
    JimfsPath path = createDirectory();
    watcher.register(path, ImmutableList.of(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY));

    Files.createDirectory(path.resolve("foo"));
    Files.createFile(path.resolve("bar"));

    assertWatcherHasEvents(watcher,
        new Event<>(ENTRY_CREATE, 1, fs.getPath("foo")),
        new Event<>(ENTRY_CREATE, 1, fs.getPath("bar")));

    Files.createFile(path.resolve("baz"));
    Files.delete(path.resolve("bar"));
    Files.createFile(path.resolve("foo/bar"));

    assertWatcherHasEvents(watcher,
        new Event<>(ENTRY_CREATE, 1, fs.getPath("baz")),
        new Event<>(ENTRY_DELETE, 1, fs.getPath("bar")),
        new Event<>(ENTRY_MODIFY, 1, fs.getPath("foo")));

    Files.delete(path.resolve("foo/bar"));
    ensureTimeToPoll(); // watcher polls, seeing modification, then polls again, seeing delete
    Files.delete(path.resolve("foo"));

    assertWatcherHasEvents(watcher,
        new Event<>(ENTRY_MODIFY, 1, fs.getPath("foo")),
        new Event<>(ENTRY_DELETE, 1, fs.getPath("foo")));

    Files.createDirectories(path.resolve("foo/bar"));

    assertWatcherHasEvents(watcher, new Event<>(ENTRY_CREATE, 1, fs.getPath("foo")));

    Files.delete(path.resolve("foo/bar"));
    Files.delete(path.resolve("foo"));

    // foo should be deleted before polling detects its modification
    // this could be flaky; may need to increase time between polling if so (or just not test it)
    assertWatcherHasEvents(watcher, new Event<>(ENTRY_DELETE, 1, fs.getPath("foo")));
  }

  private static void assertWatcherHasEvents(
      PollingWatchService watcher, Event<?>... events) throws InterruptedException {
    ensureTimeToPoll(); // otherwise we could read 1 event but not all the events we're expecting
    WatchKey key = watcher.take();
    List<WatchEvent<?>> keyEvents = key.pollEvents();
    ASSERT.that(keyEvents).has().exactlyAs(Arrays.<WatchEvent<?>>asList(events));
    key.reset();
  }

  private static void ensureTimeToPoll() {
    Uninterruptibles.sleepUninterruptibly(8, MILLISECONDS);
  }

  private JimfsPath createDirectory() throws IOException {
    JimfsPath path = fs.getPath("/" + UUID.randomUUID().toString());
    Files.createDirectory(path);
    return path;
  }
}
