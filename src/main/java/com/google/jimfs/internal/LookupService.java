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

import com.google.common.collect.Iterables;
import com.google.jimfs.internal.file.DirectoryTable;
import com.google.jimfs.internal.file.File;
import com.google.jimfs.internal.path.JimfsPath;
import com.google.jimfs.internal.path.Name;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.annotation.Nullable;

/**
 * Service handling file lookup for a {@link FileTree}.
 *
 * @author Colin Decker
 */
final class LookupService {

  private static final int MAX_SYMBOLIC_LINK_DEPTH = 10;

  private final FileTree tree;

  LookupService(FileTree tree) {
    this.tree = tree;
  }

  /**
   * Looks up the file key for the given absolute path.
   */
  public LookupResult lookup(JimfsPath path, LinkHandling linkHandling) throws IOException {
    checkNotNull(path);
    checkNotNull(linkHandling);

    File base = path.isAbsolute()
        ? tree.getSuperRoot().base()
        : tree.base();

    tree.readLock().lock();
    try {
      return lookup(base, toNames(path), linkHandling, 0);
    } finally {
      tree.readLock().unlock();
    }
  }

  /**
   * Looks up the file key for the given path.
   */
  private LookupResult lookup(
      File base, JimfsPath path, LinkHandling linkHandling, int linkDepth)
      throws IOException {
    if (path.isAbsolute()) {
      base = tree.getSuperRoot().base();
    }

    checkNotNull(linkHandling);
    return lookup(base, toNames(path), linkHandling, linkDepth);
  }

  /**
   * Looks up the given names against the given base file. If the file is not a directory, the
   * lookup fails.
   */
  private LookupResult lookup(@Nullable File base,
      Deque<Name> names, LinkHandling linkHandling, int linkDepth) throws IOException {
    Name name = names.removeFirst();
    if (names.isEmpty()) {
      return lookupLast(base, name, linkHandling, linkDepth);
    }

    DirectoryTable table = getDirectoryTable(base);
    File file = table == null ? null : table.get(name);

    if (file != null && file.isSymbolicLink()) {
      LookupResult linkResult = followSymbolicLink(table, file, linkDepth);

      if (!linkResult.found()) {
        return LookupResult.notFound();
      }

      file = linkResult.file();
    }

    return lookup(file, names, linkHandling, linkDepth);
  }

  /**
   * Looks up the last element of a path.
   */
  private LookupResult lookupLast(File base,
      Name name, LinkHandling linkHandling, int linkDepth) throws IOException {
    DirectoryTable table = getDirectoryTable(base);
    if (table == null) {
      return LookupResult.notFound();
    }

    File file = table.get(name);
    if (file == null) {
      return LookupResult.parentFound(base);
    }

    if (linkHandling == FOLLOW_LINKS && file.isSymbolicLink()) {
      // TODO(cgdecker): can add info on the symbolic link and its parent here if needed
      // for now it doesn't seem like it's needed though
      return followSymbolicLink(table, file, linkDepth);
    }

    return LookupResult.found(base, file, table.canonicalize(name));
  }

  private LookupResult followSymbolicLink(
      DirectoryTable table, File link, int linkDepth) throws IOException {
    if (linkDepth >= MAX_SYMBOLIC_LINK_DEPTH) {
      throw new IOException("too many levels of symbolic links");
    }

    JimfsPath targetPath = link.content();
    return lookup(table.get(Name.SELF), targetPath, FOLLOW_LINKS, linkDepth + 1);
  }

  @Nullable
  private DirectoryTable getDirectoryTable(@Nullable File file) {
    if (file != null && file.isDirectory()) {
      return file.content();
    }

    return null;
  }

  private static Deque<Name> toNames(JimfsPath path) {
    Deque<Name> names = new ArrayDeque<>();
    Iterables.addAll(names, path.allNames());
    return names;
  }
}
