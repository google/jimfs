package com.google.common.io.jimfs.file;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.AbstractIterator;
import com.google.common.io.jimfs.path.JimfsPath;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Implementation of {@link DirectoryStream}.
 *
 * @author Colin Decker
 */
class JimfsDirectoryStream implements DirectoryStream<Path> {

  private final FileTree tree;
  private final JimfsPath dirPath;
  private final Filter<? super Path> filter;
  private volatile DirectoryIterator iterator;

  public JimfsDirectoryStream(FileTree tree, JimfsPath dirPath, Filter<? super Path> filter) {
    this.tree = checkNotNull(tree);
    this.dirPath = checkNotNull(dirPath);
    this.filter = checkNotNull(filter);
    this.iterator = new DirectoryIterator();
  }

  /**
   * Returns the file tree used to resolve the path of this stream.
   */
  protected FileTree tree() {
    return tree;
  }

  /**
   * Returns the path for the stream's directory.
   */
  protected JimfsPath path() {
    return dirPath;
  }

  @Override
  public Iterator<Path> iterator() {
    if (iterator == null) {
      throw new IllegalStateException("iterator() has already been called once");
    }
    Iterator<Path> result = iterator;
    iterator = null;
    return result;
  }

  @Override
  public void close() throws IOException {
  }

  private final class DirectoryIterator extends AbstractIterator<Path> {

    @Nullable
    private Iterator<Path> fileNames;

    @Override
    protected Path computeNext() {
      try {
        if (fileNames == null) {
          tree.readLock().lock();
          try {
            fileNames = tree.snapshotEntries(dirPath).iterator();
          } finally {
            tree.readLock().unlock();
          }
        }

        while (fileNames.hasNext()) {
          Path name = fileNames.next();
          Path path = dirPath.resolve(name);

          if (filter.accept(path)) {
            return path;
          }
        }

        return endOfData();
      } catch (IOException e) {
        throw new DirectoryIteratorException(e);
      }
    }
  }

  /**
   * A stream filter that always returns true.
   */
  public static final Filter<Object> ALWAYS_TRUE_FILTER = new Filter<Object>() {
    @Override
    public boolean accept(Object entry) throws IOException {
      return true;
    }
  };
}
