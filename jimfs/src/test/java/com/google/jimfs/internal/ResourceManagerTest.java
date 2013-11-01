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

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link ResourceManager}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class ResourceManagerTest {

  @Test
  public void testResourceManager() {
    ResourceManager resourceManager = new ResourceManager();
    List<TestCloseable> resources = new ArrayList<>();
    resources.add(resourceManager.register(new TestCloseable()));
    resources.add(resourceManager.register(new TestCloseable()));
    resources.add(resourceManager.register(new ThrowsOnClose("a")));
    TestCloseable toUnregister = resourceManager.register(new TestCloseable());
    resources.add(resourceManager.register(new ThrowsOnClose("b")));
    resources.add(resourceManager.register(new TestCloseable()));
    resources.add(resourceManager.register(new ThrowsOnClose("c")));

    for (TestCloseable resource : resources) {
      ASSERT.that(resource.closed).isFalse();
    }
    ASSERT.that(toUnregister.closed).isFalse();

    resourceManager.unregister(toUnregister);

    try {
      resourceManager.close();
      fail();
    } catch (IOException expected) {
      ASSERT.that(expected.getSuppressed().length).is(2);

      Set<String> exceptionMessages = new HashSet<>();
      exceptionMessages.add(expected.getMessage());
      for (Throwable suppressed : expected.getSuppressed()) {
        exceptionMessages.add(suppressed.getMessage());
      }
      ASSERT.that(exceptionMessages).has().exactly("a", "b", "c");
    }

    for (TestCloseable resource : resources) {
      ASSERT.that(resource.closed).isTrue();
    }
    ASSERT.that(toUnregister.closed).isFalse();
  }

  private static class TestCloseable implements Closeable {

    boolean closed = false;

    @Override
    public void close() throws IOException {
      closed = true;
    }
  }

  private static class ThrowsOnClose extends TestCloseable {

    private final String string;

    private ThrowsOnClose(String string) {
      this.string = string;
    }

    @Override
    public void close() throws IOException {
      super.close();
      throw new IOException(string);
    }
  }
}
