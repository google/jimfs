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
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.FileMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.attribute.FileAttributeView;
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
final class UserDefinedAttributeProvider extends AttributeProvider<UserDefinedFileAttributeView> {

  public static final String VIEW = "user";

  UserDefinedAttributeProvider() {}

  @Override
  public String name() {
    return VIEW;
  }

  @Override
  public ImmutableSet<String> fixedAttributes() {
    // no fixed set of attributes for this view
    return ImmutableSet.of();
  }

  @Override
  public boolean supports(String attribute) {
    // any attribute name is supported
    return true;
  }

  @Override
  public ImmutableSet<String> attributes(FileMetadata metadata) {
    return userDefinedAttributes(metadata);
  }

  private static ImmutableSet<String> userDefinedAttributes(FileMetadata metadata) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (String attribute : metadata.getAttributeKeys()) {
      if (attribute.startsWith("user:")) {
        builder.add(attribute.substring(5));
      }
    }
    return builder.build();
  }

  @Override
  public Object get(FileMetadata metadata, String attribute) {
    Object value = metadata.getAttribute("user:" + attribute);
    if (value instanceof byte[]) {
      byte[] bytes = (byte[]) value;
      return bytes.clone();
    }
    return null;
  }

  @Override
  public void set(FileMetadata metadata, String view, String attribute, Object value,
      boolean create) {
    checkNotNull(value);
    checkNotCreate(view, attribute, create);

    byte[] bytes;
    if (value instanceof byte[]) {
      bytes = ((byte[]) value).clone();
    } else if (value instanceof ByteBuffer) {
      // value instanceof ByteBuffer
      ByteBuffer buffer = (ByteBuffer) value;
      bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
    } else{
      throw invalidType(view, attribute, value, byte[].class, ByteBuffer.class);
    }

    metadata.setAttribute(VIEW + ":" + attribute, bytes);
  }

  @Override
  public Class<UserDefinedFileAttributeView> viewType() {
    return UserDefinedFileAttributeView.class;
  }

  @Override
  public UserDefinedFileAttributeView view(FileMetadata.Lookup lookup,
      Map<String, FileAttributeView> inheritedViews) {
    return new View(lookup);
  }

  /**
   * Implementation of {@link UserDefinedFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements UserDefinedFileAttributeView {

    public View(FileMetadata.Lookup lookup) {
      super(lookup);
    }

    @Override
    public String name() {
      return "user";
    }

    @Override
    public List<String> list() throws IOException {
      return userDefinedAttributes(lookupMetadata()).asList();
    }

    private byte[] getStoredBytes(String name) throws IOException {
      byte[] bytes = (byte[]) lookupMetadata().getAttribute(name() + ":" + name);
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
      lookupMetadata().setAttribute(name() + ":" + name, bytes);
      return bytes.length;
    }

    @Override
    public void delete(String name) throws IOException {
      lookupMetadata().deleteAttribute(name() + ":" + name);
    }
  }
}
