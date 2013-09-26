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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.jimfs.AttributeViews;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.AttributeReader;
import com.google.jimfs.attribute.AttributeViewProvider;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for a set of attribute providers, indexed for easy access.
 *
 * @author Colin Decker
 */
final class AttributeProviderRegistry {

  private final ImmutableSet<AttributeProvider> providers;
  private final ImmutableListMultimap<String, AttributeProvider> allProviders;
  private final ImmutableMap<Class<?>, AttributeViewProvider<?>> viewProviders;
  private final ImmutableMap<Class<?>, AttributeReader<?>> readers;

  public AttributeProviderRegistry(AttributeViews views) {
    this(views.getProviders(new HashMap<String, AttributeProvider>()));
  }

  public AttributeProviderRegistry(Iterable<? extends AttributeProvider> providers) {
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
   * Returns the set of attribute views that are supported by the providers in this registry.
   */
  public ImmutableSet<String> getSupportedViews() {
    return allProviders.keySet();
  }

  /**
   * Returns the set of file attribute view types that are supported by the providers in this
   * registry.
   */
  public ImmutableSet<Class<?>> getSupportedViewTypes() {
    return viewProviders.keySet();
  }

  /**
   * Returns the set of file attributes types that are supported by the providers in this registry.
   */
  public ImmutableSet<Class<?>> getSupportedAttributesTypes() {
    return readers.keySet();
  }

  /**
   * Returns all attribute providers in this registry.
   */
  public ImmutableSet<AttributeProvider> getProviders() {
    return providers;
  }

  /**
   * Gets the list of all providers that can provide attributes for the view with the given name.
   * The first provider in the returned list is the actual provider for the view name, the rest
   * are views it inherits.
   */
  public ImmutableList<AttributeProvider> getProviders(String name) {
    return allProviders.get(name);
  }

  /**
   * Gets the attribute view provider for the given view type.
   */
  @SuppressWarnings("unchecked")
  public <V extends FileAttributeView> AttributeViewProvider<V> getViewProvider(Class<V> type) {
    return (AttributeViewProvider<V>) viewProviders.get(type);
  }

  /**
   * Gets the attribute reader for the given attributes type.
   */
  @SuppressWarnings("unchecked")
  public <A extends BasicFileAttributes> AttributeReader<A> getReader(Class<A> type) {
    return (AttributeReader<A>) readers.get(type);
  }
}
