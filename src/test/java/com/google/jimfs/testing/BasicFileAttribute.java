package com.google.jimfs.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.attribute.FileAttribute;

/**
 * @author Colin Decker
 */
public class BasicFileAttribute<T> implements FileAttribute<T> {

  private final String name;
  private final T value;

  public BasicFileAttribute(String name, T value) {
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
