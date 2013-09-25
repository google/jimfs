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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.path.CaseSensitivity;
import com.google.jimfs.path.PathType;

import java.net.URI;
import java.nio.file.FileSystem;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Configuration for an in-memory file system instance.
 *
 * @author Colin Decker
 */
public final class FileSystemConfiguration {

  private PathType pathType;
  private String name;

  private final Set<String> roots = new LinkedHashSet<>();
  private String workingDirectory;

  private StorageConfiguration storage = StorageConfiguration.block();
  private AttributeConfiguration attributes = AttributeConfiguration.basic();

  FileSystemConfiguration(PathType pathType) {
    this.pathType = checkNotNull(pathType);
  }

  /**
   * Returns the configured path type for the file system.
   */
  public PathType getPathType() {
    return pathType;
  }

  /**
   * Returns the configured name for the file system or a random name if none was provided.
   */
  public String getName() {
    return name != null ? name : UUID.randomUUID().toString();
  }

  /**
   * Returns the configured roots for the file system.
   */
  public ImmutableList<String> getRoots() {
    return ImmutableList.copyOf(roots);
  }

  /**
   * Returns the configured working directory for the file system.
   */
  public String getWorkingDirectory() {
    return workingDirectory;
  }

  /**
   * Returns the storage configuration for the file system.
   */
  public StorageConfiguration getStorage() {
    return storage;
  }

  /**
   * Creates a new file system using this configuration.
   */
  public FileSystem createFileSystem() {
    return Jimfs.newFileSystem(URI.create(Jimfs.URI_SCHEME + "://" + getName()), this);
  }

  /**
   * Returns the configured set of attribute providers for the file system.
   */
  public ImmutableSet<AttributeProvider> getAttributeProviders() {
    return ImmutableSet.copyOf(attributes.getProviders(new HashMap<String, AttributeProvider>()));
  }

  /**
   * Sets the name for the created file system, which will be used as the host part of the URI that
   * identifies the file system. For example, if the name is "foo" the file system's URI will be
   * "jimfs://foo" and the URI of the path "/bar" on the file system will be "jimfs://foo/bar".
   *
   * <p>By default, a random unique name will be assigned to the file system.
   */
  public FileSystemConfiguration name(String name) {
    this.name = checkNotNull(name);
    return this;
  }

  /**
   * Sets the case sensitivity that should be used in file lookups for the file system.
   */
  public FileSystemConfiguration pathCaseSensitivity(CaseSensitivity caseSensitivity) {
    pathType = pathType.withCaseSensitivity(caseSensitivity);
    return this;
  }

  /**
   * Adds the given root directories to the file system.
   *
   * @throws IllegalStateException if the path type does not allow multiple roots
   */
  public FileSystemConfiguration addRoots(String first, String... more) {
    List<String> roots = Lists.asList(first, more);
    checkState(this.roots.size() + roots.size() == 1 || pathType.allowsMultipleRoots(),
        "this path type does not allow multiple roots");
    for (String root : roots) {
      checkState(!this.roots.contains(root), "root " + root + " is already configured");
      this.roots.add(checkNotNull(root));
    }
    return this;
  }

  /**
   * Sets the working directory for the file system.
   *
   * <p>If not set, the default working directory will be a directory called "work" located in
   * the first root directory in the list of roots.
   */
  public FileSystemConfiguration workingDirectory(String workingDirectory) {
    this.workingDirectory = checkNotNull(workingDirectory);
    return this;
  }

  /**
   * Sets the storage configuration to use for the file system.
   *
   * <p>The default configuration is for block storage using 8192 byte blocks.
   */
  public FileSystemConfiguration storage(StorageConfiguration storage) {
    this.storage = checkNotNull(storage);
    return this;
  }

  /**
   * Sets the attribute view configurations to use for the file system.
   *
   * <p>The default is configuration is for the {@link AttributeConfiguration#basic() basic} view
   * only, to minimize overhead of storing attributes when you don't need anything else.
   */
  public FileSystemConfiguration attributes(AttributeConfiguration... attributes) {
    if (attributes.length == 0) {
      this.attributes = AttributeConfiguration.basic();
    } else {
      this.attributes = new AttributeConfigurationSet(attributes);
    }
    return this;
  }
}
