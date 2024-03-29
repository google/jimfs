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

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.attribute.FileTime;

/**
 * A symbolic link file, containing a {@linkplain JimfsPath path}.
 *
 * @author Colin Decker
 */
final class SymbolicLink extends File {

  private final JimfsPath target;

  /** Creates a new symbolic link with the given ID and target. */
  public static SymbolicLink create(int id, FileTime creationTime, JimfsPath target) {
    return new SymbolicLink(id, creationTime, target);
  }

  private SymbolicLink(int id, FileTime creationTime, JimfsPath target) {
    super(id, creationTime);
    this.target = checkNotNull(target);
  }

  /** Returns the target path of this symbolic link. */
  JimfsPath target() {
    return target;
  }

  @Override
  File copyWithoutContent(int id, FileTime creationTime) {
    return SymbolicLink.create(id, creationTime, target);
  }
}
