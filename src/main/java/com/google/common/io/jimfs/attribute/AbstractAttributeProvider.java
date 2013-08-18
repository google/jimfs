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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.jimfs.file.File;

/**
 * Abstract base class for most implementations of {@link AttributeProvider}.
 *
 * @author Colin Decker
 */
abstract class AbstractAttributeProvider implements AttributeProvider {

  private final ImmutableMap<String, AttributeSpec> attributes;

  public AbstractAttributeProvider(Iterable<AttributeSpec> attributes) {
    ImmutableMap.Builder<String, AttributeSpec> builder = ImmutableMap.builder();
    for (AttributeSpec attribute : attributes) {
      builder.put(attribute.name(), attribute);
    }
    this.attributes = builder.build();
  }

  @Override
  public void readAll(File file, ImmutableMap.Builder<String, Object> builder) {
    for (String attribute : attributes.keySet()) {
      builder.put(attribute, get(file, attribute));
    }
  }

  @Override
  public boolean isGettable(File file, String attribute) {
    return attributes.containsKey(attribute);
  }

  @Override
  public Object get(File file, String attribute) {
    return file.getAttribute(name() + ":" + attribute);
  }

  @Override
  public ImmutableSet<Class<?>> acceptedTypes(String attribute) {
    return ImmutableSet.<Class<?>>of(attributes.get(attribute).type());
  }

  @Override
  public boolean isSettable(File file, String attribute) {
    return attributes.containsKey(attribute) &&
        attributes.get(attribute).isUserSettable();
  }

  @Override
  public boolean isSettableOnCreate(String attribute) {
    return attributes.get(attribute).isSettableOnCreate();
  }

  @Override
  public void set(File file, String attribute, Object value) {
    file.setAttribute(name() + ":" + attribute, value);
  }
}
