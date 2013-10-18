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
import static com.google.jimfs.internal.LinkOptions.FOLLOW_LINKS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * The tree of directories and files for the file system. Contains the file system root directories
 * and provides the ability to lookup files by path. One piece of the file store implementation.
 *
 * @author Colin Decker
 */
final class FileTree {

  private static final int MAX_SYMBOLIC_LINK_DEPTH = 10;

  private static final ImmutableList<Name> EMPTY_PATH_NAMES = ImmutableList.of(Name.SELF);

  /**
   * Special directory linking root names to root directories.
   */
  private final File superRoot;

  /**
   * Names of the root directories.
   */
  private final ImmutableSortedSet<Name> rootDirectoryNames;

  /**
   * Creates a new file tree with the given super root.
   */
  FileTree(File superRoot) {
    this.superRoot = checkNotNull(superRoot);
    this.rootDirectoryNames = superRoot.asDirectory().snapshot();
  }

  /**
   * Returns the names of the root directories in this tree.
   */
  public ImmutableSortedSet<Name> getRootDirectoryNames() {
    return rootDirectoryNames;
  }

  /**
   * Gets the directory entry for the root with the given name or {@code null} if no such root
   * exists.
   */
  @Nullable
  public DirectoryEntry getRoot(Name name) {
    return superRoot.asDirectory().get(name);
  }

  /**
   * Returns the result of the file lookup for the given path.
   */
  public DirectoryEntry lookup(
      File workingDirectory, JimfsPath path, LinkOptions options) throws IOException {
    checkNotNull(path);
    checkNotNull(options);

    DirectoryEntry result = lookup(workingDirectory, path, options, 0);
    if (result == null) {
      // an intermediate file in the path did not exist or was not a directory
      throw new NoSuchFileException(path.toString());
    }
    return result;
  }

  private DirectoryEntry lookup(
      File dir, JimfsPath path, LinkOptions options, int linkDepth) throws IOException {
    ImmutableList<Name> names = path.names();

    if (path.isAbsolute()) {
      // lookup the root directory
      DirectoryEntry entry = superRoot.asDirectory().get(path.root());
      if (entry == null) {
        // root not found; always return null as no real parent directory exists
        // this prevents new roots from being created in file systems supporting multiple roots
        return null;
      } else if (names.isEmpty()) {
        // root found, no more names to look up
        return entry;
      } else {
        // root found, more names to look up; set dir to the root directory for the path
        dir = entry.file();
      }
    } else if (isEmpty(names)) {
      // set names to the canonical list of names for an empty path (singleton list of ".")
      names = EMPTY_PATH_NAMES;
    }

    return lookup(dir, names, options, linkDepth);
  }

  private boolean isEmpty(ImmutableList<Name> names) {
    return names.isEmpty() || names.size() == 1 && names.get(0).toString().equals("");
  }

  /**
   * Looks up the given names against the given base file. If the file does not exist ({@code dir}
   * is null) or is not a directory, the lookup fails.
   */
  @Nullable
  private DirectoryEntry lookup(@Nullable File dir,
      Iterable<Name> names, LinkOptions options, int linkDepth) throws IOException {
    Iterator<Name> nameIterator = names.iterator();
    Name name = nameIterator.next();
    while (nameIterator.hasNext()) {
      Directory table = toDirectoryTable(dir);
      if (table == null) {
        return null;
      }

      DirectoryEntry entry = table.get(name);
      if (entry == null) {
        return null;
      }

      File file = entry.file();
      if (file.isSymbolicLink()) {
        DirectoryEntry linkResult = followSymbolicLink(dir, file, linkDepth);

        if (linkResult == null) {
          return null;
        }

        dir = linkResult.orNull();
      } else {
        dir = file;
      }

      name = nameIterator.next();
    }

    return lookupLast(dir, name, options, linkDepth);
  }

  /**
   * Looks up the last element of a path.
   */
  @Nullable
  private DirectoryEntry lookupLast(@Nullable File dir,
      Name name, LinkOptions options, int linkDepth) throws IOException {
    Directory table = toDirectoryTable(dir);
    if (table == null) {
      return null;
    }

    DirectoryEntry entry = table.get(name);
    if (entry == null) {
      return new DirectoryEntry(dir, name, null);
    }

    File file = entry.file();
    if (options.isFollowLinks() && file.isSymbolicLink()) {
      return followSymbolicLink(dir, file, linkDepth);
    }

    return getRealEntry(entry);
  }

  /**
   * Returns the directory entry located by the target path of the given symbolic link, resolved
   * relative to the given directory.
   */
  @Nullable
  private DirectoryEntry followSymbolicLink(File dir, File link, int linkDepth) throws IOException {
    if (linkDepth >= MAX_SYMBOLIC_LINK_DEPTH) {
      throw new IOException("too many levels of symbolic links");
    }

    return lookup(dir, link.getTarget(), FOLLOW_LINKS, linkDepth + 1);
  }

  /**
   * Returns the entry for the file in its parent directory. This will be the given entry unless the
   * name for the entry is "." or "..", in which the directory linking to the file is not the file's
   * parent directory. In that case, we know the file must be a directory ("." and ".." can only
   * link to directories), so we can just get the entry that links to the directory in its parent.
   */
  private DirectoryEntry getRealEntry(DirectoryEntry entry) {
    Name name = entry.name();

    if (name.equals(Name.SELF) || name.equals(Name.PARENT)) {
      return entry.file().asDirectory().entry();
    } else {
      return entry;
    }
  }

  @Nullable
  private Directory toDirectoryTable(@Nullable File file) {
    if (file != null && file.isDirectory()) {
      return file.asDirectory();
    }

    return null;
  }
}
