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

package com.google.jimfs.internal.watch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;

import java.nio.file.WatchEvent;

import javax.annotation.Nullable;

/**
 * A basic implementation of {@link WatchEvent}.
 *
 * @author Colin Decker
 */
public final class Event<T> implements WatchEvent<T> {

  private final Kind<T> kind;
  private final int count;
  private final @Nullable T context;

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
