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

package com.google.jimfs;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.providers.AclAttributeProvider;
import com.google.jimfs.attribute.providers.BasicAttributeProvider;
import com.google.jimfs.attribute.providers.DosAttributeProvider;
import com.google.jimfs.attribute.providers.OwnerAttributeProvider;
import com.google.jimfs.attribute.providers.PosixAttributeProvider;
import com.google.jimfs.attribute.providers.UnixAttributeProvider;
import com.google.jimfs.attribute.providers.UserDefinedAttributeProvider;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A collection of other {@code AttributeConfiguration} instances.
 *
 * @author Colin Decker
 */
final class AttributeConfigurationSet extends AttributeConfiguration {

  private ImmutableMap<String, AttributeConfiguration> configurations;

  public AttributeConfigurationSet(AttributeConfiguration... configurations) {
    this(Arrays.asList(configurations));
  }

  public AttributeConfigurationSet(Iterable<? extends AttributeConfiguration> configurations) {
    // build the map of view name to config that creates the provider for that view
    Map<String, AttributeConfiguration> builder = new HashMap<>();

    // flatten any other AttributeConfigurationSets when building the map
    for (AttributeConfiguration config : configurations) {
      if (config instanceof AttributeConfigurationSet) {
        for (AttributeConfiguration config2 :
            ((AttributeConfigurationSet) config).getConfigurations()) {
          addViews(builder, config2);
        }
      } else {
        addViews(builder, config);
      }
    }

    this.configurations = ImmutableMap.copyOf(builder);
  }

  private void addViews(
      Map<String, AttributeConfiguration> builder, AttributeConfiguration config) {
    for (String providedView : config.provides()) {
      if (builder.containsKey(providedView)) {
        throw new IllegalStateException(
            "multiple configurations registered for view \"" + providedView + "\"");
      }

      builder.put(providedView, config);
    }
  }

  /**
   * Gets the set of configurations this contains.
   */
  private ImmutableSet<AttributeConfiguration> getConfigurations() {
    return ImmutableSet.copyOf(configurations.values());
  }

  @Override
  protected ImmutableSet<String> provides() {
    return configurations.keySet();
  }

  @Override
  protected ImmutableSet<String> requires() {
    return ImmutableSet.of();
  }

  @Override
  protected Iterable<? extends AttributeProvider> getProviders(
      Map<String, AttributeProvider> otherProviders) {
    Map<String, AttributeProvider> providers = new HashMap<>();
    providers.put("basic", BasicAttributeProvider.INSTANCE);

    // create each provider, creating the providers it depends directly on first
    for (String viewName : configurations.keySet()) {
      createProviders(viewName, providers);
    }

    // then, ensure that all inherited providers are also created, as there may be inherited
    // providers that the provider didn't require directly for creation
    // copy to ensure no concurrent modification exceptions
    ImmutableSet<AttributeProvider> providerSet = ImmutableSet.copyOf(providers.values());
    for (AttributeProvider provider : providerSet) {
      for (String viewName : provider.inherits()) {
        createProviders(viewName, providers);
      }
    }

    return ImmutableSet.copyOf(providers.values());
  }

  private void createProviders(String viewName, Map<String, AttributeProvider> providers) {
    if (providers.containsKey(viewName)) {
      // already created
      return;
    }

    AttributeConfiguration providerFactory = configurations.get(viewName);
    if (providerFactory == null) {
      createDefault(viewName, providers);
    } else {
      for (String requiredView : providerFactory.requires()) {
        createProviders(requiredView, providers);
      }

      Iterable<? extends AttributeProvider> newProviders = providerFactory.getProviders(providers);
      for (AttributeProvider provider : newProviders) {
        providers.put(provider.name(), provider);
      }
    }
  }

  private void createDefault(String viewName, Map<String, AttributeProvider> providers) {
    switch (viewName) {
      case "owner":
        providers.put(viewName, new OwnerAttributeProvider());
        break;
      case "posix":
        createProviders("owner", providers);
        providers.put(viewName,
            new PosixAttributeProvider((OwnerAttributeProvider) providers.get("owner")));
        break;
      case "unix":
        createProviders("posix", providers);
        providers.put(viewName, new UnixAttributeProvider(
            (PosixAttributeProvider) providers.get("posix")));
        break;
      case "dos":
        createProviders("owner", providers);
        providers.put(viewName, DosAttributeProvider.INSTANCE);
        break;
      case "acl":
        createProviders("owner", providers);
        providers.put(viewName, new AclAttributeProvider(
            (OwnerAttributeProvider) providers.get("owner")));
        break;
      case "user":
        providers.put(viewName, UserDefinedAttributeProvider.INSTANCE);
        break;
      default:
        throw new IllegalArgumentException("can't create view \"" + viewName + "\"");
    }
  }
}
