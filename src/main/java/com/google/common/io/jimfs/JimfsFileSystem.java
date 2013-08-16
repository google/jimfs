package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.jimfs.JimfsConfiguration.Feature;
import static com.google.common.io.jimfs.LinkHandling.NOFOLLOW_LINKS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Colin Decker
 */
final class JimfsFileSystem extends FileSystem {

  private final FileService fileService;
  private final JimfsFileSystemProvider provider;
  private final JimfsConfiguration configuration;
  private final ImmutableSet<Path> roots;
  private final FileTree superRoot;
  private final FileTree workingDirectory;
  private final UserLookupService userLookup;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public JimfsFileSystem(
      JimfsFileSystemProvider provider, JimfsConfiguration configuration) {
    this.provider = checkNotNull(provider);
    this.configuration = checkNotNull(configuration);
    this.fileService = new FileService(
        configuration.getAttributeService(), configuration.areNamesCaseSensitive());

    this.roots = createRootPaths(configuration.getRoots());

    this.superRoot = new FileTree(this, fileService.createDirectory(), JimfsPath.empty(this));
    createRootDirectories();

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

  private void createRootDirectories() {
    try {
      for (Path root : roots) {
        File rootDir = superRoot.createFile(
            (JimfsPath) root, fileService.directoryCallback(), false);

        // change root directory's ".." to point to itself
        DirectoryTable rootDirTable = rootDir.content();
        rootDirTable.unlinkParent();
        rootDirTable.linkParent(rootDir);
      }
    } catch (IOException e) {
      throw new RuntimeException("failed to create root directories", e);
    }
  }

  private File createWorkingDirectory(JimfsPath workingDir) {
    try {
      Files.createDirectories(workingDir);
      return superRoot.lookupFile(workingDir, NOFOLLOW_LINKS);
    } catch (IOException e) {
      throw new RuntimeException("failed to create working dir", e);
    }
  }

  /**
   * Returns the file system's read lock.
   */
  public Lock readLock() {
    return lock.readLock();
  }

  /**
   * Returns the file system's write lock.
   */
  public Lock writeLock() {
    return lock.writeLock();
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
   * Returns the file fileService for this file system.
   */
  public FileService getFileService() {
    return fileService;
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
   * Returns the attribute service for the file system, which provides methods for reading and
   * setting file attributes and getting attribute views.
   */
  public AttributeService getAttributeService() {
    return configuration.getAttributeService();
  }

  @Override
  public String getSeparator() {
    return configuration.getSeparator();
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    return roots;
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return ImmutableList.of();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return getAttributeService().supportedFileAttributeViews();
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
