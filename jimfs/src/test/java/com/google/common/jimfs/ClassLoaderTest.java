/*
 * Copyright 2015 Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;

/**
 * Tests behavior when user code loads Jimfs in a separate class loader from the system class
 * loader (which is what {@link FileSystemProvider#installedProviders()} uses to load
 * {@link FileSystemProvider}s as services from the classpath).
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class ClassLoaderTest {

  @Test
  public void separateClassLoader() throws Exception {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader systemLoader = ClassLoader.getSystemClassLoader();

    ClassLoader loader = MoreObjects.firstNonNull(contextLoader, systemLoader);

    if (loader instanceof URLClassLoader) {
      // Anything we can do if it isn't a URLClassLoader?
      URLClassLoader urlLoader = (URLClassLoader) loader;

      ClassLoader separateLoader =
          new URLClassLoader(
              urlLoader.getURLs(), systemLoader.getParent()); // either null or the boostrap loader

      Thread.currentThread().setContextClassLoader(separateLoader);
      try {
        Class<?> thisClass = separateLoader.loadClass(getClass().getName());
        Method createFileSystem = thisClass.getDeclaredMethod("createFileSystem");

        // First, the call to Jimfs.newFileSystem in createFileSystem needs to succeed
        Object fs = createFileSystem.invoke(null);

        // Next, some sanity checks:

        // The file system is a JimfsFileSystem
        assertEquals("com.google.common.jimfs.JimfsFileSystem", fs.getClass().getName());

        // But it is not seen as an instance of JimfsFileSystem here because it was loaded by a
        // different ClassLoader
        assertFalse(fs instanceof JimfsFileSystem);

        // But it should be an instance of FileSystem regardless, which is the important thing.
        assertTrue(fs instanceof FileSystem);

        // And normal file operations should work on it despite its provenance from a different
        // ClassLoader
        writeAndRead((FileSystem) fs, "bar.txt", "blah blah");

        // And for the heck of it, test the contents of the file that was created in
        // createFileSystem too
        assertEquals(
            "blah", Files.readAllLines(((FileSystem) fs).getPath("foo.txt"), UTF_8).get(0));
      } finally {
        Thread.currentThread().setContextClassLoader(contextLoader);
      }
    }
  }

  /**
   * This method is really just testing that {@code Jimfs.newFileSystem()} succeeds. Without
   * special handling, when the system class loader loads our {@code FileSystemProvider}
   * implementation as a service and this code (the user code) is loaded in a separate class
   * loader, the system-loaded provider won't see the instance of {@code Configuration} we give it
   * as being an instance of the {@code Configuration} it's expecting (they're completely separate
   * classes) and creation of the file system will fail.
   */
  public static FileSystem createFileSystem() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

    // Just some random operations to verify that basic things work on the created file system.
    writeAndRead(fs, "foo.txt", "blah");

    return fs;
  }

  private static void writeAndRead(FileSystem fs, String path, String text) throws IOException {
    Path p = fs.getPath(path);
    Files.write(p, ImmutableList.of(text), UTF_8);
    List<String> lines = Files.readAllLines(p, UTF_8);
    assertEquals(text, lines.get(0));
  }
}
