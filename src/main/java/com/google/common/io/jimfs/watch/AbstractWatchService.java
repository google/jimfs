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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Abstract implementation of {@link WatchService}. Provides the means for registering and managing
 * keys but does not handle actually watching. Subclasses should implement the means of watching
 * watchables, posting events to registered keys and queueing keys with the service by signalling
 * them.
 *
 * @author Colin Decker
 */
public abstract class AbstractWatchService implements WatchService {

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

  boolean isOpen() {
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
  void cancelled(Key key) {
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
  public void close() throws IOException {
    if (open.compareAndSet(true, false)) {
      // TODO(cgdecker): If there's a better way to guarantee that no thread is blocked on the queue
      // after this is closed I'd love to know

      // Attempt to acquire the write lock... each time we fail, there may be threads blocked
      // on the queue (if none are blocked, they will be blocked soon)
      while (!lock.writeLock().tryLock()) {
        // so transfer the poison to each blocked thread and attempt to acquire the lock again
        while (queue.tryTransfer(poison)) {
          // nothing to do here
        }
      }

      // the write lock has been acquired, so no threads are blocking on the queue
      // can now just unlock... any thread blocking for a read lock will now see this is closed
      lock.writeLock().unlock();
    }
  }
}
