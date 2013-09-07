/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jimfs.attribute.providers;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AbstractAttributeProvider;
import com.google.jimfs.attribute.AbstractAttributeView;
import com.google.jimfs.attribute.AttributeReader;
import com.google.jimfs.attribute.AttributeSpec;
import com.google.jimfs.attribute.AttributeStore;
import com.google.jimfs.attribute.AttributeViewProvider;
import com.google.jimfs.common.IoSupplier;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;

/**
 * Attribute provider that provides the {@link DosFileAttributeView} ("dos") and allows the reading
 * of {@link DosFileAttributes}.
 *
 * @author Colin Decker
 */
public final class DosAttributeProvider extends AbstractAttributeProvider implements
    AttributeViewProvider<DosFileAttributeView>, AttributeReader<DosFileAttributes> {

  public static final String READ_ONLY = "readonly";
  public static final String HIDDEN = "hidden";
  public static final String ARCHIVE = "archive";
  public static final String SYSTEM = "system";

  private static final ImmutableSet<AttributeSpec> ATTRIBUTES = ImmutableSet.of(
      AttributeSpec.settable(READ_ONLY, Boolean.class),
      AttributeSpec.settable(HIDDEN, Boolean.class),
      AttributeSpec.settable(ARCHIVE, Boolean.class),
      AttributeSpec.settable(SYSTEM, Boolean.class));

  private final BasicAttributeProvider basic;

  public DosAttributeProvider(BasicAttributeProvider basic) {
    super(ATTRIBUTES);
    this.basic = checkNotNull(basic);
  }

  @Override
  public String name() {
    return "dos";
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of("basic", "owner");
  }

  @Override
  public void setInitial(AttributeStore store) {
    set(store, READ_ONLY, false);
    set(store, HIDDEN, false);
    set(store, ARCHIVE, false);
    set(store, SYSTEM, false);
  }

  @Override
  public Class<DosFileAttributeView> viewType() {
    return DosFileAttributeView.class;
  }

  @Override
  public DosFileAttributeView getView(IoSupplier<? extends AttributeStore> supplier) {
    return new View(this, supplier);
  }

  @Override
  public Class<DosFileAttributes> attributesType() {
    return DosFileAttributes.class;
  }

  @Override
  public DosFileAttributes read(AttributeStore store) {
    try {
      return getView(IoSupplier.of(store)).readAttributes();
    } catch (IOException e) {
      throw new AssertionError(e); // IoSupplier.of doesn't throw IOException
    }
  }

  /**
   * Implementation of {@link DosFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements DosFileAttributeView {

    private final BasicFileAttributeView basicView;

    public View(
        DosAttributeProvider attributeProvider, IoSupplier<? extends AttributeStore> supplier) {
      super(attributeProvider, supplier);
      this.basicView = attributeProvider.basic.getView(supplier);
    }

    @Override
    public DosFileAttributes readAttributes() throws IOException {
      return new Attributes(basicView.readAttributes(), this);
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
        throws IOException {
      basicView.setTimes(lastModifiedTime, lastAccessTime, createTime);
    }

    @Override
    public void setReadOnly(boolean value) throws IOException {
      set(READ_ONLY, value);
    }

    @Override
    public void setHidden(boolean value) throws IOException {
      set(HIDDEN, value);
    }

    @Override
    public void setSystem(boolean value) throws IOException {
      set(SYSTEM, value);
    }

    @Override
    public void setArchive(boolean value) throws IOException {
      set(ARCHIVE, value);
    }
  }

  /**
   * Implementation of {@link DosFileAttributes}.
   */
  public static class Attributes extends BasicAttributeProvider.Attributes implements DosFileAttributes {

    private final boolean readOnly;
    private final boolean hidden;
    private final boolean archive;
    private final boolean system;

    protected Attributes(BasicFileAttributes attributes, View view) throws IOException {
      super(attributes);
      this.readOnly = view.get(READ_ONLY);
      this.hidden = view.get(HIDDEN);
      this.archive = view.get(ARCHIVE);
      this.system = view.get(SYSTEM);
    }

    @Override
    public boolean isReadOnly() {
      return readOnly;
    }

    @Override
    public boolean isHidden() {
      return hidden;
    }

    @Override
    public boolean isArchive() {
      return archive;
    }

    @Override
    public boolean isSystem() {
      return system;
    }
  }
}
