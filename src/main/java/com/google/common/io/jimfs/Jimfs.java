package com.google.common.io.jimfs;

import com.google.common.io.jimfs.config.UnixConfiguration;
import com.google.common.io.jimfs.config.WindowsConfiguration;

import java.nio.file.FileSystem;

/**
 * Static factory methods for JIMFS file systems.
 *
 * @author Colin Decker
 */
public final class Jimfs {

  private Jimfs() {}

  private static final JimfsFileSystemProvider PROVIDER = new JimfsFileSystemProvider();

  /**
   * Returns a new in-memory file system with semantics similar to UNIX.
   *
   * <p>The returned file system has a single root, "/", and uses "/" as a separator. It supports
   * symbolic and hard links. File lookup is case-sensitive. The supported file attribute views
   * are "basic", "owner", "posix" and "unix".
   *
   * <p>The working directory for the file system, which exists when it is created, is "/work".
   */
  public static FileSystem newUnixLikeFileSystem() {
    return new JimfsFileSystem(
        PROVIDER, new UnixConfiguration());
  }

  /**
   * Returns a new in-memory file system with semantics similar to Windows.
   *
   * <p>The returned file system has a single root, "C:\", and uses "\" as a separator. It also
   * recognizes "/" as a separator when parsing paths. It supports symbolic and hard links. File
   * lookup is not case-sensitive. The supported file attribute views are "basic", "owner", "dos",
   * "acl" and "user".
   *
   * <p>The working directory for the file system, which exists when it is created, is "C:\work".
   */
  public static FileSystem newWindowsLikeFileSystem() {
    return new JimfsFileSystem(
        PROVIDER, new WindowsConfiguration());
  }
}
