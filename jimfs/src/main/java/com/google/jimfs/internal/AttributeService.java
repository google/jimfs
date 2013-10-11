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

import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.jimfs.AttributeViews;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.AttributeReader;
import com.google.jimfs.attribute.AttributeStore;
import com.google.jimfs.attribute.AttributeViewProvider;
import com.google.jimfs.attribute.IoSupplier;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Service providing all attribute related operations for a file store. One piece of the file store
 * implementation.
 *
 * @author Colin Decker
 */
final class AttributeService {

  private static final String ALL_ATTRIBUTES = "*";

  private final ImmutableSet<AttributeProvider> providers;
  private final ImmutableListMultimap<String, AttributeProvider> allProviders;
  private final ImmutableMap<Class<?>, AttributeViewProvider<?>> viewProviders;
  private final ImmutableMap<Class<?>, AttributeReader<?>> readers;

  public AttributeService(AttributeViews views) {
    this(views.getProviders(new HashMap<String, AttributeProvider>()));
  }

  public AttributeService(Iterable<? extends AttributeProvider> providers) {
    this.providers = ImmutableSet.copyOf(providers);

    ListMultimap<String, AttributeProvider> allProvidersBuilder = ArrayListMultimap.create();
    Map<Class<?>, AttributeViewProvider<?>> viewProvidersBuilder = new HashMap<>();
    Map<Class<?>, AttributeReader<?>> readersBuilder = new HashMap<>();

    for (AttributeProvider provider : this.providers) {
      allProvidersBuilder.put(provider.name(), provider);

      if (provider instanceof AttributeViewProvider<?>) {
        AttributeViewProvider<?> viewProvider = (AttributeViewProvider<?>) provider;
        viewProvidersBuilder.put(viewProvider.viewType(), viewProvider);
      }

      if (provider instanceof AttributeReader<?>) {
        AttributeReader<?> reader = (AttributeReader<?>) provider;
        readersBuilder.put(reader.attributesType(), reader);
      }
    }

    for (AttributeProvider provider : this.providers) {
      for (String inherits : provider.inherits()) {
        allProvidersBuilder.put(provider.name(), allProvidersBuilder.get(inherits).get(0));
      }
    }

    this.allProviders = ImmutableListMultimap.copyOf(allProvidersBuilder);
    this.viewProviders = ImmutableMap.copyOf(viewProvidersBuilder);
    this.readers = ImmutableMap.copyOf(readersBuilder);
  }

  /**
   * Implements {@link FileSystem#supportedFileAttributeViews()}.
   */
  public ImmutableSet<String> supportedFileAttributeViews() {
    return allProviders.keySet();
  }

  /**
   * Implements {@link FileStore#supportsFileAttributeView(Class)}.
   */
  public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
    return viewProviders.keySet().contains(type);
  }

  /**
   * Sets all initial attributes for the given file, including the given attributes if possible.
   */
  public void setInitialAttributes(File file, FileAttribute<?>... attrs) {
    for (AttributeProvider provider : providers) {
      provider.setInitial(file);
    }

    for (FileAttribute<?> attr : attrs) {
      setAttributeInternal(file, attr.name(), attr.value(), true);
    }
  }

  /**
   * Copies the file times of the given file to the given copy file.
   */
  public void copyBasicAttributes(File file, File copy) {
    copy.setCreationTime(file.getCreationTime());
    copy.setLastAccessTime(file.getLastAccessTime());
    copy.setLastModifiedTime(file.getLastModifiedTime());
  }

  /**
   * Copies the attributes of the given file to the given copy file.
   */
  public void copyAttributes(File file, File copy) {
    copyBasicAttributes(file, copy);
    for (String attribute : file.getAttributeKeys()) {
      copy.setAttribute(attribute, file.getAttribute(attribute));
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
    for (AttributeProvider provider : allProviders.get(view)) {
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
    for (AttributeProvider provider : allProviders.get(view)) {
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
  @SuppressWarnings("unchecked")
  @Nullable
  public <V extends FileAttributeView> V getFileAttributeView(
      IoSupplier<? extends AttributeStore> supplier, Class<V> type) {
    if (supportsFileAttributeView(type)) {
      return ((AttributeViewProvider<V>) viewProviders.get(type)).getView(supplier);
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
      for (AttributeProvider provider : allProviders.get(view)) {
        provider.readAll(file, result);
      }
    } else {
      // for 'view:attr1,attr2,etc'
      for (String attr : attrs) {
        boolean found = false;
        for (AttributeProvider provider : allProviders.get(view)) {
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
  @SuppressWarnings("unchecked")
  public <A extends BasicFileAttributes> A readAttributes(File file, Class<A> type) {
    if (readers.keySet().contains(type)) {
      return ((AttributeReader<A>) readers.get(type)).read(file);
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
}
