/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Files;

/**
 * Definition of a specific attribute that a view supports.
 *
 * @author Colin Decker
 */
public final class AttributeSpec {

  /**
   * Creates a new attribute that cannot be set directly through the {@link Files} API.
   */
  public static AttributeSpec unsettable(String name, Class<?> type) {
    return new AttributeSpec(name, type, false, false);
  }

  /**
   * Creates a new attribute that can be set directly through the {@link Files} API but cannot be
   * set upon initial creation of a file.
   */
  public static AttributeSpec settable(String name, Class<?> type) {
    return new AttributeSpec(name, type, true, false);
  }

  /**
   * Creates a new attribute that can be set directly through the {@link Files} API and can be set
   * on initial creation of a file.
   */
  public static AttributeSpec settableOnCreate(String name, Class<?> type) {
    return new AttributeSpec(name, type, true, true);
  }

  private final String name;
  private final Class<?> type;
  private final boolean userSettable;
  private final boolean initiallySettable;

  private AttributeSpec(
      String name, Class<?> type, boolean userSettable, boolean initiallySettable) {
    this.name = checkNotNull(name);
    this.type = checkNotNull(type);
    this.userSettable = userSettable;
    this.initiallySettable = initiallySettable;
  }

  /**
   * Returns the name of the attribute.
   */
  public String name() {
    return name;
  }

  /**
   * Returns the required type of the attribute value.
   */
  public Class<?> type() {
    return type;
  }

  /**
   * Returns whether or not users are allowed to set the attribute using
   * {@link Files#setAttribute}.
   */
  public boolean isUserSettable() {
    return userSettable;
  }

  /**
   * Returns whether or not users are allowed to set the attribute when creating a new file.
   */
  public boolean isSettableOnCreate() {
    return initiallySettable;
  }
}
