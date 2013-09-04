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

import com.google.common.collect.Iterables;

import java.nio.file.LinkOption;
import java.util.Arrays;

/**
 * Enum defining symbolic link handling behavior.
 *
 * @author Colin Decker
 */
public enum LinkHandling {
  /**
   * Follow symbolic link if it's the last file in a path.
   */
  FOLLOW_LINKS,

  /**
   * Don't follow final symbolic link if it's the last file in a path.
   */
  NOFOLLOW_LINKS;

  /**
   * Returns the appropriate link handling value for the given array of JDK options. (Returns
   * {@link #NOFOLLOW_LINKS} if and only if the given options contains
   * {@link LinkOption#NOFOLLOW_LINKS}).
   */
  public static LinkHandling fromOptions(Object... options) {
    return fromOptions(Arrays.asList(options));
  }

  /**
   * Returns the appropriate link handling value for the given iterable of JDK options. (Returns
   * {@link #NOFOLLOW_LINKS} if and only if the given options contains
   * {@link LinkOption#NOFOLLOW_LINKS}).
   */
  public static LinkHandling fromOptions(Iterable<?> options) {
    return Iterables.contains(options, LinkOption.NOFOLLOW_LINKS)
        ? NOFOLLOW_LINKS
        : FOLLOW_LINKS;
  }
}
