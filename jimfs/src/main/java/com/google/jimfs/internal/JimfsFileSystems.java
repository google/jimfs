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

import com.google.jimfs.Configuration;

import java.io.IOException;
import java.net.URI;

/**
 * Initializes and configures new file system instances.
 *
 * @author Colin Decker
 */
final class JimfsFileSystems {

  /**
   * Initialize and configure a new file system with the given provider and URI, using the given
   * configuration.
   */
  public static JimfsFileSystem newFileSystem(
      JimfsFileSystemProvider provider, URI uri, Configuration config) throws IOException {
    PathService pathService = new PathService(config);

    JimfsFileStore fileStore = createFileStore(config, pathService);
    FileSystemView defaultView = createDefaultView(config, fileStore, pathService);

    JimfsFileSystem fileSystem = new JimfsFileSystem(
        provider, uri, fileStore, pathService, defaultView);

    pathService.setFileSystem(fileSystem);
    return fileSystem;
  }

  /**
   * Creates the file store for the file system.
   */
  private static JimfsFileStore createFileStore(
      Configuration config, PathService pathService) {
    AttributeService attributeService = new AttributeService(config.getAttributeConfiguration());
    RegularFileStorage storage = new HeapDisk();
    FileFactory fileFactory = new FileFactory(storage);

    File superRoot = fileFactory.createDirectory();
    superRoot.asDirectoryTable().setSuperRoot(superRoot);

    // create roots
    for (String root : config.getRoots()) {
      JimfsPath path = pathService.parsePath(root);
      if (!path.isAbsolute() && path.getNameCount() == 0) {
        throw new IllegalArgumentException("Invalid root path: " + root);
      }

      Name rootName = path.root();

      File rootDir = fileFactory.createDirectory();
      attributeService.setInitialAttributes(rootDir);

      superRoot.asDirectoryTable().link(rootName, rootDir);
      rootDir.asDirectoryTable().setRoot();
    }

    return new JimfsFileStore(new FileTree(superRoot), fileFactory, storage,
        attributeService, config.getSupportedFeatures());
  }

  /**
   * Creates the default view of the file system using the given working directory.
   */
  private static FileSystemView createDefaultView(Configuration config,
      JimfsFileStore fileStore, PathService pathService) throws IOException {
    JimfsPath workingDirPath = pathService.parsePath(config.getWorkingDirectory());

    File dir = fileStore.getRoot(workingDirPath.root());
    if (dir == null) {
      throw new IllegalArgumentException("Invalid working dir path: " + workingDirPath);
    }

    for (Name name : workingDirPath.names()) {
      File newDir = fileStore.createDirectory().get();
      fileStore.setInitialAttributes(newDir);
      dir.asDirectoryTable().link(name, newDir);

      dir = newDir;
    }

    return new FileSystemView(fileStore, dir, workingDirPath);
  }
}