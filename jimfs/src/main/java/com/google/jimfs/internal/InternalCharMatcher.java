package com.google.jimfs.internal;

import java.util.Arrays;

/**
 * Simple replacement for the real CharMatcher until it's out of @Beta.
 *
 * @author Colin Decker
 */
final class InternalCharMatcher {

  public static InternalCharMatcher anyOf(String chars) {
    return new InternalCharMatcher(chars);
  }

  private final char[] chars;

  private InternalCharMatcher(String chars) {
    this.chars = chars.toCharArray();
    Arrays.sort(this.chars);
  }

  public boolean matches(char c) {
    return Arrays.binarySearch(chars, c) >= 0;
  }
}
