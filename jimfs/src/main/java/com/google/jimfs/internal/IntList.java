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

import static com.google.jimfs.internal.Util.nextPowerOf2;

import java.util.Arrays;

/**
 * Simple list of int values. Values can only be added and removed at the end of the list.
 *
 * @author Colin Decker
 */
final class IntList {

  private int[] values;
  private int head;

  /**
   * Creates a new list with a default initial capacity.
   */
  public IntList() {
    this(32);
  }

  /**
   * Creates a new list with the given initial capacity.
   */
  public IntList(int initialCapacity) {
    this.values = new int[initialCapacity];
  }

  private void expandIfNecessary(int minSize) {
    if (minSize > values.length) {
      this.values = Arrays.copyOf(values, nextPowerOf2(minSize));
    }
  }

  /**
   * Returns true if size is 0.
   */
  public boolean isEmpty() {
    return head == 0;
  }

  /**
   * Returns the number of values this list contains.
   */
  public int size() {
    return head;
  }

  /**
   * Transfers the last count values from this list to the given list.
   */
  public void transferTo(IntList list, int count) {
    int start = head - count;
    int queueEnd = list.head + count;
    list.expandIfNecessary(queueEnd);

    System.arraycopy(this.values, start, list.values, list.head, count);

    head = start;
    list.head = queueEnd;
  }

  /**
   * Adds the given value to the end of this list.
   */
  public void add(int value) {
    expandIfNecessary(head + 1);
    values[head++] = value;
  }

  /**
   * Gets the value at the given index in this list.
   */
  public int get(int index) {
    return values[index];
  }
}
