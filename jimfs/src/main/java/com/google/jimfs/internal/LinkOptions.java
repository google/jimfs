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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.nio.file.LinkOption;
import java.util.Arrays;

/**
 * Options controlling whether or not to follow the link if the file a path points to is a symbolic
 * link.
 *
 * @author Colin Decker
 */
class LinkOptions {

  public static final LinkOptions FOLLOW_LINKS = new LinkOptions(ImmutableList.of());
  public static final LinkOptions NOFOLLOW_LINKS =
      new LinkOptions(ImmutableList.of(LinkOption.NOFOLLOW_LINKS));

  /**
   * Creates a link options object from the given options.
   */
  public static LinkOptions from(Object... options) {
    return from(Arrays.asList(options));
  }

  /**
   * Creates a link options object from the given options.
   */
  public static LinkOptions from(Iterable<?> options) {
    return Iterables.contains(options, LinkOption.NOFOLLOW_LINKS)
        ? NOFOLLOW_LINKS
        : FOLLOW_LINKS;
  }

  private final boolean nofollowLinks;

  protected LinkOptions(Iterable<?> options) {
    this.nofollowLinks = Iterables.contains(options, LinkOption.NOFOLLOW_LINKS);
  }

  /**
   * Returns {@code true} if the target of a symbolic link should be used rather than the link file
   * itself.
   */
  public final boolean isFollowLinks() {
    return !nofollowLinks;
  }
}
