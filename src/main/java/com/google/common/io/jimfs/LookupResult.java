package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Objects;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Result of a file lookup.
 *
 * <p>There are several states a result can have.
 *
 * <ul>
 *   <li>the parent directory that should have contained the file was not found:
 *   {@code isParentFound() == false}</li>
 *   <li>the parent directory that should have contained the file was found, the file was not:
 *   {@code isParentFound() == true}, but {@code isFileFound() == false}</li>
 *   <li>the parent directory and the file itself were found: {@code isFileFound() == true}</li>
 * </ul>
 *
 * @author Colin Decker
 */
final class LookupResult {

  /**
   * Returns a lookup result with neither the parent key nor the file key set.
   */
  public static LookupResult notFound() {
    return new LookupResult(null, null);
  }

  /**
   * Returns a lookup result with only the parent key set.
   */
  public static LookupResult parentFound(FileKey parentKey) {
    return new LookupResult(checkNotNull(parentKey), null);
  }

  /**
   * Returns a successful lookup result with both a parent key and a file key.
   */
  public static LookupResult found(FileKey parentKey, FileKey fileKey) {
    return new LookupResult(checkNotNull(parentKey), checkNotNull(fileKey));
  }

  @Nullable
  private final FileKey parentKey;

  @Nullable
  private final FileKey fileKey;

  private LookupResult(
      @Nullable FileKey parentKey, @Nullable FileKey fileKey) {
    this.parentKey = parentKey;
    this.fileKey = fileKey;
  }

  /**
   * Returns whether or not the file being looked up was found.
   */
  public boolean isFileFound() {
    return fileKey != null;
  }

  /**
   * Throws an exception if the file was not found. Returns this result.
   */
  public LookupResult requireFileFound(Path pathForException) throws NoSuchFileException {
    if (!isFileFound()) {
      throw new NoSuchFileException(pathForException.toString());
    }
    return this;
  }

  /**
   * Throws an exception if the file was found. Returns this result.
   */
  public LookupResult requireNotFound(Path pathForException) throws FileAlreadyExistsException {
    if (isFileFound()) {
      throw new FileAlreadyExistsException(pathForException.toString());
    }
    return this;
  }

  /**
   * Returns whether or not the parent of the file being looked up was found.
   */
  public boolean isParentFound() {
    return parentKey != null;
  }

  /**
   * Throws an exception if the parent dir was not found. Returns this result.
   */
  public LookupResult requireParentFound(Path pathForException) throws NoSuchFileException {
    if (!isParentFound()) {
      throw new NoSuchFileException(pathForException.toString());
    }
    return this;
  }

  /**
   * Gets the key of the parent directory of the path being looked up.
   *
   * @throws IllegalStateException if the result did not
   *     {@linkplain #isParentFound() find a parent key}
   */
  public FileKey getParentKey() {
    checkState(isParentFound(), "parent was not found");
    return parentKey;
  }

  /**
   * Gets the key of the file that was being looked up.
   *
   * @throws IllegalStateException if the result was not {@linkplain #isFileFound() not found}
   */
  public FileKey getFileKey() {
    checkState(isFileFound(), "file was not found");
    return fileKey;
  }

  /**
   * Returns the file key that was found or {@code null} if not found.
   */
  @Nullable
  public FileKey orNull() {
    return fileKey;
  }

  /**
   * Returns the file being looked up.
   *
   * @throws IllegalStateException if the result was not {@linkplain #isFileFound() not found}
   */
  public File getFile(FileStorage storage) {
    return storage.getFile(getFileKey());
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .omitNullValues()
        .add("parentKey", parentKey)
        .add("fileKey", fileKey)
        .toString();
  }
}
