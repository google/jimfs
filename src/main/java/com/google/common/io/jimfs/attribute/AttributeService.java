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

package com.google.common.io.jimfs.attribute;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Functions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.jimfs.file.File;
import com.google.common.io.jimfs.file.FileProvider;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Central class for handling file attributes for a file system.
 *
 * @author Colin Decker
 */
public final class AttributeService {

  private static final String ALL_ATTRIBUTES = "*";

  private final ImmutableMap<String, AttributeProvider> providers;
  private final ImmutableMap<Class<?>, AttributeViewProvider<?>> viewProviders;
  private final ImmutableMap<Class<?>, AttributeReader<?>> readers;

  public AttributeService(AttributeProvider... providers) {
    this(Arrays.asList(providers));
  }

  public AttributeService(Iterable<? extends AttributeProvider> providers) {
    ImmutableMap.Builder<String, AttributeProvider> providersBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<Class<?>, AttributeViewProvider<?>> viewProvidersBuilder
        = ImmutableMap.builder();
    ImmutableMap.Builder<Class<?>, AttributeReader<?>> readersBuilder = ImmutableMap.builder();

    for (AttributeProvider provider : providers) {
      providersBuilder.put(provider.name(), provider);

      if (provider instanceof AttributeViewProvider<?>) {
        AttributeViewProvider<?> viewProvider = (AttributeViewProvider<?>) provider;
        viewProvidersBuilder.put(viewProvider.viewType(), viewProvider);
      }

      if (provider instanceof AttributeReader<?>) {
        AttributeReader<?> reader = (AttributeReader<?>) provider;
        readersBuilder.put(reader.attributesType(), reader);
      }
    }

    this.providers = providersBuilder.build();
    this.viewProviders = viewProvidersBuilder.build();
    this.readers = readersBuilder.build();
  }

  /**
   * Implements {@link FileSystem#supportedFileAttributeViews()}.
   */
  public ImmutableSet<String> supportedFileAttributeViews() {
    return providers.keySet();
  }

  /**
   * Implements {@link FileStore#supportsFileAttributeView(Class)}.
   */
  public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
    return viewProviders.containsKey(type);
  }

  /**
   * Sets all initial attributes for the given file.
   */
  public void setInitialAttributes(File file) {
    for (AttributeProvider provider : providers.values()) {
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
    for (AttributeProvider provider : providers(view)) {
      if (provider.isGettable(file, attribute)) {
        return (V) provider.get(file, attribute);
      }
    }

    throw new IllegalArgumentException("attribute not found: " + attribute);
  }

  /**
   * Sets the value of the given attribute to the given value for the given file.
   */
  public void setAttribute(File file, String attribute, Object value, SetMode mode) {
    String view = getViewName(attribute);
    String attr = getSingleAttribute(attribute);
    setAttribute(file, view, attr, value, mode);
  }

  /**
   * Sets the value of the given attribute to the given value for the given view and file.
   */
  public void setAttribute(
      File file, String view, String attribute, Object value, SetMode mode) {
    for (AttributeProvider provider : providers(view)) {
      if (provider.isSettable(file, attribute)) {
        if (mode == SetMode.CREATE && !provider.isSettableOnCreate(attribute)) {
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
   * Returns an attribute view of the given type for the given file provider, or {@code null} if
   * the view type is not supported.
   */
  @Nullable
  public <V extends FileAttributeView> V getFileAttributeView(
      FileProvider fileProvider, Class<V> type) {
    if (viewProviders.containsKey(type)) {
      return viewProvider(type).getView(fileProvider);
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

    ImmutableMap.Builder<String, Object> result = ImmutableMap.builder();
    if (attrs.size() == 1 && attrs.contains(ALL_ATTRIBUTES)) {
      // for 'view:*' format, get all keys for all providers for the view
      for (AttributeProvider provider : providers(view)) {
        provider.readAll(file, result);
      }
    } else {
      // for 'view:attr1,attr2,etc'
      for (String attr : attrs) {
        boolean found = false;
        for (AttributeProvider provider : providers(view)) {
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

    return result.build();
  }

  /**
   * Returns attributes of the given file as an object of the given type.
   *
   * @throws UnsupportedOperationException if the given attributes type is not supported
   */
  public <A extends BasicFileAttributes> A readAttributes(File file, Class<A> type) {
    if (readers.containsKey(type)) {
      return reader(type).read(file);
    }

    throw new UnsupportedOperationException("unsupported attributes type: " + type);
  }

  private AttributeProvider provider(String name) {
    checkArgument(providers.containsKey(name), "attribute view not available: %s", name);
    return providers.get(name);
  }

  private Iterable<AttributeProvider> providers(String name) {
    AttributeProvider provider = provider(name);
    Iterable<AttributeProvider> extendedProviders = Iterables.transform(
        provider.inherits(), Functions.forMap(providers));

    return Iterables.concat(ImmutableSet.of(provider), extendedProviders);
  }

  @SuppressWarnings("unchecked")
  private <V extends FileAttributeView> AttributeViewProvider<V> viewProvider(Class<V> type) {
    return (AttributeViewProvider<V>) viewProviders.get(type);
  }

  @SuppressWarnings("unchecked")
  private <A extends BasicFileAttributes> AttributeReader<A> reader(Class<A> type) {
    return (AttributeReader<A>) readers.get(type);
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
   * Modes for setting attributes. Attributes may be set when creating a file ({@link #CREATE}) or
   * on an existing file ({@link #NORMAL}). Only certain attributes may be set when creating a file.
   */
  public enum SetMode {
    /** Mode for setting an attribute on an existing file. */
    NORMAL,
    /** Mode for setting an attribute on a file that is being created. */
    CREATE
  }
}
