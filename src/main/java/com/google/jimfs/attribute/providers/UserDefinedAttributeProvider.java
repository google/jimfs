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
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.AttributeStore;
import com.google.jimfs.attribute.AttributeViewProvider;
import com.google.jimfs.common.IoSupplier;

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
public final class UserDefinedAttributeProvider implements AttributeProvider,
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
  public void readAll(AttributeStore store, Map<String, Object> map) {
    for (String attribute : store.getAttributeKeys()) {
      if (attribute.startsWith("user:")) {
        String attributeName = attribute.substring(5);
        map.put(attributeName, get(store, attributeName));
      }
    }
  }

  private ImmutableList<String> userDefinedAttributes(AttributeStore store) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String attribute : store.getAttributeKeys()) {
      if (attribute.startsWith("user:")) {
        builder.add(attribute.substring(5));
      }
    }
    return builder.build();
  }

  @Override
  public void setInitial(AttributeStore store) {
  }

  @Override
  public boolean isGettable(AttributeStore store, String attribute) {
    return store.getAttribute(name() + ":" + attribute) != null;
  }

  @Override
  public Object get(AttributeStore store, String attribute) {
    byte[] bytes = (byte[]) store.getAttribute(name() + ":" + attribute);
    return bytes.clone();
  }

  @Override
  public ImmutableSet<Class<?>> acceptedTypes(String attribute) {
    return ImmutableSet.of(ByteBuffer.class, byte[].class);
  }

  @Override
  public boolean isSettable(AttributeStore store, String attribute) {
    return true;
  }

  @Override
  public boolean isSettableOnCreate(String attribute) {
    return false;
  }

  @Override
  public void set(AttributeStore store, String attribute, Object value) {
    byte[] bytes;
    if (value instanceof byte[]) {
      bytes = ((byte[]) value).clone();
    } else {
      // value instanceof ByteBuffer
      ByteBuffer buffer = (ByteBuffer) value;
      bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
    }

    store.setAttribute(name() + ":" + attribute, bytes);
  }

  @Override
  public Class<UserDefinedFileAttributeView> viewType() {
    return UserDefinedFileAttributeView.class;
  }

  @Override
  public UserDefinedFileAttributeView getView(IoSupplier<? extends AttributeStore> supplier) {
    return new View(this, supplier);
  }

  /**
   * Implementation of {@link UserDefinedFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements UserDefinedFileAttributeView {

    public View(UserDefinedAttributeProvider attributeProvider,
        IoSupplier<? extends AttributeStore> supplier) {
      super(attributeProvider, supplier);
    }

    @Override
    public List<String> list() throws IOException {
      return ((UserDefinedAttributeProvider) provider()).userDefinedAttributes(store());
    }

    private byte[] getStoredBytes(String name) throws IOException {
      byte[] bytes = (byte[]) store().getAttribute(name() + ":" + name);
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
      store().setAttribute(name() + ":" + name, bytes);
      return bytes.length;
    }

    @Override
    public void delete(String name) throws IOException {
      store().deleteAttribute(name() + ":" + name);
    }
  }
}
