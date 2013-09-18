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

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Files;

/**
 * Definition of a specific attribute that a view supports.
 *
 * @author Colin Decker
 */
public final class Attribute {

  /**
   * Creates a new attribute that cannot be set directly through the {@link Files} API.
   */
  public static Attribute unsettable(String view, String name, Class<?> type) {
    return new Attribute(view, name, type, false, false);
  }

  /**
   * Creates a new attribute that can be set directly through the {@link Files} API but cannot be
   * set upon initial creation of a file.
   */
  public static Attribute settable(String view, String name, Class<?> type) {
    return new Attribute(view, name, type, true, false);
  }

  /**
   * Creates a new attribute that can be set directly through the {@link Files} API and can be set
   * on initial creation of a file.
   */
  public static Attribute settableOnCreate(String view, String name, Class<?> type) {
    return new Attribute(view, name, type, true, true);
  }

  private final String name;
  private final String key;
  private final Class<?> type;
  private final boolean userSettable;
  private final boolean initiallySettable;

  private Attribute(
      String view, String name, Class<?> type, boolean userSettable, boolean initiallySettable) {
    this.name = checkNotNull(name);
    this.key = checkNotNull(view) + ":" + name;
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
   * Returns the full key for this attribute in the form "view:attribute".
   */
  public String key() {
    return key;
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
