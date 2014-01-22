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

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Marker interface for implementations of content for different types of files.
 *
 * @author Colin Decker
 */
interface FileContent {

  /**
   * Creates a copy of this content. How this functions depends on the type of file content and is
   * defined by {@link Files#copy(Path, Path, CopyOption...)}. The copy need not be equivalent to
   * the original; for example, a copy of a directory is an empty directory regardless of what the
   * original directory contains.
   */
  FileContent copy() throws IOException;

  /**
   * Returns the size, in bytes, of this content. This may be 0 when there is no logical size we
   * can use for the content since it's implemented using Java objects.
   */
  long size();

  /**
   * Called when the file containing this content is linked in a parent directory. The given entry
   * is the new entry linking to the file.
   */
  void linked(DirectoryEntry entry);

  /**
   * Called when the file containing this content is unlinked from its parent directory.
   */
  void unlinked();

  /**
   * Called when the file containing this content has been deleted by the user. This method may
   * either do any cleanup needed immediately or may mark this content as deleted and do cleanup
   * once no open references to the file (such as streams) remain.
   */
  void deleted();
}
