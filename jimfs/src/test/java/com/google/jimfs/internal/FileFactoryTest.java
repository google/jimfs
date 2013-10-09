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

import static com.google.jimfs.internal.PathServiceTest.fakeUnixPathService;
import static org.truth0.Truth.ASSERT;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link FileFactory}.
 *
 * @author Colin Decker
 */
public class FileFactoryTest {

  private FileFactory factory;

  @Before
  public void setUp() {
    factory = new FileFactory(new RegularFileStorage() {
      @Override
      public ByteStore createByteStore() {
        return new StubByteStore(0);
      }

      @Override
      public long getTotalSpace() {
        return 0;
      }

      @Override
      public long getUnallocatedSpace() {
        return 0;
      }
    });
  }

  @Test
  public void testCreateFiles_basic() {
    File file = factory.createDirectory();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();

    file = factory.createRegularFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();

    file = factory.createSymbolicLink(fakePath());
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
  }

  @Test
  public void testCreateFiles_withSupplier() {
    File file = factory.directorySupplier().get();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();

    file = factory.regularFileSupplier().get();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();

    file = factory.symbolicLinkSupplier(fakePath()).get();
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
  }

  static JimfsPath fakePath() {
    return fakeUnixPathService().emptyPath();
  }
}
