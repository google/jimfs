package com.google.jimfs.internal;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

/**
 * Manages a set of open resources, ensuring they're closed when this is closed.
 *
 * @author Colin Decker
 */
final class ResourceManager implements Closeable {

  private final Set<Closeable> resources = Sets.newConcurrentHashSet();

  /**
   * Registers the given resource to be closed when this manager is closed. Should be called when
   * the resource is opened.
   */
  public <C extends Closeable> C register(C resource) {
    resources.add(resource);
    return resource;
  }

  /**
   * Unregisters this resource. Should be called when the resource is closed.
   */
  public void unregister(Closeable resource) {
    resources.remove(resource);
  }

  @Override
  public void close() throws IOException {
    Throwable thrown = null;
    for (Closeable resource : resources) {
      try {
        resource.close();
      } catch (Throwable e) {
        if (thrown == null) {
          thrown = e;
        } else {
          thrown.addSuppressed(e);
        }
      }
    }

    Throwables.propagateIfPossible(thrown, IOException.class);
  }
}
