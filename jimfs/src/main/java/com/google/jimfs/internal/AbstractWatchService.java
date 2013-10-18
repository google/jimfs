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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nullable;

/**
 * Abstract implementation of {@link WatchService}. Provides the means for registering and managing
 * keys but does not handle actually watching. Subclasses should implement the means of watching
 * watchables, posting events to registered keys and queueing keys with the service by signalling
 * them.
 *
 * @author Colin Decker
 */
abstract class AbstractWatchService implements WatchService {

  private final TransferQueue<WatchKey> queue = new LinkedTransferQueue<>();
  private final WatchKey poison = new Key(this, null, ImmutableSet.<WatchEvent.Kind<?>>of());

  /**
   * This lock is used to ensure that no thread may block on poll with timeout or take and miss
   * being notified that the service has been closed. Each thread acquires a read lock before
   * blocking and the close() method transfers the poison to blocked queue readers until it manages
   * to acquire the write lock, at which point it's guaranteed there will be no more blocked
   * consumers.
   */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  private final AtomicBoolean open = new AtomicBoolean(true);

  public Key register(Watchable watchable, Iterable<? extends WatchEvent.Kind<?>> eventTypes)
      throws IOException {
    checkOpen();
    return new Key(this, watchable, eventTypes);
  }

  /**
   * Returns whether or not this watch service is open.
   */
  @VisibleForTesting
  public boolean isOpen() {
    return open.get();
  }

  void enqueue(Key key) {
    if (isOpen()) {
      queue.add(key);
    }
  }

  /**
   * Called when the given key is cancelled. Does nothing by default.
   */
  public void cancelled(Key key) {
  }

  @VisibleForTesting
  ImmutableList<WatchKey> queuedKeys() {
    return ImmutableList.copyOf(queue);
  }

  @Override
  public WatchKey poll() {
    checkOpen();
    return check(queue.poll());
  }

  @Override
  public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
    checkOpen();
    lock.readLock().lock();
    try {
      checkOpen(); // check again because it's possible the lock blocked for close() to complete
      return check(queue.poll(timeout, unit));
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public WatchKey take() throws InterruptedException {
    checkOpen();
    lock.readLock().lock();
    try {
      checkOpen(); // check again because it's possible the lock blocked for close() to complete
      return check(queue.take());
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns the given key, throwing an exception if it's the poison.
   */
  private WatchKey check(WatchKey key) {
    if (key == poison) {
      throw new ClosedWatchServiceException();
    }
    return key;
  }

  private void checkOpen() {
    if (!open.get()) {
      throw new ClosedWatchServiceException();
    }
  }

  @Override
  public void close() {
    if (open.compareAndSet(true, false)) {
      // TODO(cgdecker): If there's a better way to guarantee that no thread is blocked on the
      // queue after this is closed I'd love to know

      // Attempt to acquire the write lock... each time we fail, there may be threads blocked
      // on the queue (if none are blocked, they will be blocked soon)
      while (!lock.writeLock().tryLock()) {
        // so transfer the poison to each blocked thread and attempt to acquire the lock again
        boolean poisonTransferred;
        do {
          poisonTransferred = queue.tryTransfer(poison);
        } while (poisonTransferred);
      }

      // the write lock has been acquired, so no threads are blocking on the queue
      // can now just unlock... any thread blocking for a read lock will now see this is closed
      lock.writeLock().unlock();
    }
  }

  /**
   * A basic implementation of {@link WatchEvent}.
   */
  static final class Event<T> implements WatchEvent<T> {

    private final Kind<T> kind;
    private final int count;
    private final @Nullable
    T context;

    public Event(Kind<T> kind, int count, @Nullable T context) {
      this.kind = checkNotNull(kind);
      checkArgument(count >= 0, "count (%s) must be non-negative", count);
      this.count = count;
      this.context = context;
    }

    @Override
    public Kind<T> kind() {
      return kind;
    }

    @Override
    public int count() {
      return count;
    }

    @Override
    public @Nullable T context() {
      return context;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Event) {
        Event<?> other = (Event<?>) obj;
        return kind().equals(other.kind())
            && count() == other.count()
            && Objects.equal(context(), other.context());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(kind(), count(), context());
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
          .add("kind", kind())
          .add("count", count())
          .add("context", context())
          .toString();
    }
  }

  /**
   * Implementation of {@link WatchKey} for an {@link AbstractWatchService}.
   */
  static final class Key implements WatchKey {

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
     * Gets the current state of this key, State.READY or SIGNALLED.
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
    public void post(WatchEvent<?> event) {
      if (!events.offer(event)) {
        overflow.incrementAndGet();
      }
    }

    /**
     * Sets the state to SIGNALLED and enqueues this key with the watcher if it was previously in the
     * READY state.
     */
    public void signal() {
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
      // note: it's correct to be able to retrieve more events from a key without calling reset()
      // reset() is ONLY for "returning" the key to the watch service to potentially be retrieved by
      // another thread when you're finished with it
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
}
