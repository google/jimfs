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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests a Turkish case-insensitive filesystem using public Files APIs.
 *
 * @author Ben Hamilton
 */
@RunWith(JUnit4.class)
public class JimfsTurkishCaseInsensitiveFileSystemTest extends AbstractJimfsIntegrationTest {

  private static final Configuration TURKISH_CONFIGURATION =
      Configuration.windows()
          .toBuilder()
          .setNameCanonicalNormalization(PathNormalization.CASE_FOLD_TURKISH)
          .build();

  @Override
  protected FileSystem createFileSystem() {
    return Jimfs.newFileSystem(TURKISH_CONFIGURATION);
  }

  @Test
  public void caseInsensitiveTurkishMatching() throws IOException {
    Files.createDirectory(path("Windows"));
    assertThatPath("Windows").isSameFileAs("W\u0130NDOWS");
    assertThatPath("Windows").isNotSameFileAs("WINDOWS");
    Files.createFile(path("SYSTEM.INI"));
    assertThatPath("SYSTEM.INI").isNotSameFileAs("system.ini");
    assertThatPath("SYSTEM.INI").isSameFileAs("system.\u0131n\u0131");
  }
}
