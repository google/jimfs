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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;

/**
 * Configuration for an {@code AttributeProvider} that requires other {@code AttributeProvider}
 * instances to construct.
 *
 * @author Colin Decker
 */
abstract class DependantAttributeConfiguration extends AttributeConfiguration {

  private final String view;
  private final ImmutableSet<String> requires;

  protected DependantAttributeConfiguration(String view, String... requires) {
    this.view = checkNotNull(view);
    this.requires = ImmutableSet.copyOf(requires);
  }

  @Override
  protected ImmutableSet<String> provides() {
    return ImmutableSet.of(view);
  }

  @Override
  protected ImmutableSet<String> requires() {
    return requires;
  }
}
