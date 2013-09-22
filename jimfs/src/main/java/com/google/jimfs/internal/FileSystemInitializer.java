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

import com.google.jimfs.JimfsConfiguration;

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
      JimfsFileSystemProvider provider, URI uri, JimfsConfiguration config) throws IOException {
    RealPathService pathService = new RealPathService(config.getPathType());
    JimfsFileStore fileStore = new JimfsFileStore("jimfs", config.getAllAttributeProviders());

    File superRoot = fileStore.createDirectory();
    DirectoryTable superRootTable = superRoot.content();

    for (String root : config.getRoots()) {
      createRootDir(root, pathService, fileStore, superRootTable);
    }

    JimfsPath workingDirPath = pathService.parsePath(config.getWorkingDirectory());

    File workingDir = createWorkingDirectory(workingDirPath, fileStore, superRootTable);
    FileSystemService service = new FileSystemService(
        superRoot, workingDir, workingDirPath, fileStore, pathService);

    JimfsFileSystem fileSystem = new JimfsFileSystem(provider, uri, service);
    pathService.setFileSystem(fileSystem);
    return fileSystem;
  }

  private static void createRootDir(String root,
      PathService pathService, JimfsFileStore fileStore, DirectoryTable superRootTable) {
    JimfsPath path = pathService.parsePath(root);
    if (!path.isAbsolute() && path.getNameCount() == 0) {
      throw new IllegalArgumentException("Invalid root path: " + root);
    }

    Name rootName = path.root();

    File rootDir = fileStore.createDirectory();
    DirectoryTable rootDirTable = rootDir.content();
    rootDirTable.linkSelf(rootDir);
    rootDirTable.linkParent(rootDir);
    superRootTable.link(rootName, rootDir);
  }

  private static File createWorkingDirectory(JimfsPath workingDirPath,
      JimfsFileStore fileStore, DirectoryTable superRootTable) throws IOException {
    File dir = superRootTable.get(workingDirPath.root());
    if (dir == null) {
      throw new IllegalArgumentException("Invalid working dir path: " + workingDirPath);
    }
    DirectoryTable table = dir.content();
    for (Name name : workingDirPath.names()) {
      File newDir = fileStore.createDirectory();
      table.link(name, newDir);
      table = newDir.content();
      table.linkSelf(newDir);
      table.linkParent(dir);
      dir = newDir;
    }
    return dir;
  }
}