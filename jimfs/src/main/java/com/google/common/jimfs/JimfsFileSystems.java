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
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Initializes and configures new file system instances.
 *
 * @author Colin Decker
 */
final class JimfsFileSystems {

  private JimfsFileSystems() {}

  /**
   * Initialize and configure a new file system with the given provider and URI, using the given
   * configuration.
   */
  public static JimfsFileSystem newFileSystem(
      JimfsFileSystemProvider provider, URI uri, Configuration config) throws IOException {
    PathService pathService = new PathService(config);
    FileSystemState state = new FileSystemState(
        JimfsFileSystemProvider.removeFileSystemRunnable(uri));

    JimfsFileStore fileStore = createFileStore(config, pathService, state);
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
      Configuration config, PathService pathService, FileSystemState state) {
    AttributeService attributeService = new AttributeService(config);

    // TODO(cgdecker): Make disk values configurable
    HeapDisk disk = new HeapDisk(config);
    FileFactory fileFactory = new FileFactory(disk);

    Map<Name, Directory> roots = new HashMap<>();

    // create roots
    for (String root : config.roots) {
      JimfsPath path = pathService.parsePath(root);
      if (!path.isAbsolute() && path.getNameCount() == 0) {
        throw new IllegalArgumentException("Invalid root path: " + root);
      }

      Name rootName = path.root();

      Directory rootDir = fileFactory.createRootDirectory(rootName);
      attributeService.setInitialAttributes(rootDir);
      roots.put(rootName, rootDir);
    }

    return new JimfsFileStore(
        new FileTree(roots), fileFactory, disk, attributeService, config.supportedFeatures, state);
  }

  /**
   * Creates the default view of the file system using the given working directory.
   */
  private static FileSystemView createDefaultView(Configuration config,
      JimfsFileStore fileStore, PathService pathService) throws IOException {
    JimfsPath workingDirPath = pathService.parsePath(config.workingDirectory);

    Directory dir = fileStore.getRoot(workingDirPath.root());
    if (dir == null) {
      throw new IllegalArgumentException("Invalid working dir path: " + workingDirPath);
    }

    for (Name name : workingDirPath.names()) {
      Directory newDir = fileStore.directoryCreator().get();
      fileStore.setInitialAttributes(newDir);
      dir.link(name, newDir);

      dir = newDir;
    }

    return new FileSystemView(fileStore, dir, workingDirPath);
  }
}
