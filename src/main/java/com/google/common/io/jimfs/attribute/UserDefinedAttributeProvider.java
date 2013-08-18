/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

package com.google.common.io.jimfs.attribute;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.jimfs.file.File;
import com.google.common.io.jimfs.file.FileProvider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.List;

/**
 * Attribute provider that provides the {@link UserDefinedFileAttributeView} ("user"). Unlike most
 * other attribute providers, this one has no pre-defined set of attributes. Rather, it allows
 * arbitrary user defined attributes to be set (as {@code ByteBuffer} or {@code byte[]}) and read
 * (as {@code byte[]}).
 *
 * @author Colin Decker
 */
public class UserDefinedAttributeProvider implements AttributeProvider,
    AttributeViewProvider<UserDefinedFileAttributeView> {

  @Override
  public String name() {
    return "user";
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of();
  }

  @Override
  public void readAll(File file, ImmutableMap.Builder<String, Object> builder) {
    for (String attribute : file.getAttributeKeys()) {
      if (attribute.startsWith("user:")) {
        builder.put(attribute.substring(5), get(file, attribute));
      }
    }
  }

  private ImmutableList<String> userDefinedAttributes(File file) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String attribute : file.getAttributeKeys()) {
      if (attribute.startsWith("user:")) {
        builder.add(attribute.substring(5));
      }
    }
    return builder.build();
  }

  @Override
  public void setInitial(File file) {
  }

  @Override
  public boolean isGettable(File file, String attribute) {
    return file.getAttribute(name() + ":" + attribute) != null;
  }

  @Override
  public Object get(File file, String attribute) {
    byte[] bytes = (byte[]) file.getAttribute(name() + ":" + attribute);
    return bytes.clone();
  }

  @Override
  public ImmutableSet<Class<?>> acceptedTypes(String attribute) {
    return ImmutableSet.of(ByteBuffer.class, byte[].class);
  }

  @Override
  public boolean isSettable(File file, String attribute) {
    return true;
  }

  @Override
  public boolean isSettableOnCreate(String attribute) {
    return false;
  }

  @Override
  public void set(File file, String attribute, Object value) {
    byte[] bytes;
    if (value instanceof byte[]) {
      bytes = ((byte[]) value).clone();
    } else {
      // value instanceof ByteBuffer
      ByteBuffer buffer = (ByteBuffer) value;
      bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
    }

    file.setAttribute(name() + ":" + attribute, bytes);
  }

  @Override
  public Class<UserDefinedFileAttributeView> viewType() {
    return UserDefinedFileAttributeView.class;
  }

  @Override
  public UserDefinedFileAttributeView getView(FileProvider fileProvider) {
    return new View(this, fileProvider);
  }

  /**
   * Implementation of {@link UserDefinedFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements UserDefinedFileAttributeView {

    public View(UserDefinedAttributeProvider attributeProvider, FileProvider fileProvider) {
      super(attributeProvider, fileProvider);
    }

    @Override
    public List<String> list() throws IOException {
      return ((UserDefinedAttributeProvider) provider()).userDefinedAttributes(file());
    }

    private byte[] getStoredBytes(String name) throws IOException {
      byte[] bytes = (byte[]) file().getAttribute(name() + ":" + name);
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
      file().setAttribute(name() + ":" + name, bytes);
      return bytes.length;
    }

    @Override
    public void delete(String name) throws IOException {
      file().deleteAttribute(name() + ":" + name);
    }
  }
}
