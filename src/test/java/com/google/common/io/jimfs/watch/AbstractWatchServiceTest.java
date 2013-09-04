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

package com.google.common.io.jimfs.watch;

import static com.google.common.io.jimfs.watch.Key.State.READY;
import static com.google.common.io.jimfs.watch.Key.State.SIGNALLED;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;

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
public class AbstractWatchServiceTest {

  private AbstractWatchService watcher;

  @Before
  public void setUp() throws IOException {
    watcher = new AbstractWatchService() {};
  }

  @Test
  public void testNewWatcher() throws IOException {
    ASSERT.that(watcher.isOpen()).isTrue();
    ASSERT.that(watcher.poll()).isNull();
    ASSERT.that(watcher.queuedKeys()).isEmpty();
    watcher.close();
    ASSERT.that(watcher.isOpen()).isFalse();
  }

  @Test
  public void testRegister() throws IOException {
    Watchable watchable = new StubWatchable();
    Key key = watcher.register(watchable, ImmutableSet.of(ENTRY_CREATE));
    ASSERT.that(key.isValid()).isTrue();
    ASSERT.that(key.pollEvents()).isEmpty();
    ASSERT.that(key.subscribesTo(ENTRY_CREATE)).isTrue();
    ASSERT.that(key.subscribesTo(ENTRY_DELETE)).isFalse();
    ASSERT.that(key.watchable()).is(watchable);
    ASSERT.that(key.state()).is(READY);
  }

  @Test
  public void testPostEvent() throws IOException {
    Key key = watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));

    Event<Path> event = new Event<>(ENTRY_CREATE, 1, null);
    key.post(event);
    key.signal();

    ASSERT.that(watcher.queuedKeys()).has().exactly(key);

    WatchKey retrievedKey = watcher.poll();
    ASSERT.that(retrievedKey).is(key);

    List<WatchEvent<?>> events = retrievedKey.pollEvents();
    ASSERT.that(events.size()).is(1);
    ASSERT.that(events.get(0)).is(event);

    // polling should have removed all events
    ASSERT.that(retrievedKey.pollEvents()).isEmpty();
  }

  @Test
  public void testKeyStates() throws IOException {
    Key key = watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));

    Event<Path> event = new Event<>(ENTRY_CREATE, 1, null);
    ASSERT.that(key.state()).is(READY);
    key.post(event);
    key.signal();
    ASSERT.that(key.state()).is(SIGNALLED);

    Event<Path> event2 = new Event<>(ENTRY_CREATE, 1, null);
    key.post(event2);
    ASSERT.that(key.state()).is(SIGNALLED);

    // key was not queued twice
    ASSERT.that(watcher.queuedKeys()).has().exactly(key);
    ASSERT.that(watcher.poll().pollEvents()).has().exactly(event, event2);

    ASSERT.that(watcher.poll()).isNull();

    key.post(event);

    // still not added to queue; already signalled
    ASSERT.that(watcher.poll()).isNull();
    ASSERT.that(key.pollEvents()).has().exactly(event);

    key.reset();
    ASSERT.that(key.state()).is(READY);

    key.post(event2);
    key.signal();

    // now that it's reset it can be requeued
    ASSERT.that(watcher.poll()).is(key);
  }

  @Test
  public void testKeyRequeuedOnResetIfEventsArePending() throws IOException {
    Key key = watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));
    key.post(new Event<>(ENTRY_CREATE, 1, null));
    key.signal();

    key = (Key) watcher.poll();
    ASSERT.that(watcher.queuedKeys()).isEmpty();

    ASSERT.that(key.pollEvents().size()).is(1);

    key.post(new Event<>(ENTRY_CREATE, 1, null));
    ASSERT.that(watcher.queuedKeys()).isEmpty();

    key.reset();
    ASSERT.that(key.state()).is(SIGNALLED);
    ASSERT.that(watcher.queuedKeys().size()).is(1);
  }

  @Test
  public void testOverflow() throws IOException {
    Key key = watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));
    for (int i = 0; i < Key.MAX_QUEUE_SIZE + 10; i++) {
      key.post(new Event<>(ENTRY_CREATE, 1, null));
    }
    key.signal();

    List<WatchEvent<?>> events = key.pollEvents();

    ASSERT.that(events.size()).is(Key.MAX_QUEUE_SIZE + 1);
    for (int i = 0; i < Key.MAX_QUEUE_SIZE; i++) {
      ASSERT.that(events.get(i).kind()).is(ENTRY_CREATE);
    }

    WatchEvent<?> lastEvent = events.get(Key.MAX_QUEUE_SIZE);
    ASSERT.that(lastEvent.kind()).is(OVERFLOW);
    ASSERT.that(lastEvent.count()).is(10);
  }

  @Test
  public void testResetAfterCancelReturnsFalse() throws IOException {
    Key key = watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));
    key.signal();
    key.cancel();
    ASSERT.that(key.reset()).isFalse();
  }

  @Test
  public void testClosedWatcher() throws IOException, InterruptedException {
    Key key1 = watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_CREATE));
    Key key2 = watcher.register(new StubWatchable(), ImmutableSet.of(ENTRY_MODIFY));

    ASSERT.that(key1.isValid()).isTrue();
    ASSERT.that(key2.isValid()).isTrue();

    watcher.close();

    ASSERT.that(key1.isValid()).isFalse();
    ASSERT.that(key2.isValid()).isFalse();
    ASSERT.that(key1.reset()).isFalse();
    ASSERT.that(key2.reset()).isFalse();

    try {
      watcher.poll();
      fail();
    } catch (ClosedWatchServiceException expected) {}

    try {
      watcher.poll(10, SECONDS);
      fail();
    } catch (ClosedWatchServiceException expected) {}

    try {
      watcher.take();
      fail();
    } catch (ClosedWatchServiceException expected) {}

    try {
      watcher.register(new StubWatchable(), ImmutableList.<WatchEvent.Kind<?>>of());
      fail();
    } catch (ClosedWatchServiceException expected) {}
  }

  // TODO(cgdecker): Test concurrent use of Watcher

  /**
   * A fake {@link Watchable} for testing.
   */
  private static final class StubWatchable implements Watchable {

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
        WatchEvent.Modifier... modifiers) throws IOException {
      return register(watcher, events);
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events)
        throws IOException {
      return ((AbstractWatchService) watcher).register(this, Arrays.asList(events));
    }
  }
}
