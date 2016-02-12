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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.util.Arrays;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Abstract provider for handling a specific file attribute view.
 *
 * @author Colin Decker
 */
public abstract class AttributeProvider {

  /**
   * Returns the view name that's used to get attributes from this provider.
   */
  public abstract String name();

  /**
   * Returns the names of other providers that this provider inherits attributes from.
   */
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of();
  }

  /**
   * Returns the type of the view interface that this provider supports.
   */
  public abstract Class<? extends FileAttributeView> viewType();

  /**
   * Returns a view of the file located by the given lookup callback. The given map contains the
   * views inherited by this view.
   */
  public abstract FileAttributeView view(
      FileLookup lookup, ImmutableMap<String, FileAttributeView> inheritedViews);

  /**
   * Returns a map containing the default attribute values for this provider. The keys of the map
   * are attribute identifier strings (in "view:attribute" form) and the value for each is the
   * default value that should be set for that attribute when creating a new file.
   *
   * <p>The given map should be in the same format and contains user-provided default values. If
   * the user provided any default values for attributes handled by this provider, those values
   * should be checked to ensure they are of the correct type. Additionally, if any changes to a
   * user-provided attribute are necessary (for example, creating an immutable defensive copy),
   * that should be done. The resulting values should be included in the result map along with
   * default values for any attributes the user did not provide a value for.
   */
  public ImmutableMap<String, ?> defaultValues(Map<String, ?> userDefaults) {
    return ImmutableMap.of();
  }

  /**
   * Returns the set of attributes that are always available from this provider.
   */
  public abstract ImmutableSet<String> fixedAttributes();

  /**
   * Returns whether or not this provider supports the given attribute directly.
   */
  public boolean supports(String attribute) {
    return fixedAttributes().contains(attribute);
  }

  /**
   * Returns the set of attributes supported by this view that are present in the given file. For
   * most providers, this will be a fixed set of attributes.
   */
  public ImmutableSet<String> attributes(File file) {
    return fixedAttributes();
  }

  /**
   * Returns the value of the given attribute in the given file or null if the attribute is not
   * supported by this provider.
   */
  @Nullable
  public abstract Object get(File file, String attribute);

  /**
   * Sets the value of the given attribute in the given file object. The {@code create}
   * parameter indicates whether or not the value is being set upon creation of a new file via a
   * user-provided {@code FileAttribute}.
   *
   * @throws IllegalArgumentException if the given attribute is one supported by this provider but
   *     it is not allowed to be set by the user
   * @throws UnsupportedOperationException if the given attribute is one supported by this provider
   *     and is allowed to be set by the user, but not on file creation and {@code create} is true
   */
  public abstract void set(File file, String view, String attribute, Object value, boolean create);

  // optional

  /**
   * Returns the type of file attributes object this provider supports, or null if it doesn't
   * support reading its attributes as an object.
   */
  @Nullable
  public Class<? extends BasicFileAttributes> attributesType() {
    return null;
  }

  /**
   * Reads this provider's attributes from the given file as an attributes object.
   *
   * @throws UnsupportedOperationException if this provider does not support reading an attributes
   *     object
   */
  public BasicFileAttributes readAttributes(File file) {
    throw new UnsupportedOperationException();
  }

  // exception helpers

  /**
   * Throws an illegal argument exception indicating that the given attribute cannot be set.
   */
  protected static IllegalArgumentException unsettable(String view, String attribute) {
    throw new IllegalArgumentException("cannot set attribute '" + view + ":" + attribute + "'");
  }

  /**
   * Checks that the attribute is not being set by the user on file creation, throwing an
   * unsupported operation exception if it is.
   */
  protected static void checkNotCreate(String view, String attribute, boolean create) {
    if (create) {
      throw new UnsupportedOperationException(
          "cannot set attribute '" + view + ":" + attribute + "' during file creation");
    }
  }

  /**
   * Checks that the given value is of the given type, returning the value if so and throwing an
   * exception if not.
   */
  protected static <T> T checkType(String view, String attribute, Object value, Class<T> type) {
    checkNotNull(value);
    if (type.isInstance(value)) {
      return type.cast(value);
    }

    throw invalidType(view, attribute, value, type);
  }

  /**
   * Throws an illegal argument exception indicating that the given value is not one of the
   * expected types for the given attribute.
   */
  protected static IllegalArgumentException invalidType(
      String view, String attribute, Object value, Class<?>... expectedTypes) {
    Object expected =
        expectedTypes.length == 1 ? expectedTypes[0] : "one of " + Arrays.toString(expectedTypes);
    throw new IllegalArgumentException(
        "invalid type " + value.getClass() 
            + " for attribute '" + view + ":" + attribute
            + "': expected " + expected);
  }
}
