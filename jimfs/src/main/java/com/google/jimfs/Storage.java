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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Configuration for file system storage.
 *
 * @author Colin Decker
 */
public final class Storage {

  /**
   * The default block size.
   */
  public static final int DEFAULT_BLOCK_SIZE = 8192;

  /**
   * Returns a new storage configuration for block storage. This is the preferred type of storage
   * to use.
   *
   * <p>Block storage keeps a central store of data blocks per file system and provides files with
   * blocks to store their data in. As files are deleted, those blocks are returned to the file
   * system and reused for new files, resulting in much faster writes and much less garbage
   * collection during use of the file system.
   *
   * <p>If no other options are configured, the returned configuration uses heap memory and a block
   * size of 8192 bytes.
   */
  public static Storage block() {
    return new Storage(true);
  }

  /**
   * Returns a new storage configuration for per-file storage.
   *
   * <p><b>Note:</b> In general, you should prefer the use of {@linkplain #block} storage. In most
   * cases it performs as well or better than per-file storage.
   *
   * <p>Per-file storage gives each file its own buffer to store data in. The file's buffer is
   * doubled in size as needed to accommodate the data written to it. Compared to block storage,
   * writes to this type of storage tend to be slower while reads <i>may</i> be slightly faster.
   * The most significant difference is observed when transferring large files to a socket using
   * {@link FileChannel#transferTo}.
   *
   * <p>By default, this storage uses heap memory. The {@linkplain #blockSize(int) block size}
   * option does not apply to this type of storage and cannot be set.
   */
  public static Storage perFile() {
    return new Storage(false);
  }

  private final boolean block;
  private boolean direct = false;
  private int blockSize = DEFAULT_BLOCK_SIZE;

  private Storage(boolean block) {
    this.block = block;
  }

  /**
   * Returns whether or not block storage should be used.
   */
  public boolean isBlock() {
    return block;
  }

  /**
   * Returns whether or not direct storage should be used.
   */
  public boolean isDirect() {
    return direct;
  }

  /**
   * Returns the block size to use if block storage is used.
   */
  public int getBlockSize() {
    return blockSize;
  }

  /**
   * Configures this storage to store data in heap memory (e.g. in {@code byte} arrays).
   *
   * <p>This is the default.
   */
  public Storage heap() {
    this.direct = false;
    return this;
  }

  /**
   * Configures this storage to store data in direct/native memory (e.g. in
   * {@linkplain ByteBuffer#allocateDirect(int) direct ByteBuffers}). This may have slight
   * performance advantages in certain situations (such as transferring files to sockets) but in
   * general {@linkplain #heap() heap} storage is preferred.
   */
  public Storage direct() {
    this.direct = true;
    return this;
  }

  /**
   * Configures this storage to allocate files in blocks of the given {@code blockSize} bytes.
   *
   * <p>The default block size is 8192 bytes.
   *
   * @throws IllegalStateException if this configuration is for {@linkplain #perFile() per-file}
   *     storage
   * @throws IllegalArgumentException if {@code blockSize} is not positive
   */
  public Storage blockSize(int blockSize) {
    checkState(block, "cannot set block size for non-block storage");
    checkArgument(blockSize > 0, "blockSize must be positive");
    this.blockSize = blockSize;
    return this;
  }
}
