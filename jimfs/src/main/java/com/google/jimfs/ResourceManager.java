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

package com.google.jimfs;

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

  // TODO(cgdecker): Give this full responsibility for closing a FileSystem and a different name.

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
