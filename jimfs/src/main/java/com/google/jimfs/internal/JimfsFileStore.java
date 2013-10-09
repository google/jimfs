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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.jimfs.Jimfs;
import com.google.jimfs.attribute.AttributeStore;
import com.google.jimfs.common.IoSupplier;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nullable;

/**
 * {@link FileStore} implementation which provides methods for file creation, lookup and attribute
 * handling.
 *
 * <p>Most of these methods are actually implemented in another class: {@link FileTree} for lookup,
 * {@link FileFactory} for creating and copying files and {@link AttributeService} for attribute
 * handling. This class merely provides a single API through which to access the functionality of
 * those classes.
 *
 * @author Colin Decker
 */
final class JimfsFileStore extends FileStore {

  private final FileTree tree;
  private final RegularFileStorage storage;
  private final AttributeService attributes;
  private final FileFactory factory;

  private final ImmutableSet<Jimfs.Feature> supportedFeatures;

  private final Lock readLock;
  private final Lock writeLock;

  public JimfsFileStore(FileTree tree, FileFactory factory, RegularFileStorage storage,
      AttributeService attributes, Set<Jimfs.Feature> supportedFeatures) {
    this.tree = checkNotNull(tree);
    this.factory = checkNotNull(factory);
    this.storage = checkNotNull(storage);
    this.attributes = checkNotNull(attributes);
    this.supportedFeatures = ImmutableSet.copyOf(supportedFeatures);

    ReadWriteLock lock = new ReentrantReadWriteLock();
    this.readLock = lock.readLock();
    this.writeLock = lock.writeLock();
  }

  // internal use methods

  /**
   * Returns the read lock for this store.
   */
  Lock readLock() {
    return readLock;
  }

  /**
   * Returns the write lock for this store.
   */
  Lock writeLock() {
    return writeLock;
  }

  /**
   * Returns the names of the root directories in this store.
   */
  ImmutableSortedSet<Name> getRootDirectoryNames() {
    return tree.getRootDirectoryNames();
  }

  /**
   * Returns whether or not the given feature is supported.
   */
  boolean supports(Jimfs.Feature feature) {
    return supportedFeatures.contains(feature);
  }

  /**
   * Checks that the given feature is supported.
   *
   * @throws UnsupportedOperationException if the feature is not supported
   */
  void checkSupported(Jimfs.Feature feature) {
    if (!supports(feature)) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Returns the root directory with the given name or {@code null} if no such directory exists.
   */
  @Nullable
  File getRoot(Name name) {
    DirectoryEntry entry = tree.getRoot(name);
    return entry == null ? null : entry.file();
  }

  /**
   * Looks up the file at the given path using the given link options. If the path is relative, the
   * lookup is relative to the given working directory.
   *
   * @throws NoSuchFileException if an element of the path other than the final element does not
   *     resolve to a directory or symbolic link (e.g. it doesn't exist or is a regular file)
   * @throws IOException if a symbolic link cycle is detected or the depth of symbolic link
   *    recursion otherwise exceeds a threshold
   */
  DirectoryEntry lookup(
      File workingDirectory, JimfsPath path, LinkOptions options) throws IOException {
    return tree.lookup(workingDirectory, path, options);
  }

  /**
   * Returns a supplier that creates a new regular file.
   */
  Supplier<File> createRegularFile() {
    return factory.regularFileSupplier();
  }

  /**
   * Returns a supplier that creates a new directory.
   */
  Supplier<File> createDirectory() {
    return factory.directorySupplier();
  }

  /**
   * Returns a supplier that creates a new symbolic link with the given target.
   */
  Supplier<File> createSymbolicLink(JimfsPath target) {
    return factory.symbolicLinkSupplier(target);
  }

  /**
   * Creates a copy of the given file, copying its attributes as well if copy attributes is true.
   * Returns the copy.
   */
  File copy(File file, boolean copyAttributes) {
    File copy = factory.copy(file);
    setInitialAttributes(copy);
    if (copyAttributes) {
      attributes.copyAttributes(file, copy);
    }
    return copy;
  }

  /**
   * Sets initial attributes on the given file, including the given attributes if possible.
   */
  void setInitialAttributes(File file, FileAttribute<?>... attrs) {
    attributes.setInitialAttributes(file, attrs);
  }

  /**
   * Copies the basic attributes (just file times) of the given file to the given copy file.
   */
  void copyBasicAttributes(File file, File copy) {
    attributes.copyBasicAttributes(file, copy);
  }

  /**
   * Returns an attribute view of the given type for the given file supplier, or {@code null} if the
   * view type is not supported.
   */
  @Nullable
  <V extends FileAttributeView> V getFileAttributeView(
      IoSupplier<? extends AttributeStore> supplier, Class<V> type) {
    return attributes.getFileAttributeView(supplier, type);
  }

  /**
   * Returns a map containing the attributes described by the given string mapped to their values.
   */
  ImmutableMap<String, Object> readAttributes(File file, String attributes) {
    return this.attributes.readAttributes(file, attributes);
  }

  /**
   * Returns attributes of the given file as an object of the given type.
   *
   * @throws UnsupportedOperationException if the given attributes type is not supported
   */
  <A extends BasicFileAttributes> A readAttributes(File file, Class<A> type) {
    return attributes.readAttributes(file, type);
  }

  /**
   * Sets the given attribute to the given value for the given file.
   */
  void setAttribute(File file, String attribute, Object value) {
    attributes.setAttribute(file, attribute, value);
  }

  /**
   * Returns the file attribute views supported by this store.
   */
  ImmutableSet<String> supportedFileAttributeViews() {
    return attributes.supportedFileAttributeViews();
  }

  // methods implementing the FileStore API

  @Override
  public String name() {
    return "jimfs";
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
    return storage.getTotalSpace();
  }

  @Override
  public long getUsableSpace() throws IOException {
    return getTotalSpace();
  }

  @Override
  public long getUnallocatedSpace() throws IOException {
    return storage.getUnallocatedSpace();
  }

  @Override
  public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
    return attributes.supportsFileAttributeView(type);
  }

  @Override
  public boolean supportsFileAttributeView(String name) {
    return attributes.supportedFileAttributeViews().contains(name);
  }

  @Override
  public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
    return null; // no supported views
  }

  @Override
  public Object getAttribute(String attribute) throws IOException {
    throw new UnsupportedOperationException();
  }
}
