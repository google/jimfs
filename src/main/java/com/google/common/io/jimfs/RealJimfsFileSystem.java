package com.google.common.io.jimfs;

import static com.google.common.io.jimfs.config.JimfsConfiguration.Feature.GROUPS;
import static com.google.common.io.jimfs.file.LinkHandling.NOFOLLOW_LINKS;

import com.google.common.io.jimfs.attribute.UserLookupService;
import com.google.common.io.jimfs.config.JimfsConfiguration;
import com.google.common.io.jimfs.file.DirectoryTable;
import com.google.common.io.jimfs.file.File;
import com.google.common.io.jimfs.file.FileService;
import com.google.common.io.jimfs.file.FileTree;
import com.google.common.io.jimfs.path.JimfsPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of {@link JimfsFileSystem} that actually provides the full set of file system
 * services.
 *
 * @author Colin Decker
 */
final class RealJimfsFileSystem extends JimfsFileSystem {

  private final FileService fileService;
  private final UserPrincipalLookupService userLookupService;

  private final FileTree superRoot;

  private final FileTree workingDirectory;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  RealJimfsFileSystem(JimfsFileSystemProvider provider,
      JimfsConfiguration configuration) {
    super(provider, configuration);

    this.fileService = new FileService(configuration.getAttributeProviders());
    this.userLookupService = new UserLookupService(configuration.supportsFeature(GROUPS));

    // this FileTree becomes the super root because the super root field is not yet set when
    // it is created... a little hacky, but it works
    this.superRoot = new FileTree(
        this, fileService.createDirectory(), JimfsPath.empty(this));

    createRootDirectories(getRootDirectories());

    this.workingDirectory = new FileTree(
        this, createWorkingDirectory(getWorkingDirectory()), getWorkingDirectory());
  }

  private void createRootDirectories(Iterable<Path> roots) {
    try {
      for (Path root : roots) {
        File rootDir = superRoot.createFile(
            (JimfsPath) root, fileService.directoryCreator(), false);

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

  @Override
  public ReadWriteLock lock() {
    return lock;
  }

  @Override
  public FileService getFileService() {
    return fileService;
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    return userLookupService;
  }

  @Override
  public FileTree getSuperRootTree() {
    return superRoot;
  }

  @Override
  public FileTree getWorkingDirectoryTree() {
    return workingDirectory;
  }

  @Override
  public FileTree getFileTree(JimfsPath path) {
    return path.isAbsolute() ? superRoot : workingDirectory;
  }

  @Override
  public WatchService newWatchService() throws IOException {
    return null;
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public void close() throws IOException {
  }
}
