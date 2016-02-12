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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.AbstractWatchService.Event;
import com.google.common.jimfs.AbstractWatchService.Key;
import com.google.common.util.concurrent.Runnables;
import com.google.common.util.concurrent.Uninterruptibles;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
@RunWith(JUnit4.class)
public class PollingWatchServiceTest {

  private JimfsFileSystem fs;
  private PollingWatchService watcher;

  @Before
  public void setUp() {
    fs = (JimfsFileSystem) Jimfs.newFileSystem(Configuration.unix());
    watcher =
        new PollingWatchService(
            fs.getDefaultView(),
            fs.getPathService(),
            new FileSystemState(Runnables.doNothing()),
            4, MILLISECONDS);
  }

  @After
  public void tearDown() throws IOException {
    watcher.close();
    fs.close();
    watcher = null;
    fs = null;
  }

  @Test
  public void testNewWatcher() {
    assertThat(watcher.isOpen()).isTrue();
    assertThat(watcher.isPolling()).isFalse();
  }

  @Test
  public void testRegister() throws IOException {
    Key key = watcher.register(createDirectory(), ImmutableList.of(ENTRY_CREATE));
    assertThat(key.isValid()).isTrue();

    assertThat(watcher.isPolling()).isTrue();
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
    assertThat(key.isValid()).isFalse();

    assertThat(watcher.isPolling()).isFalse();

    Key key2 = watcher.register(createDirectory(), ImmutableList.of(ENTRY_CREATE));
    Key key3 = watcher.register(createDirectory(), ImmutableList.of(ENTRY_DELETE));

    assertThat(watcher.isPolling()).isTrue();

    key2.cancel();

    assertThat(watcher.isPolling()).isTrue();

    key3.cancel();

    assertThat(watcher.isPolling()).isFalse();
  }

  @Test
  public void testCloseCancelsAllKeysAndStopsPolling() throws IOException {
    Key key1 = watcher.register(createDirectory(), ImmutableList.of(ENTRY_CREATE));
    Key key2 = watcher.register(createDirectory(), ImmutableList.of(ENTRY_DELETE));

    assertThat(key1.isValid()).isTrue();
    assertThat(key2.isValid()).isTrue();
    assertThat(watcher.isPolling()).isTrue();

    watcher.close();

    assertThat(key1.isValid()).isFalse();
    assertThat(key2.isValid()).isFalse();
    assertThat(watcher.isPolling()).isFalse();
  }

  @Test(timeout = 2000)
  public void testWatchForOneEventType() throws IOException, InterruptedException {
    JimfsPath path = createDirectory();
    watcher.register(path, ImmutableList.of(ENTRY_CREATE));

    Files.createFile(path.resolve("foo"));

    assertWatcherHasEvents(new Event<>(ENTRY_CREATE, 1, fs.getPath("foo")));

    Files.createFile(path.resolve("bar"));
    Files.createFile(path.resolve("baz"));

    assertWatcherHasEvents(
        new Event<>(ENTRY_CREATE, 1, fs.getPath("bar")),
        new Event<>(ENTRY_CREATE, 1, fs.getPath("baz")));
  }

  @Test(timeout = 2000)
  public void testWatchForMultipleEventTypes() throws IOException, InterruptedException {
    JimfsPath path = createDirectory();
    watcher.register(path, ImmutableList.of(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY));

    Files.createDirectory(path.resolve("foo"));
    Files.createFile(path.resolve("bar"));

    assertWatcherHasEvents(
        new Event<>(ENTRY_CREATE, 1, fs.getPath("bar")),
        new Event<>(ENTRY_CREATE, 1, fs.getPath("foo")));

    Files.createFile(path.resolve("baz"));
    Files.delete(path.resolve("bar"));
    Files.createFile(path.resolve("foo/bar"));

    assertWatcherHasEvents(
        new Event<>(ENTRY_CREATE, 1, fs.getPath("baz")),
        new Event<>(ENTRY_DELETE, 1, fs.getPath("bar")),
        new Event<>(ENTRY_MODIFY, 1, fs.getPath("foo")));

    Files.delete(path.resolve("foo/bar"));
    ensureTimeToPoll(); // watcher polls, seeing modification, then polls again, seeing delete
    Files.delete(path.resolve("foo"));

    assertWatcherHasEvents(
        new Event<>(ENTRY_MODIFY, 1, fs.getPath("foo")),
        new Event<>(ENTRY_DELETE, 1, fs.getPath("foo")));

    Files.createDirectories(path.resolve("foo/bar"));

    // polling here may either see just the creation of foo, or may first see the creation of foo
    // and then the creation of foo/bar (modification of foo) since those don't happen atomically
    assertWatcherHasEvents(
        ImmutableList.<WatchEvent<?>>of(new Event<>(ENTRY_CREATE, 1, fs.getPath("foo"))),
        // or
        ImmutableList.<WatchEvent<?>>of(
            new Event<>(ENTRY_CREATE, 1, fs.getPath("foo")),
            new Event<>(ENTRY_MODIFY, 1, fs.getPath("foo"))));

    Files.delete(path.resolve("foo/bar"));
    Files.delete(path.resolve("foo"));

    // polling here may either just see the deletion of foo, or may first see the deletion of bar
    // (modification of foo) and then the deletion of foo
    assertWatcherHasEvents(
        ImmutableList.<WatchEvent<?>>of(new Event<>(ENTRY_DELETE, 1, fs.getPath("foo"))),
        // or
        ImmutableList.<WatchEvent<?>>of(
            new Event<>(ENTRY_MODIFY, 1, fs.getPath("foo")),
            new Event<>(ENTRY_DELETE, 1, fs.getPath("foo"))));
  }

  private void assertWatcherHasEvents(WatchEvent<?>... events) throws InterruptedException {
    assertWatcherHasEvents(Arrays.asList(events), ImmutableList.<WatchEvent<?>>of());
  }

  private void assertWatcherHasEvents(List<WatchEvent<?>> expected, List<WatchEvent<?>> alternate)
      throws InterruptedException {
    ensureTimeToPoll(); // otherwise we could read 1 event but not all the events we're expecting
    WatchKey key = watcher.take();
    List<WatchEvent<?>> keyEvents = key.pollEvents();

    if (keyEvents.size() == expected.size() || alternate.isEmpty()) {
      assertThat(keyEvents).containsExactlyElementsIn(expected);
    } else {
      assertThat(keyEvents).containsExactlyElementsIn(alternate);
    }
    key.reset();
  }

  private static void ensureTimeToPoll() {
    Uninterruptibles.sleepUninterruptibly(40, MILLISECONDS);
  }

  private JimfsPath createDirectory() throws IOException {
    JimfsPath path = fs.getPath("/" + UUID.randomUUID().toString());
    Files.createDirectory(path);
    return path;
  }
}
