package com.google.common.io.jimfs;

import com.google.common.io.jimfs.config.JimfsConfiguration;
import com.google.common.io.jimfs.config.UnixConfiguration;
import com.google.common.io.jimfs.file.FileService;
import com.google.common.io.jimfs.file.FileTree;
import com.google.common.io.jimfs.path.JimfsPath;

import java.io.IOException;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Minimal {@link JimfsFileSystem} implementation for testing that doesn't provide any features
 * other than the ability to create {@link JimfsPath} instances.
 *
 * @author Colin Decker
 */
public final class FakeJimfsFileSystem extends JimfsFileSystem {

  public FakeJimfsFileSystem() {
    this(new JimfsFileSystemProvider(), new UnixConfiguration());
  }

  public FakeJimfsFileSystem(JimfsFileSystemProvider provider, JimfsConfiguration configuration) {
    super(provider, configuration);
  }

  @Override
  public ReadWriteLock lock() {
    return null;
  }

  @Override
  public FileService getFileService() {
    return null;
  }

  @Override
  public FileTree getSuperRootTree() {
    return null;
  }

  @Override
  public FileTree getWorkingDirectoryTree() {
    return null;
  }

  @Override
  public FileTree getFileTree(JimfsPath path) {
    return null;
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    return null;
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
