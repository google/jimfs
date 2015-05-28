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

package com.google.common.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Service providing all attribute related operations for a file store. One piece of the file store
 * implementation.
 *
 * @author Colin Decker
 */
final class AttributeService {

  private static final String ALL_ATTRIBUTES = "*";

  private final ImmutableMap<String, AttributeProvider> providersByName;
  private final ImmutableMap<Class<?>, AttributeProvider> providersByViewType;
  private final ImmutableMap<Class<?>, AttributeProvider> providersByAttributesType;

  private final ImmutableList<FileAttribute<?>> defaultValues;

  /**
   * Creates a new attribute service using the given configuration.
   */
  public AttributeService(Configuration configuration) {
    this(getProviders(configuration), configuration.defaultAttributeValues);
  }

  /**
   * Creates a new attribute service using the given providers and user provided default attribute
   * values.
   */
  public AttributeService(
      Iterable<? extends AttributeProvider> providers, Map<String, ?> userProvidedDefaults) {
    ImmutableMap.Builder<String, AttributeProvider> byViewNameBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<Class<?>, AttributeProvider> byViewTypeBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<Class<?>, AttributeProvider> byAttributesTypeBuilder =
        ImmutableMap.builder();

    ImmutableList.Builder<FileAttribute<?>> defaultAttributesBuilder = ImmutableList.builder();

    for (AttributeProvider provider : providers) {
      byViewNameBuilder.put(provider.name(), provider);
      byViewTypeBuilder.put(provider.viewType(), provider);
      if (provider.attributesType() != null) {
        byAttributesTypeBuilder.put(provider.attributesType(), provider);
      }

      for (Map.Entry<String, ?> entry : provider.defaultValues(userProvidedDefaults).entrySet()) {
        defaultAttributesBuilder.add(new SimpleFileAttribute<>(entry.getKey(), entry.getValue()));
      }
    }

    this.providersByName = byViewNameBuilder.build();
    this.providersByViewType = byViewTypeBuilder.build();
    this.providersByAttributesType = byAttributesTypeBuilder.build();
    this.defaultValues = defaultAttributesBuilder.build();
  }

  private static Iterable<AttributeProvider> getProviders(Configuration configuration) {
    Map<String, AttributeProvider> result = new HashMap<>();

    for (AttributeProvider provider : configuration.attributeProviders) {
      result.put(provider.name(), provider);
    }

    for (String view : configuration.attributeViews) {
      addStandardProvider(result, view);
    }

    addMissingProviders(result);

    return Collections.unmodifiableCollection(result.values());
  }

  private static void addMissingProviders(Map<String, AttributeProvider> providers) {
    Set<String> missingViews = new HashSet<>();
    for (AttributeProvider provider : providers.values()) {
      for (String inheritedView : provider.inherits()) {
        if (!providers.containsKey(inheritedView)) {
          missingViews.add(inheritedView);
        }
      }
    }

    if (missingViews.isEmpty()) {
      return;
    }

    // add any inherited views that were not listed directly
    for (String view : missingViews) {
      addStandardProvider(providers, view);
    }

    // in case any of the providers that were added themselves have missing views they inherit
    addMissingProviders(providers);
  }

  private static void addStandardProvider(Map<String, AttributeProvider> result, String view) {
    AttributeProvider provider = StandardAttributeProviders.get(view);

    if (provider == null) {
      if (!result.containsKey(view)) {
        throw new IllegalStateException("no provider found for attribute view '" + view + "'");
      }
    } else {
      result.put(provider.name(), provider);
    }
  }

  /**
   * Implements {@link FileSystem#supportedFileAttributeViews()}.
   */
  public ImmutableSet<String> supportedFileAttributeViews() {
    return providersByName.keySet();
  }

  /**
   * Implements {@link FileStore#supportsFileAttributeView(Class)}.
   */
  public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
    return providersByViewType.containsKey(type);
  }

  /**
   * Sets all initial attributes for the given file, including the given attributes if possible.
   */
  public void setInitialAttributes(File file, FileAttribute<?>... attrs) {
    // default values should already be sanitized by their providers
    for (int i = 0; i < defaultValues.size(); i++) {
      FileAttribute<?> attribute = defaultValues.get(i);

      int separatorIndex = attribute.name().indexOf(':');
      String view = attribute.name().substring(0, separatorIndex);
      String attr = attribute.name().substring(separatorIndex + 1);
      file.setAttribute(view, attr, attribute.value());
    }

    for (FileAttribute<?> attr : attrs) {
      setAttribute(file, attr.name(), attr.value(), true);
    }
  }

  /**
   * Copies the attributes of the given file to the given copy file.
   */
  public void copyAttributes(File file, File copy, AttributeCopyOption copyOption) {
    switch (copyOption) {
      case ALL:
        file.copyAttributes(copy);
        break;
      case BASIC:
        file.copyBasicAttributes(copy);
        break;
      default:
        // don't copy
    }
  }

  /**
   * Gets the value of the given attribute for the given file. {@code attribute} must be of the
   * form "view:attribute" or "attribute".
   */
  public Object getAttribute(File file, String attribute) {
    String view = getViewName(attribute);
    String attr = getSingleAttribute(attribute);
    return getAttribute(file, view, attr);
  }

  /**
   * Gets the value of the given attribute for the given view and file. Neither view nor attribute
   * may have a ':' character.
   */
  public Object getAttribute(File file, String view, String attribute) {
    Object value = getAttributeInternal(file, view, attribute);
    if (value == null) {
      throw new IllegalArgumentException("invalid attribute for view '" + view + "': " + attribute);
    }
    return value;
  }

  @Nullable
  private Object getAttributeInternal(File file, String view, String attribute) {
    AttributeProvider provider = providersByName.get(view);
    if (provider == null) {
      return null;
    }

    Object value = provider.get(file, attribute);
    if (value == null) {
      for (String inheritedView : provider.inherits()) {
        value = getAttributeInternal(file, inheritedView, attribute);
        if (value != null) {
          break;
        }
      }
    }

    return value;
  }

  /**
   * Sets the value of the given attribute to the given value for the given file.
   */
  public void setAttribute(File file, String attribute, Object value, boolean create) {
    String view = getViewName(attribute);
    String attr = getSingleAttribute(attribute);
    setAttributeInternal(file, view, attr, value, create);
  }

  private void setAttributeInternal(
      File file, String view, String attribute, Object value, boolean create) {
    AttributeProvider provider = providersByName.get(view);

    if (provider != null) {
      if (provider.supports(attribute)) {
        provider.set(file, view, attribute, value, create);
        return;
      }

      for (String inheritedView : provider.inherits()) {
        AttributeProvider inheritedProvider = providersByName.get(inheritedView);
        if (inheritedProvider.supports(attribute)) {
          inheritedProvider.set(file, view, attribute, value, create);
          return;
        }
      }
    }

    throw new IllegalArgumentException("cannot set attribute '" + view + ":" + attribute + "'");
  }

  /**
   * Returns an attribute view of the given type for the given file lookup callback, or
   * {@code null} if the view type is not supported.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <V extends FileAttributeView> V getFileAttributeView(FileLookup lookup, Class<V> type) {
    AttributeProvider provider = providersByViewType.get(type);

    if (provider != null) {
      return (V) provider.view(lookup, createInheritedViews(lookup, provider));
    }

    return null;
  }

  private ImmutableMap<String, FileAttributeView> createInheritedViews(
      FileLookup lookup, AttributeProvider provider) {
    if (provider.inherits().isEmpty()) {
      return ImmutableMap.of();
    }

    Map<String, FileAttributeView> inheritedViews = new HashMap<>();
    createInheritedViews(lookup, provider, inheritedViews);
    return ImmutableMap.copyOf(inheritedViews);
  }

  private void createInheritedViews(
      FileLookup lookup,
      AttributeProvider provider,
      Map<String, FileAttributeView> inheritedViews) {

    for (String inherited : provider.inherits()) {
      if (!inheritedViews.containsKey(inherited)) {
        AttributeProvider inheritedProvider = providersByName.get(inherited);
        FileAttributeView inheritedView =
            getFileAttributeView(lookup, inheritedProvider.viewType(), inheritedViews);

        inheritedViews.put(inherited, inheritedView);
      }
    }
  }

  private FileAttributeView getFileAttributeView(
      FileLookup lookup,
      Class<? extends FileAttributeView> viewType,
      Map<String, FileAttributeView> inheritedViews) {
    AttributeProvider provider = providersByViewType.get(viewType);
    createInheritedViews(lookup, provider, inheritedViews);
    return provider.view(lookup, ImmutableMap.copyOf(inheritedViews));
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
      AttributeProvider provider = providersByName.get(view);
      readAll(file, provider, result);

      for (String inheritedView : provider.inherits()) {
        AttributeProvider inheritedProvider = providersByName.get(inheritedView);
        readAll(file, inheritedProvider, result);
      }
    } else {
      // for 'view:attr1,attr2,etc'
      for (String attr : attrs) {
        result.put(attr, getAttribute(file, view, attr));
      }
    }

    return ImmutableMap.copyOf(result);
  }

  private static void readAll(File file, AttributeProvider provider, Map<String, Object> map) {
    for (String attribute : provider.attributes(file)) {
      Object value = provider.get(file, attribute);

      // check for null to protect against race condition when an attribute present when
      // attributes(file) was called is deleted before get() is called for that attribute
      if (value != null) {
        map.put(attribute, value);
      }
    }
  }

  /**
   * Returns attributes of the given file as an object of the given type.
   *
   * @throws UnsupportedOperationException if the given attributes type is not supported
   */
  @SuppressWarnings("unchecked")
  public <A extends BasicFileAttributes> A readAttributes(File file, Class<A> type) {
    AttributeProvider provider = providersByAttributesType.get(type);
    if (provider != null) {
      return (A) provider.readAttributes(file);
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
   * Simple implementation of {@link FileAttribute}.
   */
  private static final class SimpleFileAttribute<T> implements FileAttribute<T> {

    private final String name;
    private final T value;

    SimpleFileAttribute(String name, T value) {
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
}
