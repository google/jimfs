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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link FileContent} implementation that does nothing.
 *
 * @author Colin Decker
 */
public final class FakeFileContent implements FileContent {

  @Override
  public FileContent copy() {
    return new FakeFileContent();
  }

  @Override
  public long size() {
    return 0;
  }

  @Override
  public void linked(DirectoryEntry entry) {
    checkNotNull(entry); // for NullPointerTester
  }

  @Override
  public void unlinked() {
  }

  @Override
  public void deleted() {
  }
}
