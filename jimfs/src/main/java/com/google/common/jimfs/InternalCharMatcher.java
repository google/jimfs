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
