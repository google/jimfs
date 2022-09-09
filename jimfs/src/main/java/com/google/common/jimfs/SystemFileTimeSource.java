/*
 * Copyright 2021 Google Inc.
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

import java.nio.file.attribute.FileTime;

/** Implementation of of {@link FileTimeSource} that gets the current time from the system. */
enum SystemFileTimeSource implements FileTimeSource {
  INSTANCE;

  // If/when Jimfs requires Java 8 this should use the FileTime factory that takes an Instant as
  // that has the potential to be more precise. At that point, we should make a similar change to
  // FakeFileTimeSource.

  @Override
  public FileTime now() {
    return FileTime.fromMillis(System.currentTimeMillis());
  }

  @Override
  public String toString() {
    return "SystemFileTimeSource";
  }
}
