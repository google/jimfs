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

import static com.google.common.jimfs.AbstractWatchService.Key.State.READY;
import static com.google.common.jimfs.AbstractWatchService.Key.State.SIGNALLED;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link AbstractWatchService}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class AbstractWatchServiceTest {

  private AbstractWatchService watcher;

  @Before
  public void setUp() throws IOException {
    watcher = new AbstractWatchService() {};
  }

  @Test
  public void testNewWatcher() throws IOException {
    assertThat(watcher.isOpen()).isTrue();
    assertThat(watcher.poll()).isNull();
    assertThat(watcher.queuedKeys()).isEmpty();
    watcher.close();
    assertThat(watcher.isOpen()).isFalse();
  }

  @Test
  public void testRegister() throws IOException {
    Watchable watchable = new StubWatchable();
    AbstractWatchService.Key key = watcher.register(watchable, ImmutableSet.of(ENTRY_CREATE));
    assertThat(key.isValid()).isTrue();
    assertThat(key.pollEvents()).isEmpty();
    assertThat(key.subscribesTo(ENTRY_CREATE)).isTrue();
    assertThat(key.subscribesTo(ENTRY_DELETE)).isFalse();
    assertThat(key.watchable()).isEqualTo(watchable);
    assertThat(key.state()).isEqualTo(READY);
  }

  @Test
  public void testPostEvent() throws IOException {
    AbstractWatchService.Key key =
        watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));

    AbstractWatchService.Event<Path> event =
        new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null);
    key.post(event);
    key.signal();

    assertThat(watcher.queuedKeys()).containsExactly(key);

    WatchKey retrievedKey = watcher.poll();
    assertThat(retrievedKey).isEqualTo(key);

    List<WatchEvent<?>> events = retrievedKey.pollEvents();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isEqualTo(event);

    // polling should have removed all events
    assertThat(retrievedKey.pollEvents()).isEmpty();
  }

  @Test
  public void testKeyStates() throws IOException {
    AbstractWatchService.Key key =
        watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));

    AbstractWatchService.Event<Path> event =
        new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null);
    assertThat(key.state()).isEqualTo(READY);
    key.post(event);
    key.signal();
    assertThat(key.state()).isEqualTo(SIGNALLED);

    AbstractWatchService.Event<Path> event2 =
        new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null);
    key.post(event2);
    assertThat(key.state()).isEqualTo(SIGNALLED);

    // key was not queued twice
    assertThat(watcher.queuedKeys()).containsExactly(key);
    assertThat(watcher.poll().pollEvents()).containsExactly(event, event2);

    assertThat(watcher.poll()).isNull();

    key.post(event);

    // still not added to queue; already signalled
    assertThat(watcher.poll()).isNull();
    assertThat(key.pollEvents()).containsExactly(event);

    key.reset();
    assertThat(key.state()).isEqualTo(READY);

    key.post(event2);
    key.signal();

    // now that it's reset it can be requeued
    assertThat(watcher.poll()).isEqualTo(key);
  }

  @Test
  public void testKeyRequeuedOnResetIfEventsArePending() throws IOException {
    AbstractWatchService.Key key =
        watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));
    key.post(new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null));
    key.signal();

    key = (AbstractWatchService.Key) watcher.poll();
    assertThat(watcher.queuedKeys()).isEmpty();

    assertThat(key.pollEvents()).hasSize(1);

    key.post(new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null));
    assertThat(watcher.queuedKeys()).isEmpty();

    key.reset();
    assertThat(key.state()).isEqualTo(SIGNALLED);
    assertThat(watcher.queuedKeys()).hasSize(1);
  }

  @Test
  public void testOverflow() throws IOException {
    AbstractWatchService.Key key =
        watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));
    for (int i = 0; i < AbstractWatchService.Key.MAX_QUEUE_SIZE + 10; i++) {
      key.post(new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null));
    }
    key.signal();

    List<WatchEvent<?>> events = key.pollEvents();

    assertThat(events).hasSize(AbstractWatchService.Key.MAX_QUEUE_SIZE + 1);
    for (int i = 0; i < AbstractWatchService.Key.MAX_QUEUE_SIZE; i++) {
      assertThat(events.get(i).kind()).isEqualTo(ENTRY_CREATE);
    }

    WatchEvent<?> lastEvent = events.get(AbstractWatchService.Key.MAX_QUEUE_SIZE);
    assertThat(lastEvent.kind()).isEqualTo(OVERFLOW);
    assertThat(lastEvent.count()).isEqualTo(10);
  }

  @Test
  public void testResetAfterCancelReturnsFalse() throws IOException {
    AbstractWatchService.Key key =
        watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));
    key.signal();
    key.cancel();
    assertThat(key.reset()).isFalse();
  }

  @Test
  public void testClosedWatcher() throws IOException, InterruptedException {
    AbstractWatchService.Key key1 =
        watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));
    AbstractWatchService.Key key2 =
        watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_MODIFY));

    assertThat(key1.isValid()).isTrue();
    assertThat(key2.isValid()).isTrue();

    watcher.close();

    assertThat(key1.isValid()).isFalse();
    assertThat(key2.isValid()).isFalse();
    assertThat(key1.reset()).isFalse();
    assertThat(key2.reset()).isFalse();

    try {
      watcher.poll();
      fail();
    } catch (ClosedWatchServiceException expected) {
    }

    try {
      watcher.poll(10, SECONDS);
      fail();
    } catch (ClosedWatchServiceException expected) {
    }

    try {
      watcher.take();
      fail();
    } catch (ClosedWatchServiceException expected) {
    }

    try {
      watcher.register(new StubWatchable(), ImmutableList.<WatchEvent.Kind<?>>of());
      fail();
    } catch (ClosedWatchServiceException expected) {
    }
  }

  // TODO(cgdecker): Test concurrent use of Watcher

  /**
   * A fake {@link Watchable} for testing.
   */
  private static final class StubWatchable implements Watchable {

    @Override
    public WatchKey register(
        WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
        throws IOException {
      return register(watcher, events);
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events)
        throws IOException {
      return ((AbstractWatchService) watcher).register(this, Arrays.asList(events));
    }
  }
}
