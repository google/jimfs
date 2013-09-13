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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jimfs.internal.LinkHandling.FOLLOW_LINKS;

import java.io.IOException;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Service handling file lookup for a {@link FileTree}.
 *
 * @author Colin Decker
 */
final class LookupService {

  private static final int MAX_SYMBOLIC_LINK_DEPTH = 10;

  private final PathService pathService;

  public LookupService(PathService pathService) {
    this.pathService = checkNotNull(pathService);
  }

  /**
   * Looks up the file key for the given absolute path.
   */
  public LookupResult lookup(
      FileTree tree, JimfsPath path, LinkHandling linkHandling) throws IOException {
    checkNotNull(path);
    checkNotNull(linkHandling);

    File base;
    if (path.isAbsolute()) {
      base = tree.getSuperRoot().base();
    } else {
      base = tree.base();
      if (isEmpty(path)) {
        // empty path is equivalent to "." in a lookup
        path = pathService.createFileName(Name.SELF);
      }
    }

    tree.readLock().lock();
    try {
      return lookup(tree.getSuperRoot(), base, path.path(), linkHandling, 0);
    } finally {
      tree.readLock().unlock();
    }
  }

  /**
   * Looks up the file key for the given path.
   */
  private LookupResult lookup(
      FileTree superRoot, File dir, JimfsPath path, LinkHandling linkHandling, int linkDepth)
      throws IOException {
    if (path.isAbsolute()) {
      dir = superRoot.base();
    } else if (isEmpty(path)) {
      // empty path is equivalent to "." in a lookup
      path = pathService.createFileName(Name.SELF);
    }

    checkNotNull(linkHandling);
    return lookup(superRoot, dir, path.path(), linkHandling, linkDepth);
  }

  /**
   * Looks up the given names against the given base file. If the file is not a directory, the
   * lookup fails.
   */
  private LookupResult lookup(FileTree superRoot, @Nullable File dir,
      Iterable<Name> names, LinkHandling linkHandling, int linkDepth) throws IOException {
    Iterator<Name> nameIterator = names.iterator();
    Name name = nameIterator.next();
    while (nameIterator.hasNext()) {
      DirectoryTable table = getDirectoryTable(dir);
      File file = table == null ? null : table.get(name);

      if (file != null && file.isSymbolicLink()) {
        LookupResult linkResult = followSymbolicLink(superRoot, table, file, linkDepth);

        if (!linkResult.found()) {
          return LookupResult.notFound();
        }

        dir = linkResult.file();
      } else {
        dir = file;
      }

      name = nameIterator.next();
    }

    return lookupLast(superRoot, dir, name, linkHandling, linkDepth);
  }

  /**
   * Looks up the last element of a path.
   */
  private LookupResult lookupLast(FileTree superRoot, File dir,
      Name name, LinkHandling linkHandling, int linkDepth) throws IOException {
    DirectoryTable table = getDirectoryTable(dir);
    if (table == null) {
      return LookupResult.notFound();
    }

    File file = table.get(name);
    if (file == null) {
      return LookupResult.parentFound(dir);
    }

    if (linkHandling == FOLLOW_LINKS && file.isSymbolicLink()) {
      // TODO(cgdecker): can add info on the symbolic link and its parent here if needed
      // for now it doesn't seem like it's needed though
      return followSymbolicLink(superRoot, table, file, linkDepth);
    }

    return createFoundResult(superRoot, dir, name, file);
  }

  private LookupResult followSymbolicLink(
      FileTree superRoot, DirectoryTable table, File link, int linkDepth) throws IOException {
    if (linkDepth >= MAX_SYMBOLIC_LINK_DEPTH) {
      throw new IOException("too many levels of symbolic links");
    }

    JimfsPath targetPath = link.content();
    return lookup(superRoot, table.self(), targetPath, FOLLOW_LINKS, linkDepth + 1);
  }

  /**
   * Creates a result indicating the file was found. Accounts for cases where the name used to
   * lookup the file was "." or "..", meaning that the directory the last lookup was done in is not
   * actually the parent directory of the file.
   */
  private LookupResult createFoundResult(FileTree superRoot, File parent, Name name, File file) {
    DirectoryTable table = parent.content();
    if (name.equals(Name.SELF) || name.equals(Name.PARENT)) {
      // the parent dir is not the directory we did the lookup in
      // also, the file itself must be a directory
      DirectoryTable fileTable = file.content();
      parent = fileTable.parent();
      if (parent == file) {
        // root dir
        parent = superRoot.base();
        DirectoryTable superRootTable = parent.content();
        name = superRootTable.getName(file);
      } else {
        name = fileTable.name();
      }
    } else {
      name = table.canonicalize(name);
    }
    return LookupResult.found(parent, file, name);
  }

  @Nullable
  private DirectoryTable getDirectoryTable(@Nullable File file) {
    if (file != null && file.isDirectory()) {
      return file.content();
    }

    return null;
  }

  /**
   * Returns true if path has no root component (is not absolute) and either has no name
   * components or only has a single name component, the empty string.
   */
  private static boolean isEmpty(JimfsPath path) {
    return !path.isAbsolute() && (path.getNameCount() == 0
        || path.getNameCount() == 1 && path.getName(0).toString().equals(""));
  }
}
