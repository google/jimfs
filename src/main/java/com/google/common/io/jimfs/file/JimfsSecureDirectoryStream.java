package com.google.common.io.jimfs.file;

import static com.google.common.io.jimfs.JimfsFileSystemProvider.getOptionsForChannel;
import static com.google.common.io.jimfs.file.FileTree.DeleteMode;
import static com.google.common.io.jimfs.util.ExceptionHelpers.throwProviderMismatch;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.jimfs.path.JimfsPath;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Set;

/**
 * Secure directory stream implementation that uses a {@link FileTree} as its base.
 *
 * @author Colin Decker
 */
final class JimfsSecureDirectoryStream
    extends JimfsDirectoryStream implements SecureDirectoryStream<Path> {

  public JimfsSecureDirectoryStream(FileTree tree, Filter<? super Path> filter) {
    super(tree, tree.getBasePath(), filter);
  }

  /**
   * Gets the appropriate file tree to use for the given path, which is this stream's base tree
   * if the path is relative and the super root tree if it's absolute.
   */
  private FileTree tree(JimfsPath path) {
    return path.isAbsolute() ? tree().getSuperRoot() : tree();
  }

  @Override
  public SecureDirectoryStream<Path> newDirectoryStream(Path path, LinkOption... options)
      throws IOException {
    JimfsPath checkedPath = checkPath(path);
    return tree(checkedPath).newSecureDirectoryStream(
        checkedPath, ALWAYS_TRUE_FILTER, LinkHandling.fromOptions(options));
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
      FileAttribute<?>... attrs) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    options = getOptionsForChannel(options);
    return tree(checkedPath).getByteStore(checkedPath, options).openFileChannel(options);
  }

  @Override
  public void deleteFile(Path path) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    tree(checkedPath).deleteFile(checkedPath, DeleteMode.NON_DIRECTORY_ONLY);
  }

  @Override
  public void deleteDirectory(Path path) throws IOException {
    JimfsPath checkedPath = checkPath(path);
    tree(checkedPath).deleteFile(checkedPath, DeleteMode.DIRECTORY_ONLY);
  }

  @Override
  public void move(Path srcPath, SecureDirectoryStream<Path> targetDir, Path targetPath)
      throws IOException {
    JimfsPath checkedSrcPath = checkPath(srcPath);
    JimfsPath checkedTargetPath = checkPath(targetPath);

    if (!(targetDir instanceof JimfsSecureDirectoryStream)) {
      throw new ProviderMismatchException(
          "targetDir isn't a secure directory stream associated with this file system");
    }

    JimfsSecureDirectoryStream checkedTargetDir = (JimfsSecureDirectoryStream) targetDir;
    FileTree targetDirTree = checkedTargetDir.tree(checkedTargetPath);

    tree(checkedSrcPath).moveFile(
        checkedSrcPath, targetDirTree, checkedTargetPath, ImmutableSet.<CopyOption>of());
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Class<V> type) {
    return getFileAttributeView(JimfsPath.empty(tree().getFileSystem()), type);
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
      LinkOption... options) {
    JimfsPath checkedPath = checkPath(path);
    return tree().getFileAttributeView(checkedPath, type, LinkHandling.fromOptions(options));
  }

  private static JimfsPath checkPath(Path path) {
    if (path instanceof JimfsPath) {
      return (JimfsPath) path;
    }
    throw throwProviderMismatch(path);
  }
}
