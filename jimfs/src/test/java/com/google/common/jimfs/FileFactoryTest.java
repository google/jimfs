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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link FileFactory}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class FileFactoryTest {

  private FileFactory factory;

  @Before
  public void setUp() {
    factory = new FileFactory(new HeapDisk(2, 2, 0));
  }

  @Test
  public void testCreateFiles_basic() {
    File file = factory.createDirectory();
    assertThat(file.id()).isEqualTo(0L);
    assertThat(file.isDirectory()).isTrue();

    file = factory.createRegularFile();
    assertThat(file.id()).isEqualTo(1L);
    assertThat(file.isRegularFile()).isTrue();

    file = factory.createSymbolicLink(fakePath());
    assertThat(file.id()).isEqualTo(2L);
    assertThat(file.isSymbolicLink()).isTrue();
  }

  @Test
  public void testCreateFiles_withSupplier() {
    File file = factory.directoryCreator().get();
    assertThat(file.id()).isEqualTo(0L);
    assertThat(file.isDirectory()).isTrue();

    file = factory.regularFileCreator().get();
    assertThat(file.id()).isEqualTo(1L);
    assertThat(file.isRegularFile()).isTrue();

    file = factory.symbolicLinkCreator(fakePath()).get();
    assertThat(file.id()).isEqualTo(2L);
    assertThat(file.isSymbolicLink()).isTrue();
  }

  static JimfsPath fakePath() {
    return PathServiceTest.fakeUnixPathService().emptyPath();
  }
}
