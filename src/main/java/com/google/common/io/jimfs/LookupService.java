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
  private final FileStorage storage;

  LookupService(FileTree tree) {
    this.tree = tree;
    this.storage = tree.storage();
  }

  /**
   * Looks up the file key for the given absolute path.
   */
  public LookupResult lookup(JimfsPath path, LinkHandling linkHandling) throws IOException {
    checkNotNull(path);
    checkNotNull(linkHandling);

    FileKey baseKey = path.isAbsolute()
        ? tree.getSuperRoot().getBaseKey()
        : tree.getBaseKey();

    tree.readLock().lock();
    try {
      return lookup(baseKey, toNames(path), linkHandling, 0);
    } finally {
      tree.readLock().unlock();
    }
  }

  /**
   * Looks up the file key for the given path.
   */
  private LookupResult lookup(
      FileKey dirKey, JimfsPath path, LinkHandling linkHandling, int linkDepth)
      throws IOException {
    if (path.isAbsolute()) {
      dirKey = tree.getSuperRoot().getBaseKey();
    }

    checkNotNull(linkHandling);
    return lookup(dirKey, toNames(path), linkHandling, linkDepth);
  }

  /**
   * Looks up the given names against the given key. If the key does not resolve to a directory,
   * the lookup fails.
   */
  private LookupResult lookup(FileKey dirKey,
      Deque<String> names, LinkHandling linkHandling, int linkDepth) throws IOException {
    String name = names.removeFirst();
    if (names.isEmpty()) {
      return lookupLast(dirKey, name, linkHandling, linkDepth);
    }

    DirectoryTable table = getDirectoryTable(dirKey);
    if (table == null || !table.containsEntry(name)) {
      return LookupResult.notFound();
    }

    FileKey key = table.get(name);
    if (key.isSymbolicLink()) {
      LookupResult linkResult = followSymbolicLink(table, storage.getFile(key), linkDepth);

      if (!linkResult.isFileFound()) {
        return LookupResult.notFound();
      }

      key = linkResult.getFileKey();
    }

    return lookup(key, names, linkHandling, linkDepth);
  }

  /**
   * Looks up the last element of a path.
   */
  private LookupResult lookupLast(FileKey dirKey,
      String name, LinkHandling linkHandling, int linkDepth) throws IOException {
    DirectoryTable table = getDirectoryTable(dirKey);
    if (table == null) {
      return LookupResult.notFound();
    }

    if (!table.containsEntry(name)) {
      // found the parent, didn't find the last name
      return LookupResult.parentFound(table.key());
    }

    FileKey key = table.get(name);

    if (linkHandling == FOLLOW_LINKS) {
      File file = storage.getFile(key);
      if (file.isSymbolicLink()) {
        // TODO(cgdecker): can add info on the symbolic link and its parent here if needed
        // for now it doesn't seem like it's needed though
        return followSymbolicLink(table, file, linkDepth);
      }
    }

    return LookupResult.found(dirKey, key);
  }

  private LookupResult followSymbolicLink(
      DirectoryTable table, File link, int linkDepth) throws IOException {
    if (linkDepth >= MAX_SYMBOLIC_LINK_DEPTH) {
      throw new IOException("too many levels of symbolic links");
    }

    JimfsPath targetPath = link.content();
    return lookup(table.key(), targetPath, FOLLOW_LINKS, linkDepth + 1);
  }

  @Nullable
  private DirectoryTable getDirectoryTable(FileKey key) {
    if (key.isDirectory() && storage.exists(key)) {
      return storage.getFile(key).content();
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
