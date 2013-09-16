package com.google.jimfs.internal;

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;

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
