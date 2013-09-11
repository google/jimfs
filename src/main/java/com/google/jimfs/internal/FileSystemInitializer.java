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

import static com.google.jimfs.internal.LinkHandling.NOFOLLOW_LINKS;

import com.google.jimfs.JimfsConfiguration;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    RealJimfsPathService pathService = new RealJimfsPathService(config.getPathType());
    LookupService lookupService = new LookupService(pathService);
    JimfsFileStore store = new JimfsFileStore("jimfs", config.getAllAttributeProviders());
    ReadWriteLock lock = new ReentrantReadWriteLock();

    FileTree superRootTree = new FileTree(store.createDirectory(), pathService.emptyPath(),
        null, lock, store, pathService, lookupService);
    DirectoryTable superRootTable = superRootTree.base().content();

    for (String root : config.getRoots()) {
      createRootDir(root, pathService, store, superRootTable);
    }

    JimfsPath workingDirPath = pathService.parsePath(config.getWorkingDirectory());

    File workingDir = createWorkingDirectory(workingDirPath, store, superRootTree);
    FileTree workingDirTree = new FileTree(
        workingDir, workingDirPath, superRootTree, lock, store, pathService, lookupService);

    JimfsFileSystem fileSystem = new JimfsFileSystem(
        provider, uri, config, pathService, store, lock, superRootTree, workingDirTree);
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
      JimfsFileStore fileStore, FileTree superRoot) throws IOException {
    File dir = superRoot.lookup(workingDirPath.getRoot(), NOFOLLOW_LINKS)
        .orNull();
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