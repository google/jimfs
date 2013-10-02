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
 * The tree of directories and files for the file system.
 *
 * @author Colin Decker
 */
final class FileTree {

  private static final int MAX_SYMBOLIC_LINK_DEPTH = 10;

  private final File superRoot;
  private final ImmutableSortedSet<Name> rootDirectoryNames;

  /**
   * Creates a new file tree with the given super root.
   */
  FileTree(File superRoot) {
    this.superRoot = checkNotNull(superRoot);

    DirectoryTable superRootTable = superRoot.content();
    this.rootDirectoryNames = superRootTable.snapshot();
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
    DirectoryTable superRootTable = superRoot.content();
    return superRootTable.getEntry(name);
  }

  /**
   * Returns the result of the file lookup for the given path.
   */
  public DirectoryEntry lookup(
      File workingDirectory, JimfsPath path, LinkOptions options) throws IOException {
    checkNotNull(path);
    checkNotNull(options);

    File base;
    Iterable<Name> names = path.path();
    if (path.isAbsolute()) {
      base = superRoot;
    } else {
      base = workingDirectory;
      if (isEmpty(path)) {
        // empty path is equivalent to "." in a lookup
        names = ImmutableList.of(Name.SELF);
      }
    }

    DirectoryEntry result = lookup(base, names, options, 0);
    if (result == null) {
      throw new NoSuchFileException(path.toString());
    }
    return result;
  }

  private DirectoryEntry lookup(
      File dir, JimfsPath path, LinkOptions options, int linkDepth) throws IOException {
    Iterable<Name> names = path.path();
    if (path.isAbsolute()) {
      dir = superRoot;
    } else if (isEmpty(path)) {
      // empty path is equivalent to "." in a lookup
      names = ImmutableList.of(Name.SELF);
    }

    return lookup(dir, names, options, linkDepth);
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
      DirectoryTable table = getDirectoryTable(dir);
      if (table == null) {
        return null;
      }

      DirectoryEntry entry = table.getEntry(name);
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
    DirectoryTable table = getDirectoryTable(dir);
    if (table == null) {
      return null;
    }

    DirectoryEntry entry = table.getEntry(name);
    if (entry == null) {
      return new DirectoryEntry(dir, name, null);
    }

    File file = entry.file();
    if (options.isFollowLinks() && file.isSymbolicLink()) {
      return followSymbolicLink(dir, file, linkDepth);
    }

    return getRealEntry(entry);
  }

  private DirectoryEntry followSymbolicLink(File dir, File link, int linkDepth) throws IOException {
    if (linkDepth >= MAX_SYMBOLIC_LINK_DEPTH) {
      throw new IOException("too many levels of symbolic links");
    }

    JimfsPath targetPath = link.content();
    return lookup(dir, targetPath, FOLLOW_LINKS, linkDepth + 1);
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
      DirectoryTable table = entry.file().content();
      return table.entry();
    } else {
      return entry;
    }
  }

  @Nullable
  private DirectoryTable getDirectoryTable(@Nullable File file) {
    if (file != null && file.isDirectory()) {
      return file.content();
    }

    return null;
  }

  /**
   * Returns true if path has no root component (is not absolute) and either has no name components
   * or only has a single name component, the empty string.
   */
  private static boolean isEmpty(JimfsPath path) {
    return !path.isAbsolute() && (path.getNameCount() == 0
        || path.getNameCount() == 1 && path.getName(0).toString().equals(""));
  }
}
