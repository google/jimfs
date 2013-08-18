package com.google.common.io.jimfs.file;

/**
 * {@link FileContent} implementation that does nothing.
 *
 * @author Colin Decker
 */
public final class FakeFileContent implements FileContent {

  @Override
  public FileContent copy() {
    return new FakeFileContent();
  }

  @Override
  public int size() {
    return 0;
  }
}
