package com.google.jimfs.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.attribute.FileAttribute;

/**
 * Simple implementation of {@link FileAttribute}.
 *
 * @author Colin Decker
 */
final class SimpleFileAttribute<T> implements FileAttribute<T> {

  private final String name;
  private final T value;

  SimpleFileAttribute(String name, T value) {
    this.name = checkNotNull(name);
    this.value = checkNotNull(value);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public T value() {
    return value;
  }
}
