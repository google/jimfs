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

import com.google.jimfs.Jimfs;

import java.io.IOException;
import java.net.URI;

/**
 * Initializes and configures new file system instances.
 *
 * @author Colin Decker
 */
final class FileSystemInitializer {

  /**
   * Initialize and configure a new file system with the given provider and URI, using the given
   * configuration.
   */
  public static JimfsFileSystem createFileSystem(
      JimfsFileSystemProvider provider, URI uri, Jimfs.Configuration config) throws IOException {
    // create core services
    PathService pathService = new RealPathService(config.getPathType());
    AttributeService attributeService = new AttributeService(config.getAttributeViews());
    RegularFileStorage storage = RegularFileStorage.from(config.getStorage());
    FileFactory fileFactory = new FileFactory(storage);

    FileTree tree = createFileTree(config, pathService, fileFactory);
    JimfsFileStore fileStore = new JimfsFileStore(tree, fileFactory, storage, attributeService);

    JimfsPath workingDirPath = pathService.parsePath(config.getWorkingDirectory());
    File workingDir = createWorkingDirectory(workingDirPath, fileFactory, tree);

    FileSystemService service = new FileSystemService(
        fileStore, workingDir, workingDirPath, pathService);
    JimfsFileSystem fileSystem = new JimfsFileSystem(
        provider, uri, fileStore, pathService, service);
    pathService.setFileSystem(fileSystem);
    return fileSystem;
  }

  /**
   * Creates the file tree for the file system including all root directories.
   */
  private static FileTree createFileTree(
      Jimfs.Configuration config, PathService pathService, FileFactory fileFactory) {
    File superRoot = fileFactory.createDirectory();
    DirectoryTable superRootTable = superRoot.content();
    superRootTable.setSuperRoot(superRoot);

    for (String root : config.getRoots()) {
      createRootDir(root, pathService, fileFactory, superRootTable);
    }

    return new FileTree(superRoot);
  }

  private static void createRootDir(String root,
      PathService pathService, FileFactory fileFactory, DirectoryTable superRootTable) {
    JimfsPath path = pathService.parsePath(root);
    if (!path.isAbsolute() && path.getNameCount() == 0) {
      throw new IllegalArgumentException("Invalid root path: " + root);
    }

    Name rootName = path.root();

    File rootDir = fileFactory.createDirectory();
    DirectoryTable rootDirTable = rootDir.content();
    superRootTable.link(rootName, rootDir);
    rootDirTable.setRoot();
  }

  private static File createWorkingDirectory(JimfsPath workingDirPath,
      FileFactory fileFactory, FileTree tree) throws IOException {
    DirectoryEntry rootEntry = tree.getRoot(workingDirPath.root());
    if (rootEntry == null) {
      throw new IllegalArgumentException("Invalid working dir path: " + workingDirPath);
    }
    File dir = rootEntry.file();
    DirectoryTable table = dir.content();
    for (Name name : workingDirPath.names()) {
      File newDir = fileFactory.createDirectory();
      table.link(name, newDir);

      dir = newDir;
      table = newDir.content();
    }
    return dir;
  }
}