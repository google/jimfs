/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * Implementation of {@link WatchKey} linked to a {@link AbstractWatchService}.
 */
public final class Key implements WatchKey {

  @VisibleForTesting
  static final int MAX_QUEUE_SIZE = 256;

  private static WatchEvent<Object> overflowEvent(int count) {
    return new Event<>(OVERFLOW, count, null);
  }

  private final AbstractWatchService watcher;
  private final Watchable watchable;
  private final ImmutableSet<WatchEvent.Kind<?>> subscribedTypes;

  private final AtomicReference<State> state = new AtomicReference<>(State.READY);
  private final AtomicBoolean valid = new AtomicBoolean(true);
  private final AtomicInteger overflow = new AtomicInteger();

  private final BlockingQueue<WatchEvent<?>> events = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);

  public Key(AbstractWatchService watcher, @Nullable Watchable watchable,
      Iterable<? extends WatchEvent.Kind<?>> subscribedTypes) {
    this.watcher = checkNotNull(watcher);
    this.watchable = watchable; // nullable for Watcher poison
    this.subscribedTypes = ImmutableSet.copyOf(subscribedTypes);
  }

  /**
   * Gets the current state of this key, READY or SIGNALLED.
   */
  @VisibleForTesting
  State state() {
    return state.get();
  }

  /**
   * Gets whether or not this key is subscribed to the given type of event.
   */
  public boolean subscribesTo(WatchEvent.Kind<?> eventType) {
    return subscribedTypes.contains(eventType);
  }

  /**
   * Posts the given event to this key. After posting one or more events, {@link #signal()} must be
   * called to cause the key to be enqueued with the watch service.
   */
  void post(WatchEvent<?> event) {
    if (!events.offer(event)) {
      overflow.incrementAndGet();
    }
  }

  /**
   * Sets the state to SIGNALLED and enqueues this key with the watcher if it was previously in the
   * READY state.
   */
  void signal() {
    if (state.getAndSet(State.SIGNALLED) == State.READY) {
      watcher.enqueue(this);
    }
  }

  @Override
  public boolean isValid() {
    return watcher.isOpen() && valid.get();
  }

  @Override
  public List<WatchEvent<?>> pollEvents() {
    List<WatchEvent<?>> result = new ArrayList<>(events.size());
    events.drainTo(result);
    int overflowCount = overflow.getAndSet(0);
    if (overflowCount != 0) {
      result.add(overflowEvent(overflowCount));
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public boolean reset() {
    // calling reset() multiple times without polling events would cause key to be placed in watcher
    // queue multiple times, but not much that can be done about that
    if (isValid() && state.compareAndSet(State.SIGNALLED, State.READY)) {
      // requeue if events are pending
      if (!events.isEmpty()) {
        signal();
      }
      return true;
    }

    return false;
  }

  @Override
  public void cancel() {
    valid.set(false);
    watcher.cancelled(this);
  }

  @Override
  public Watchable watchable() {
    return watchable;
  }

  @VisibleForTesting
  enum State {
    READY,
    SIGNALLED
  }
}
