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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.jimfs.attribute.AttributeProvider;

import java.util.Arrays;
import java.util.Map;

/**
 * {@code AttributeConfiguration} providing a set of one or more already constructed
 * {@code AttributeProvider} instances.
 *
 * @author Colin Decker
 */
final class ProviderAttributeConfiguration extends AttributeConfiguration {

  private final ImmutableSet<AttributeProvider> providers;
  private final ImmutableSet<String> views;

  ProviderAttributeConfiguration(AttributeProvider... providers) {
    this(Arrays.asList(providers));
  }

  ProviderAttributeConfiguration(Iterable<? extends AttributeProvider> providers) {
    this.providers = ImmutableSet.copyOf(providers);
    this.views = ImmutableSet.copyOf(Iterables.transform(this.providers,
        new Function<AttributeProvider, String>() {
          @Override
          public String apply(AttributeProvider provider) {
            return provider.name();
          }
        }));
  }

  @Override
  protected ImmutableSet<String> provides() {
    return views;
  }

  @Override
  protected ImmutableSet<String> requires() {
    return ImmutableSet.of();
  }

  @Override
  protected Iterable<? extends AttributeProvider> getProviders(
      Map<String, AttributeProvider> otherProviders) {
    return providers;
  }
}
