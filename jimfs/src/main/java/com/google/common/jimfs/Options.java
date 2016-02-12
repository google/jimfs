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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.util.Set;

/**
 * Utility methods for normalizing user-provided options arrays and sets to canonical immutable
 * sets of options.
 *
 * @author Colin Decker
 */
final class Options {

  private Options() {}

  /**
   * Immutable set containing LinkOption.NOFOLLOW_LINKS.
   */
  public static final ImmutableSet<LinkOption> NOFOLLOW_LINKS =
      ImmutableSet.of(LinkOption.NOFOLLOW_LINKS);

  /**
   * Immutable empty LinkOption set.
   */
  public static final ImmutableSet<LinkOption> FOLLOW_LINKS = ImmutableSet.of();

  private static final ImmutableSet<OpenOption> DEFAULT_READ = ImmutableSet.<OpenOption>of(READ);

  private static final ImmutableSet<OpenOption> DEFAULT_READ_NOFOLLOW_LINKS =
      ImmutableSet.<OpenOption>of(READ, LinkOption.NOFOLLOW_LINKS);

  private static final ImmutableSet<OpenOption> DEFAULT_WRITE =
      ImmutableSet.<OpenOption>of(WRITE, CREATE, TRUNCATE_EXISTING);

  /**
   * Returns an immutable set of link options.
   */
  public static ImmutableSet<LinkOption> getLinkOptions(LinkOption... options) {
    return options.length == 0 ? FOLLOW_LINKS : NOFOLLOW_LINKS;
  }

  /**
   * Returns an immutable set of open options for opening a new file channel.
   */
  public static ImmutableSet<OpenOption> getOptionsForChannel(Set<? extends OpenOption> options) {
    if (options.isEmpty()) {
      return DEFAULT_READ;
    }

    boolean append = options.contains(APPEND);
    boolean write = append || options.contains(WRITE);
    boolean read = !write || options.contains(READ);

    if (read) {
      if (append) {
        throw new UnsupportedOperationException("'READ' + 'APPEND' not allowed");
      }

      if (!write) {
        // ignore all write related options
        return options.contains(LinkOption.NOFOLLOW_LINKS)
            ? DEFAULT_READ_NOFOLLOW_LINKS
            : DEFAULT_READ;
      }
    }

    // options contains write or append and may also contain read
    // it does not contain both read and append

    if (options.contains(WRITE)) {
      return ImmutableSet.copyOf(options);
    } else {
      return new ImmutableSet.Builder<OpenOption>()
          .add(WRITE)
          .addAll(options)
          .build();
    }
  }

  /**
   * Returns an immutable set of open options for opening a new input stream.
   */
  @SuppressWarnings("unchecked") // safe covariant cast
  public static ImmutableSet<OpenOption> getOptionsForInputStream(OpenOption... options) {
    boolean nofollowLinks = false;
    for (OpenOption option : options) {
      if (checkNotNull(option) != READ) {
        if (option == LinkOption.NOFOLLOW_LINKS) {
          nofollowLinks = true;
        } else {
          throw new UnsupportedOperationException("'" + option + "' not allowed");
        }
      }
    }

    // just return the link options for finding the file, nothing else is needed
    return (ImmutableSet<OpenOption>)
        (ImmutableSet<?>) (nofollowLinks ? NOFOLLOW_LINKS : FOLLOW_LINKS);
  }

  /**
   * Returns an immutable set of open options for opening a new output stream.
   */
  public static ImmutableSet<OpenOption> getOptionsForOutputStream(OpenOption... options) {
    if (options.length == 0) {
      return DEFAULT_WRITE;
    }

    ImmutableSet<OpenOption> result = ImmutableSet.copyOf(options);
    if (result.contains(READ)) {
      throw new UnsupportedOperationException("'READ' not allowed");
    }
    return result;
  }

  /**
   * Returns an immutable set of the given options for a move.
   */
  public static ImmutableSet<CopyOption> getMoveOptions(CopyOption... options) {
    return ImmutableSet.copyOf(Lists.asList(LinkOption.NOFOLLOW_LINKS, options));
  }

  /**
   * Returns an immutable set of the given options for a copy.
   */
  public static ImmutableSet<CopyOption> getCopyOptions(CopyOption... options) {
    ImmutableSet<CopyOption> result = ImmutableSet.copyOf(options);
    if (result.contains(ATOMIC_MOVE)) {
      throw new UnsupportedOperationException("'ATOMIC_MOVE' not allowed");
    }
    return result;
  }
}
