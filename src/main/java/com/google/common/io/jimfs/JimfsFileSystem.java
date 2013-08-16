package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.jimfs.JimfsConfiguration.Feature;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Colin Decker
 */
final class JimfsFileSystem extends FileSystem {

  private final FileStorage storage;

  private final JimfsFileSystemProvider provider;
  private final JimfsConfiguration configuration;

  private final ImmutableSet<Path> rootPaths;
  private final ImmutableSet<FileKey> rootKeys;

  private final FileTree superRoot;
  private final FileTree workingDirectory;

  private final UserLookupService userLookup;

  public JimfsFileSystem(
      JimfsFileSystemProvider provider, JimfsConfiguration configuration) {
    this.provider = checkNotNull(provider);
    this.configuration = checkNotNull(configuration);
    this.storage = new FileStorage(
        configuration.getAttributeManager(), configuration.isLookupCaseSensitive());

    this.rootPaths = createRootPaths(configuration.getRoots());

    this.superRoot = new FileTree(this, storage.createDirectory(), JimfsPath.empty(this));
    this.rootKeys = createRootDirectories();

    this.userLookup = new UserLookupService(configuration.supportsFeature(Feature.GROUPS));

    JimfsPath workingDirPath = configuration
        .parsePath(this, Lists.newArrayList(configuration.getWorkingDirectory()));
    this.workingDirectory = new FileTree(
        this, createWorkingDirectory(workingDirPath), workingDirPath);
  }

  private ImmutableSet<Path> createRootPaths(Iterable<String> roots) {
    ImmutableSet.Builder<Path> rootPathsBuilder = ImmutableSet.builder();
    for (String root : roots) {
      rootPathsBuilder.add(getPath(root));
    }
    return rootPathsBuilder.build();
  }

  private ImmutableSet<FileKey> createRootDirectories() {
    try {
      ImmutableSet.Builder<FileKey> builder = ImmutableSet.builder();
      for (Path root : rootPaths) {
        FileKey rootKey = superRoot.createFile(
            (JimfsPath) root, storage.directoryFactory(), false);
        builder.add(rootKey);

        // change root directory's ".." to point to itself
        File rootDir = storage.getFile(rootKey);
        DirectoryTable rootDirTable = rootDir.content();
        rootDirTable.unlinkParent();
        rootDirTable.linkParent(rootKey);
      }
      return builder.build();
    } catch (IOException e) {
      throw new RuntimeException("failed to create root directories", e);
    }
  }

  private FileKey createWorkingDirectory(JimfsPath workingDir) {
    try {
      JimfsPath currentPath = workingDir.getRoot();
      FileKey newDirKey = null;
      for (Path name : workingDir) {
        currentPath = currentPath.resolve(name);
        newDirKey = superRoot.createFile(currentPath, storage.directoryFactory(), false);
      }

      if (newDirKey == null) {
        // no directories created; working directory is a root directory
        File file = superRoot.lookupFile(currentPath, LinkHandling.NOFOLLOW_LINKS);
        assert file != null;
        return file.key();
      }

      // at least one new directory was created
      return newDirKey;
    } catch (IOException e) {
      throw new RuntimeException("failed to create working directory", e);
    }
  }

  @Override
  public JimfsFileSystemProvider provider() {
    return provider;
  }

  /**
   * Returns the configuration for this file system.
   */
  public JimfsConfiguration configuration() {
    return configuration;
  }

  /**
   * Returns the file storage for this file system.
   */
  public FileStorage getFileStorage() {
    return storage;
  }

  /**
   * Returns the super root for this file system.
   */
  public FileTree getSuperRoot() {
    return superRoot;
  }

  /**
   * Returns the working directory tree for this file system.
   */
  public FileTree getWorkingDirectory() {
    return workingDirectory;
  }

  /**
   * Returns the file tree to use for operations on the given path. For absolute paths, the
   * super root is returned. For relative paths, the working directory is returned.
   */
  public FileTree getFileTree(JimfsPath path) {
    return path.isAbsolute() ? superRoot : workingDirectory;
  }

  /**
   * Returns the attribute manager for the file system, which provides methods for reading and
   * setting file attributes and getting attribute views.
   */
  public AttributeManager getAttributeManager() {
    return configuration.getAttributeManager();
  }

  /**
   * Returns the set of file keys for the root directories of this file system.
   */
  public ImmutableSet<FileKey> getRootKeys() {
    return rootKeys;
  }

  @Override
  public String getSeparator() {
    return configuration.getSeparator();
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    return rootPaths;
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return ImmutableList.of();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return getAttributeManager().supportedFileAttributeViews();
  }

  @Override
  public JimfsPath getPath(String first, String... more) {
    List<String> parts = new ArrayList<>();
    for (String s : Lists.asList(first, more)) {
      if (!s.isEmpty()) {
        parts.add(s);
      }
    }
    return configuration.parsePath(this, parts);
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    return PathMatchers.getPathMatcher(syntaxAndPattern, configuration.getRecognizedSeparators());
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    return userLookup;
  }

  @Override
  public WatchService newWatchService() throws IOException {
    return null;
  }

  /**
   * Returns {@code false}; currently, cannot create a read-only file system.
   *
   * @return {@code false}, always
   */
  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public void close() throws IOException {
  }
}
