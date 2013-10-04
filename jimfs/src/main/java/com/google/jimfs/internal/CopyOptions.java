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

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.common.collect.ImmutableSet;

import java.nio.file.LinkOption;
import java.util.Arrays;
import java.util.Set;

/**
 * Options controlling the behavior of move and copy operations.
 *
 * @author Colin Decker
 */
final class CopyOptions extends LinkOptions {

  private static final CopyOptions MOVE = new CopyOptions(true, ImmutableSet.of());
  private static final CopyOptions COPY = new CopyOptions(false, ImmutableSet.of());

  /**
   * Creates a new options object for a move operation from the given options.
   */
  public static CopyOptions move(Object... options) {
    return from(true, Arrays.asList(options));
  }

  /**
   * Creates a new options object for a copy operation from the given options.
   */
  public static CopyOptions copy(Object... options) {
    return from(false, Arrays.asList(options));
  }

  /**
   * Creates a new options object from the given options.
   */
  private static CopyOptions from(boolean move, Iterable<?> options) {
    Set<?> optionsSet = options instanceof Set<?> ? (Set<?>) options : ImmutableSet.copyOf(options);
    if (optionsSet.isEmpty()) {
      return move ? MOVE : COPY;
    }
    return new CopyOptions(move, optionsSet);
  }

  private final boolean move;
  private final ImmutableSet<?> options;

  private CopyOptions(boolean move, Set<?> options) {
    super(move ? ImmutableSet.of(LinkOption.NOFOLLOW_LINKS) : options);
    this.move = move;
    this.options = ImmutableSet.copyOf(options);
  }

  /**
   * Returns whether or not these options are for a move operation.
   */
  public boolean isMove() {
    return move;
  }

  /**
   * returns whether or not these options are for a copy operation.
   */
  public boolean isCopy() {
    return !move;
  }

  /**
   * Returns whether or not attributes should be copied as well.
   */
  public boolean isCopyAttributes() {
    return options.contains(COPY_ATTRIBUTES);
  }

  /**
   * Returns whether or not a move must be an atomic move.
   */
  public boolean isAtomicMove() {
    return options.contains(ATOMIC_MOVE);
  }

  /**
   * Returns whether or not an existing file at the destination should be replaced.
   */
  public boolean isReplaceExisting() {
    return options.contains(REPLACE_EXISTING);
  }
}
