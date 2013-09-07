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

package com.google.jimfs.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.AttributeStore;
import com.google.jimfs.common.IoSupplier;
import com.google.jimfs.internal.file.ArrayByteStore;
import com.google.jimfs.internal.file.DirectoryTable;
import com.google.jimfs.internal.file.File;
import com.google.jimfs.internal.file.FileContent;
import com.google.jimfs.internal.file.TargetPath;
import com.google.jimfs.internal.path.JimfsPath;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

/**
 * Service for creating and copying files as well as reading and setting attributes on them.
 *
 * @author Colin Decker
 */
public final class JimfsFileStore extends FileStore {

  private static final String ALL_ATTRIBUTES = "*";

  private final AtomicLong idGenerator = new AtomicLong();

  private final String name;
  private final AttributeProviderRegistry attributeProviders;

  /** Directory supplier with no extra file attributes. */
  private final Supplier<File> defaultDirectorySupplier = new DirectorySupplier();
  /** Regular file supplier with no extra file attributes. */
  private final Supplier<File> defaultRegularFileSupplier = new RegularFileSupplier();

  /**
   * Creates a new file service using the given providers to handle file attributes.
   */
  public JimfsFileStore(String name, AttributeProvider... providers) {
    this(name, Arrays.asList(providers));
  }

  /**
   * Creates a new file service using the given providers to handle file attributes.
   */
  public JimfsFileStore(String name, Iterable<? extends AttributeProvider> providers) {
    this.name = checkNotNull(name);
    this.attributeProviders = new AttributeProviderRegistry(providers);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String type() {
    return "jimfs";
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public long getTotalSpace() throws IOException {
    return Integer.MAX_VALUE;
  }

  @Override
  public long getUsableSpace() throws IOException {
    return Integer.MAX_VALUE;
  }

  @Override
  public long getUnallocatedSpace() throws IOException {
    return Integer.MAX_VALUE;
  }

  private long nextFileId() {
    return idGenerator.getAndIncrement();
  }

  private File createFile(long id, FileContent content, FileAttribute<?>... attrs) {
    File file = new File(id, content);
    setInitialAttributes(file);
    for (FileAttribute<?> attr : attrs) {
      setAttributeInternal(file, attr.name(), attr.value(), true);
    }
    return file;
  }

  /**
   * Creates a new directory and stores it. Returns the key of the new file.
   */
  public File createDirectory(FileAttribute<?>... attrs) {
    return createFile(nextFileId(), new DirectoryTable(), attrs);
  }

  /**
   * Creates a new regular file and stores it. Returns the key of the new file.
   */
  public File createRegularFile(FileAttribute<?>... attrs) {
    return createFile(nextFileId(), new ArrayByteStore(), attrs);
  }

  /**
   * Creates a new symbolic link referencing the given target path and stores it. Returns the key of
   * the new file.
   */
  public File createSymbolicLink(JimfsPath target, FileAttribute<?>... attrs) {
    return createFile(nextFileId(), new TargetPath(target), attrs);
  }

  /**
   * Creates copies of the given file metadata and content and stores them. Returns the key of the
   * new file.
   */
  public File copy(File file) {
    return createFile(nextFileId(), file.content().copy());
  }

  /**
   * Returns a supplier that creates directories and sets the given attributes.
   */
  public Supplier<File> directorySupplier(FileAttribute<?>... attrs) {
    return attrs.length == 0 ? defaultDirectorySupplier : new DirectorySupplier(attrs);
  }

  /**
   * Returns a supplier that creates a regular files and sets the given attributes.
   */
  public Supplier<File> regularFileSupplier(FileAttribute<?>... attrs) {
    return attrs.length == 0 ? defaultRegularFileSupplier : new RegularFileSupplier(attrs);
  }

  /**
   * Returns a supplier that creates a symbolic links to the given path and sets the given
   * attributes.
   */
  public Supplier<File> symbolicLinkSupplier(JimfsPath target, FileAttribute<?>... attrs) {
    return new SymbolicLinkSupplier(target, attrs);
  }

  /**
   * Implements {@link FileSystem#supportedFileAttributeViews()}.
   */
  public ImmutableSet<String> supportedFileAttributeViews() {
    return attributeProviders.getSupportedViews();
  }

  /**
   * Implements {@link FileStore#supportsFileAttributeView(Class)}.
   */
  public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
    return attributeProviders.getSupportedViewTypes().contains(type);
  }

  @Override
  public boolean supportsFileAttributeView(String name) {
    return supportedFileAttributeViews().contains(name);
  }

  /**
   * Sets all initial attributes for the given file.
   */
  private void setInitialAttributes(File file) {
    for (AttributeProvider provider : attributeProviders.getProviders()) {
      provider.setInitial(file);
    }
  }

  /**
   * Gets the value of the given attribute for the given file. {@code attribute} must be of the form
   * "view:attribute" or "attribute".
   */
  public <V> V getAttribute(File file, String attribute) {
    String view = getViewName(attribute);
    String attr = getSingleAttribute(attribute);
    return getAttribute(file, view, attr);
  }

  /**
   * Gets the value of the given attribute for the given view and file. Neither view nor file may
   * have a ':' character.
   */
  @SuppressWarnings("unchecked")
  public <V> V getAttribute(File file, String view, String attribute) {
    for (AttributeProvider provider : attributeProviders.getProviders(view)) {
      if (provider.isGettable(file, attribute)) {
        return (V) provider.get(file, attribute);
      }
    }

    throw new IllegalArgumentException("attribute not found: " + attribute);
  }

  /**
   * Sets the value of the given attribute to the given value for the given file.
   */
  public void setAttribute(File file, String attribute, Object value) {
    setAttributeInternal(file, attribute, value, false);
  }

  /**
   * Sets the value of the given attribute to the given value for the given view and file.
   */
  public void setAttribute(File file, String view, String attribute, Object value) {
    setAttributeInternal(file, view, attribute, value, false);
  }

  private void setAttributeInternal(File file, String attribute, Object value, boolean create) {
    String view = getViewName(attribute);
    String attr = getSingleAttribute(attribute);
    setAttributeInternal(file, view, attr, value, create);
  }

  private void setAttributeInternal(
      File file, String view, String attribute, Object value, boolean create) {
    for (AttributeProvider provider : attributeProviders.getProviders(view)) {
      if (provider.isSettable(file, attribute)) {
        if (create && !provider.isSettableOnCreate(attribute)) {
          throw new UnsupportedOperationException(
              "cannot set attribute '" + view + ":" + attribute + "' during file creation");
        }

        ImmutableSet<Class<?>> acceptedTypes = provider.acceptedTypes(attribute);
        boolean validType = false;
        for (Class<?> type : acceptedTypes) {
          if (type.isInstance(value)) {
            validType = true;
            break;
          }
        }

        if (validType) {
          provider.set(file, attribute, value);
          return;
        } else {
          Object acceptedTypeMessage = acceptedTypes.size() == 1
              ? acceptedTypes.iterator().next()
              : "one of " + acceptedTypes;
          throw new IllegalArgumentException("invalid type " + value.getClass()
              + " for attribute '" + view + ":" + attribute + "': should be "
              + acceptedTypeMessage);
        }
      }
    }

    throw new IllegalArgumentException("cannot set attribute '" + view + ":" + attribute + "'");
  }

  /**
   * Returns an attribute view of the given type for the given file provider, or {@code null} if the
   * view type is not supported.
   */
  @Nullable
  public <V extends FileAttributeView> V getFileAttributeView(
      IoSupplier<? extends AttributeStore> supplier, Class<V> type) {
    if (supportsFileAttributeView(type)) {
      return attributeProviders.getViewProvider(type).getView(supplier);
    }

    return null;
  }

  /**
   * Implements {@link Files#readAttributes(Path, String, LinkOption...)}.
   */
  public ImmutableMap<String, Object> readAttributes(File file, String attributes) {
    String view = getViewName(attributes);
    List<String> attrs = getAttributeNames(attributes);

    if (attrs.size() > 1 && attrs.contains(ALL_ATTRIBUTES)) {
      // attrs contains * and other attributes
      throw new IllegalArgumentException("invalid attributes: " + attributes);
    }

    Map<String, Object> result = new HashMap<>();
    if (attrs.size() == 1 && attrs.contains(ALL_ATTRIBUTES)) {
      // for 'view:*' format, get all keys for all providers for the view
      for (AttributeProvider provider : attributeProviders.getProviders(view)) {
        provider.readAll(file, result);
      }
    } else {
      // for 'view:attr1,attr2,etc'
      for (String attr : attrs) {
        boolean found = false;
        for (AttributeProvider provider : attributeProviders.getProviders(view)) {
          if (provider.isGettable(file, attr)) {
            result.put(attr, provider.get(file, attr));
            found = true;
            break;
          }
        }

        if (!found) {
          throw new IllegalArgumentException("invalid attribute for view '" + view + "': " + attr);
        }
      }
    }

    return ImmutableMap.copyOf(result);
  }

  /**
   * Returns attributes of the given file as an object of the given type.
   *
   * @throws UnsupportedOperationException if the given attributes type is not supported
   */
  public <A extends BasicFileAttributes> A readAttributes(File file, Class<A> type) {
    if (attributeProviders.getSupportedAttributesTypes().contains(type)) {
      return attributeProviders.getReader(type).read(file);
    }

    throw new UnsupportedOperationException("unsupported attributes type: " + type);
  }

  private static String getViewName(String attribute) {
    int separatorIndex = attribute.indexOf(':');

    if (separatorIndex == -1) {
      return "basic";
    }

    // separator must not be at the start or end of the string or appear more than once
    if (separatorIndex == 0
        || separatorIndex == attribute.length() - 1
        || attribute.indexOf(':', separatorIndex + 1) != -1) {
      throw new IllegalArgumentException("illegal attribute format: " + attribute);
    }

    return attribute.substring(0, separatorIndex);
  }

  private static final Splitter ATTRIBUTE_SPLITTER = Splitter.on(',');

  private static ImmutableList<String> getAttributeNames(String attributes) {
    int separatorIndex = attributes.indexOf(':');
    String attributesPart = attributes.substring(separatorIndex + 1);

    return ImmutableList.copyOf(ATTRIBUTE_SPLITTER.split(attributesPart));
  }

  private static String getSingleAttribute(String attribute) {
    ImmutableList<String> attributeNames = getAttributeNames(attribute);

    if (attributeNames.size() != 1 || ALL_ATTRIBUTES.equals(attributeNames.get(0))) {
      throw new IllegalArgumentException("must specify a single attribute: " + attribute);
    }

    return attributeNames.get(0);
  }

  /**
   * Returns {@code null}. This file store does not support any file store attribute views.
   */
  @Override
  public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
    return null;
  }

  /**
   * Throws {@link UnsupportedOperationException}. This file store does not support any file store
   * attributes.
   */
  @Override
  public Object getAttribute(String attribute) throws IOException {
    throw new UnsupportedOperationException();
  }

  private abstract class FileSupplier implements Supplier<File> {

    protected final FileAttribute<?>[] attrs;

    protected FileSupplier(FileAttribute<?>[] attrs) {
      this.attrs = checkNotNull(attrs);
    }
  }

  private final class DirectorySupplier extends FileSupplier {

    private DirectorySupplier(FileAttribute<?>... attrs) {
      super(attrs);
    }

    @Override
    public File get() {
      return createDirectory(attrs);
    }
  }

  private final class RegularFileSupplier extends FileSupplier {

    private RegularFileSupplier(FileAttribute<?>... attrs) {
      super(attrs);
    }

    @Override
    public File get() {
      return createRegularFile(attrs);
    }
  }

  private final class SymbolicLinkSupplier extends FileSupplier {

    private final JimfsPath target;

    protected SymbolicLinkSupplier(JimfsPath target, FileAttribute<?>... attrs) {
      super(attrs);
      this.target = checkNotNull(target);
    }

    @Override
    public File get() {
      return createSymbolicLink(target, attrs);
    }
  }
}
