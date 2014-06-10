/*
 * Copyright 2014 Google Inc.
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

import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Optional file system features that may be supported or unsupported by a Jimfs file system
 * instance.
 *
 * @author Colin Decker
 */
public enum Feature {

  /**
   * Feature controlling support for hard links to regular files.
   *
   * <p>Affected method:
   *
   * <ul>
   *   <li>{@link Files#createLink(Path, Path)}</li>
   * </ul>
   *
   * <p>If this feature is not enabled, this method will throw
   * {@link UnsupportedOperationException}.
   */
  LINKS,

  /**
   * Feature controlling support for symbolic links.
   *
   * <p>Affected methods:
   *
   * <ul>
   *   <li>{@link Files#createSymbolicLink(Path, Path, FileAttribute...)}</li>
   *   <li>{@link Files#readSymbolicLink(Path)}</li>
   * </ul>
   *
   * <p>If this feature is not enabled, these methods will throw
   * {@link UnsupportedOperationException}.
   */
  SYMBOLIC_LINKS,

  /**
   * Feature controlling support for {@link SecureDirectoryStream}.
   *
   * <p>Affected methods:
   *
   * <ul>
   *   <li>{@link Files#newDirectoryStream(Path)}</li>
   *   <li>{@link Files#newDirectoryStream(Path, DirectoryStream.Filter)}</li>
   *   <li>{@link Files#newDirectoryStream(Path, String)}</li>
   * </ul>
   *
   * <p>If this feature is enabled, the {@link DirectoryStream} instances returned by these methods
   * will also implement {@link SecureDirectoryStream}.
   */
  SECURE_DIRECTORY_STREAM,

  /**
   * Feature controlling support for {@link FileChannel}.
   *
   * <p>Affected methods:
   *
   * <ul>
   *   <li>{@link Files#newByteChannel(Path, OpenOption...)}</li>
   *   <li>{@link Files#newByteChannel(Path, Set, FileAttribute...)}</li>
   *   <li>{@link FileChannel#open(Path, OpenOption...)}</li>
   *   <li>{@link FileChannel#open(Path, Set, FileAttribute...)}</li>
   *   <li>{@link AsynchronousFileChannel#open(Path, OpenOption...)}</li>
   *   <li>{@link AsynchronousFileChannel#open(Path, Set, ExecutorService, FileAttribute...)}</li>
   * </ul>
   *
   * <p>If this feature is not enabled, the {@link SeekableByteChannel} instances returned by the
   * {@code Files} methods will not be {@code FileChannel} instances and the
   * {@code FileChannel.open} and {@code AsynchronousFileChannel.open} methods will throw
   * {@link UnsupportedOperationException}.
   */
  // TODO(cgdecker): Should support for AsynchronousFileChannel be a separate feature?
  FILE_CHANNEL
}
