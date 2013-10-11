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

package com.google.jimfs.attribute;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;

/**
 * Abstract base class for most implementations of {@link AttributeProvider}.
 *
 * @author Colin Decker
 */
public abstract class AbstractAttributeProvider implements AttributeProvider {

  private final ImmutableMap<String, Attribute> attributes;

  public AbstractAttributeProvider(Iterable<Attribute> attributes) {
    ImmutableMap.Builder<String, Attribute> builder = ImmutableMap.builder();
    for (Attribute attribute : attributes) {
      builder.put(attribute.name(), attribute);
    }
    this.attributes = builder.build();
  }

  @Override
  public void readAll(FileMetadata metadata, Map<String, Object> map) {
    for (String attribute : attributes.keySet()) {
      map.put(attribute, get(metadata, attribute));
    }
  }

  @Override
  public boolean isGettable(FileMetadata metadata, String attribute) {
    return attributes.containsKey(attribute);
  }

  @Override
  public Object get(FileMetadata metadata, String attribute) {
    return metadata.getAttribute(attributes.get(attribute).key());
  }

  @Override
  public ImmutableSet<Class<?>> acceptedTypes(String attribute) {
    return ImmutableSet.<Class<?>>of(attributes.get(attribute).type());
  }

  @Override
  public boolean isSettable(FileMetadata metadata, String attribute) {
    return attributes.containsKey(attribute) &&
        attributes.get(attribute).isUserSettable();
  }

  @Override
  public boolean isSettableOnCreate(String attribute) {
    return attributes.get(attribute).isSettableOnCreate();
  }

  @Override
  public void set(FileMetadata metadata, String attribute, Object value) {
    metadata.setAttribute(attributes.get(attribute).key(), value);
  }
}
