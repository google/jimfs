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
public final class StorageConfiguration {

  /**
   * Returns a new storage configuration for block storage. This is the preferred type of storage
   * to use.
   *
   * <p>Block storage keeps a central store of data blocks per file system and provides files with
   * blocks to store their data in. As files are deleted, those blocks are returned to the file
   * system and reused for new files, resulting in much faster writes and much less garbage
   * collection during use of the file system.
   *
   * <p>If no other options are configured, the returned configuration uses heap memory, a block
   * size of 8192 bytes and has no limit on the number of of unused blocks it will retain for reuse.
   * In other words, the size of the storage is always the maximum number of bytes it has used
   * needed at one time so far.
   */
  public static StorageConfiguration block() {
    return new StorageConfiguration(true);
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
   * <p>By default, this storage uses heap memory. The {@linkplain #blockSize(int) block size} and
   * {@linkplain #maxCacheSize(long) max cache size} options do not apply to this type of storage
   * and cannot be set.
   */
  public static StorageConfiguration perFile() {
    return new StorageConfiguration(false);
  }

  private final boolean block;
  private boolean direct = false;
  private int blockSize = 8192;
  private long maxCacheSize = Long.MAX_VALUE;

  private StorageConfiguration(boolean block) {
    this.block = block;
  }

  /**
   * Configures this storage to store data in heap memory (e.g. in {@code byte} arrays).
   *
   * <p>This is the default.
   */
  public StorageConfiguration heap() {
    this.direct = false;
    return this;
  }

  /**
   * Configures this storage to store data in direct/native memory (e.g. in
   * {@linkplain ByteBuffer#allocateDirect(int) direct ByteBuffers}). This may have slight
   * performance advantages in certain situations (such as transferring files to sockets) but in
   * general {@linkplain #heap() heap} storage is preferred.
   */
  public StorageConfiguration direct() {
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
  public StorageConfiguration blockSize(int blockSize) {
    checkState(block, "cannot set block size for non-block storage");
    checkArgument(blockSize > 0, "maxCacheSize must be positive");
    this.blockSize = blockSize;
    return this;
  }

  /**
   * Configures this storage to use no more than the given {@code maxCacheSize} bytes when the
   * currently allocated files do not require more.
   *
   * <p>In other words, the storage will allocate an unlimited number of bytes for creating and
   * writing to files (up to the memory limits of the VM, of course) but as long as the total
   * number of bytes in the storage is greater than the given size, blocks that are freed by
   * truncated or deleted files will not be discarded rather than being cached for reuse by other
   * files.
   *
   * <p>The default max cache size is unlimited (that is, {@code Long.MAX_VALUE}). This means that
   * the storage will retain the maximum number of bytes it's allocated so far until garbage
   * collected.
   *
   * <p>The max cache size may also be set to 0, meaning that the storage will not cache any unused
   * blocks.
   *
   * @throws IllegalStateException if this configuration is for {@linkplain #perFile() per-file}
   *     storage
   * @throws IllegalArgumentException if {@code maxCacheSize} is negative
   */
  public StorageConfiguration maxCacheSize(long maxCacheSize) {
    checkState(block, "cannot set max cache size for non-block storage");
    checkArgument(maxCacheSize >= 0, "maxCacheSize must be non-negative");
    this.maxCacheSize = maxCacheSize;
    return this;
  }
}
