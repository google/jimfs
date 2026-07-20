/*
 * Copyright 2026 Google Inc.
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

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * A test that the {@code toPath} method of {@link SystemJimfsFileSystemProvider} handles the case
 * in which that class and {@link JimfsFileSystem} are from different class loaders.
 *
 * <p>See <a href="https://github.com/google/jimfs/issues/476">issue #476</a>.
 */
public class CrossClassLoaderAccessTest {
  @Test
  public void testCrossClassLoaderAccessToPathDoesNotThrow() throws Exception {
    try (URLClassLoader isolatedLoader = createIsolatedClassLoader()) {
      String fsName = "isolated-fs";

      Class<?> jimfsClass = isolatedLoader.loadClass("com.google.common.jimfs.Jimfs");
      Class<?> configClass = isolatedLoader.loadClass("com.google.common.jimfs.Configuration");
      Object unixConfig = configClass.getMethod("unix").invoke(null);
      jimfsClass
          .getMethod("newFileSystem", String.class, configClass)
          .invoke(null, fsName, unixConfig);

      URI isolatedUri = new URI("jimfs", fsName, "/test-path", null, null);
      // We just need to test that the next operation doesn't throw IllegalAccessException.
      Path unused = Paths.get(isolatedUri);
    }
  }

  private static URLClassLoader createIsolatedClassLoader() throws Exception {
    URL jimfsClassesUrl =
        Class.forName("com.google.common.jimfs.Jimfs")
            .getProtectionDomain()
            .getCodeSource()
            .getLocation();

    ClassLoader parentLoader = CrossClassLoaderAccessTest.class.getClassLoader();
    return new URLClassLoader(new URL[] {jimfsClassesUrl}, parentLoader) {
      @Override
      public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name.startsWith("com.google.common.jimfs.")) {
          Class<?> c = findLoadedClass(name);
          if (c != null) {
            return c;
          }
          try {
            return super.findClass(name);
          } catch (ClassNotFoundException e) {
            /*
             * Fall back to the parent class loader for jimfs classes not in the main jimfs jar
             * (like this test).
             */
          }
        }
        return super.loadClass(name);
      }
    };
  }
}
