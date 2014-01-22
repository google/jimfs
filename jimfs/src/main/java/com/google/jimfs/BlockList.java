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

package com.google.jimfs;

import static com.google.jimfs.Util.clear;
import static com.google.jimfs.Util.nextPowerOf2;

import java.util.Arrays;

/**
 * Simple list of byte array blocks. Blocks can only be added and removed at the end of the list.
 *
 * @author Colin Decker
 */
final class BlockList {

  private byte[][] blocks;
  private int head;

  /**
   * Creates a new list with a default initial capacity.
   */
  public BlockList() {
    this(32);
  }

  /**
   * Creates a new list with the given initial capacity.
   */
  public BlockList(int initialCapacity) {
    this.blocks = new byte[initialCapacity][];
  }

  private void expandIfNecessary(int minSize) {
    if (minSize > blocks.length) {
      this.blocks = Arrays.copyOf(blocks, nextPowerOf2(minSize));
    }
  }

  /**
   * Returns true if size is 0.
   */
  public boolean isEmpty() {
    return head == 0;
  }

  /**
   * Returns the number of blocks this list contains.
   */
  public int size() {
    return head;
  }

  /**
   * Copies the last {@code count} blocks from this list to the end of the given target list.
   */
  public void copyTo(BlockList target, int count) {
    int start = head - count;
    int targetEnd = target.head + count;
    target.expandIfNecessary(targetEnd);

    System.arraycopy(this.blocks, start, target.blocks, target.head, count);
    target.head = targetEnd;
  }

  /**
   * Transfers the last {@code count} blocks from this list to the end of the given target list.
   */
  public void transferTo(BlockList target, int count) {
    copyTo(target, count);
    truncate(head - count);
  }

  /**
   * Truncates this list to the given size.
   */
  public void truncate(int size) {
    clear(blocks, size, head - size);
    head = size;
  }

  /**
   * Adds the given block to the end of this list.
   */
  public void add(byte[] block) {
    expandIfNecessary(head + 1);
    blocks[head++] = block;
  }

  /**
   * Gets the block at the given index in this list.
   */
  public byte[] get(int index) {
    return blocks[index];
  }
}
