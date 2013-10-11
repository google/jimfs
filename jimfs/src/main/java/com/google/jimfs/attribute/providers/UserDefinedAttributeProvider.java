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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AbstractAttributeView;
import com.google.jimfs.attribute.AttributeViewProvider;
import com.google.jimfs.attribute.FileMetadata;
import com.google.jimfs.attribute.FileMetadataSupplier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.List;
import java.util.Map;

/**
 * Attribute provider that provides the {@link UserDefinedFileAttributeView} ("user"). Unlike most
 * other attribute providers, this one has no pre-defined set of attributes. Rather, it allows
 * arbitrary user defined attributes to be set (as {@code ByteBuffer} or {@code byte[]}) and read
 * (as {@code byte[]}).
 *
 * @author Colin Decker
 */
public final class UserDefinedAttributeProvider
    implements AttributeViewProvider<UserDefinedFileAttributeView> {

  /**
   * The singleton instance of {@link UserDefinedAttributeProvider}.
   */
  public static final UserDefinedAttributeProvider INSTANCE = new UserDefinedAttributeProvider();

  public static final String VIEW = "user";

  private UserDefinedAttributeProvider() {}

  @Override
  public String name() {
    return VIEW;
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of();
  }

  @Override
  public void readAll(FileMetadata metadata, Map<String, Object> map) {
    for (String attribute : metadata.getAttributeKeys()) {
      if (attribute.startsWith("user:")) {
        String attributeName = attribute.substring(5);
        map.put(attributeName, get(metadata, attributeName));
      }
    }
  }

  private ImmutableList<String> userDefinedAttributes(FileMetadata store) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String attribute : store.getAttributeKeys()) {
      if (attribute.startsWith("user:")) {
        builder.add(attribute.substring(5));
      }
    }
    return builder.build();
  }

  @Override
  public void setInitial(FileMetadata metadata) {
  }

  @Override
  public boolean isGettable(FileMetadata metadata, String attribute) {
    return metadata.getAttribute(name() + ":" + attribute) != null;
  }

  @Override
  public Object get(FileMetadata metadata, String attribute) {
    byte[] bytes = (byte[]) metadata.getAttribute(name() + ":" + attribute);
    return bytes.clone();
  }

  @Override
  public ImmutableSet<Class<?>> acceptedTypes(String attribute) {
    return ImmutableSet.of(ByteBuffer.class, byte[].class);
  }

  @Override
  public boolean isSettable(FileMetadata metadata, String attribute) {
    return true;
  }

  @Override
  public boolean isSettableOnCreate(String attribute) {
    return false;
  }

  @Override
  public void set(FileMetadata metadata, String attribute, Object value) {
    byte[] bytes;
    if (value instanceof byte[]) {
      bytes = ((byte[]) value).clone();
    } else {
      // value instanceof ByteBuffer
      ByteBuffer buffer = (ByteBuffer) value;
      bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
    }

    metadata.setAttribute(name() + ":" + attribute, bytes);
  }

  @Override
  public Class<UserDefinedFileAttributeView> viewType() {
    return UserDefinedFileAttributeView.class;
  }

  @Override
  public UserDefinedFileAttributeView getView(FileMetadataSupplier supplier) {
    return new View(this, supplier);
  }

  /**
   * Implementation of {@link UserDefinedFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements UserDefinedFileAttributeView {

    public View(UserDefinedAttributeProvider attributeProvider, FileMetadataSupplier supplier) {
      super(attributeProvider, supplier);
    }

    @Override
    public List<String> list() throws IOException {
      return ((UserDefinedAttributeProvider) provider()).userDefinedAttributes(getFileMetadata());
    }

    private byte[] getStoredBytes(String name) throws IOException {
      byte[] bytes = (byte[]) getFileMetadata().getAttribute(name() + ":" + name);
      if (bytes == null) {
        throw new IllegalArgumentException("attribute '" + name() + ":" + name + "' is not set");
      }
      return bytes;
    }

    @Override
    public int size(String name) throws IOException {
      return getStoredBytes(name).length;
    }

    @Override
    public int read(String name, ByteBuffer dst) throws IOException {
      byte[] bytes = getStoredBytes(name);
      dst.put(bytes);
      return bytes.length;
    }

    @Override
    public int write(String name, ByteBuffer src) throws IOException {
      byte[] bytes = new byte[src.remaining()];
      src.get(bytes);
      getFileMetadata().setAttribute(name() + ":" + name, bytes);
      return bytes.length;
    }

    @Override
    public void delete(String name) throws IOException {
      getFileMetadata().deleteAttribute(name() + ":" + name);
    }
  }
}
