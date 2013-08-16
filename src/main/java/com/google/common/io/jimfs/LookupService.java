package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.jimfs.LinkHandling.FOLLOW_LINKS;

import java.io.IOException;
import java.nio.file.Path;
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
  private LookupResult lookup(File base,
      Deque<String> names, LinkHandling linkHandling, int linkDepth) throws IOException {
    String name = names.removeFirst();
    if (names.isEmpty()) {
      return lookupLast(base, name, linkHandling, linkDepth);
    }

    DirectoryTable table = getDirectoryTable(base);
    if (table == null || !table.containsEntry(name)) {
      return LookupResult.notFound();
    }

    File file = table.get(name);
    if (file.isSymbolicLink()) {
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
      String name, LinkHandling linkHandling, int linkDepth) throws IOException {
    DirectoryTable table = getDirectoryTable(base);
    if (table == null) {
      return LookupResult.notFound();
    }

    if (!table.containsEntry(name)) {
      // found the parent, didn't find the last name
      return LookupResult.parentFound(base);
    }

    File file = table.get(name);

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
    return lookup(table.get(DirectoryTable.SELF), targetPath, FOLLOW_LINKS, linkDepth + 1);
  }

  @Nullable
  private DirectoryTable getDirectoryTable(File file) {
    if (file.isDirectory()) {
      return file.content();
    }

    return null;
  }

  private static Deque<String> toNames(JimfsPath path) {
    Deque<String> names = new ArrayDeque<>();
    if (path.isAbsolute()) {
      names.add(path.getRoot().toString());
    }
    for (Path name : path) {
      names.add(name.toString());
    }
    return names;
  }
}
