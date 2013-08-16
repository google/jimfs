package com.google.common.io.jimfs;

import com.google.common.collect.Iterables;

import java.nio.file.LinkOption;
import java.util.Arrays;

/**
 * Enum defining symbolic link handling behavior.
 *
 * @author Colin Decker
 */
enum LinkHandling {
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
