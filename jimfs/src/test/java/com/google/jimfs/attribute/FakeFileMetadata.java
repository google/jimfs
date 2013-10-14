package com.google.jimfs.attribute;

/**
 * @author Colin Decker
 */
public class FakeFileMetadata extends FileMetadata {

  private final boolean directory;
  private final boolean regularFile;
  private final boolean symbolicLink;
  private final long size;

  public FakeFileMetadata(long id) {
    this(id, true, false, false, 0);
  }

  public FakeFileMetadata(long id,
      boolean directory, boolean regularFile, boolean symbolicLink, long size) {
    super(id);
    this.directory = directory;
    this.regularFile = regularFile;
    this.symbolicLink = symbolicLink;
    this.size = size;
  }

  @Override
  public boolean isDirectory() {
    return directory;
  }

  @Override
  public boolean isRegularFile() {
    return regularFile;
  }

  @Override
  public boolean isSymbolicLink() {
    return symbolicLink;
  }

  @Override
  public long size() {
    return size;
  }
}
