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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.providers.AclAttributeProvider;
import com.google.jimfs.attribute.providers.BasicAttributeProvider;
import com.google.jimfs.attribute.providers.DosAttributeProvider;
import com.google.jimfs.attribute.providers.OwnerAttributeProvider;
import com.google.jimfs.attribute.providers.PosixAttributeProvider;
import com.google.jimfs.attribute.providers.UnixAttributeProvider;
import com.google.jimfs.attribute.providers.UserDefinedAttributeProvider;

import java.nio.file.attribute.AclEntry;
import java.util.List;
import java.util.Map;

/**
 * Attribute view configuration for a file system. This class contains a set of static methods that
 * return configuration instances that define one or more attribute views. Multiple configurations
 * may be combined to configure the set of attribute views that a file system supports, but
 * providing more than one configuration that defines the same attribute view will result in an
 * exception.
 *
 * @author Colin Decker
 */
public abstract class AttributeConfiguration {

  /**
   * Returns a configuration for the "basic" attribute view.
   *
   * <p>This is the default, and a file system will always have a basic attribute view regardless
   * of what views are configured for it.
   */
  public static AttributeConfiguration basic() {
    return new ProviderAttributeConfiguration(BasicAttributeProvider.INSTANCE);
  }

  /**
   * Returns a configuration for the "owner" attribute view. Files will be created with the owner
   * "user" by default.
   */
  public static AttributeConfiguration owner() {
    return owner("user");
  }

  /**
   * Returns a configuration for the "owner" attribute view. Files will be created with the given
   * owner by default.
   */
  public static AttributeConfiguration owner(String owner) {
    return new ProviderAttributeConfiguration(new OwnerAttributeProvider(owner));
  }

  /**
   * Returns a configuration for the "posix" attribute view. Files will be created with the group
   * "group" and permissions "rw-r--r--" by default.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeConfiguration posix() {
    return posix("group", "rw-r--r--");
  }

  /**
   * Returns a configuration for the "posix" attribute view. Files will be created with the given
   * group and permissions by default.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeConfiguration posix(final String group, final String permissions) {
    return new DependantAttributeConfiguration("posix", "owner") {
      @Override
      protected Iterable<? extends AttributeProvider> getProviders(
          Map<String, AttributeProvider> otherProviders) {
        OwnerAttributeProvider owner = (OwnerAttributeProvider) otherProviders.get("owner");
        PosixAttributeProvider posix = new PosixAttributeProvider(group, permissions, owner);
        return ImmutableSet.of(posix);
      }
    };
  }

  /**
   * Returns a configuration for the "unix" attribute view.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   *
   * <p>If a "posix" view configuration not provided, the configuration defined by
   * {@link #posix() posix} will be used.
   */
  public static AttributeConfiguration unix() {
    return new DependantAttributeConfiguration("unix", "posix") {
      @Override
      protected Iterable<? extends AttributeProvider> getProviders(
          Map<String, AttributeProvider> otherProviders) {
        PosixAttributeProvider posix = (PosixAttributeProvider) otherProviders.get("posix");
        UnixAttributeProvider unix = new UnixAttributeProvider(posix);
        return ImmutableSet.of(unix);
      }
    };
  }

  /**
   * Returns a configuration for the "unix", "posix" and "owner" attribute views. Files will be
   * created with the given owner, group and permissions by default.
   */
  public static AttributeConfiguration unix(String owner, String group, String permissions) {
    AttributeConfiguration ownerView = owner(owner);
    AttributeConfiguration posixView = posix(group, permissions);
    AttributeConfiguration unixView = unix();
    return new AttributeConfigurationSet(ownerView, posixView, unixView);
  }

  /**
   * Returns a configuration for the "dos" attribute view. Files will be created with the four DOS
   * attributes all set to false.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeConfiguration dos() {
    return new ProviderAttributeConfiguration(DosAttributeProvider.INSTANCE);
  }

  /**
   * Returns a configuration for the "acl" attribute view. Files will be created with an empty ACL
   * by default.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeConfiguration acl() {
    return acl(ImmutableList.<AclEntry>of());
  }

  /**
   * Returns a configuration for the "acl" attribute view. Files will be created with the given
   * ACL by default.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeConfiguration acl(List<AclEntry> acl) {
    final ImmutableList<AclEntry> aclCopy = ImmutableList.copyOf(acl);
    return new DependantAttributeConfiguration("acl", "owner") {
      @Override
      protected Iterable<? extends AttributeProvider> getProviders(
          Map<String, AttributeProvider> otherProviders) {
        OwnerAttributeProvider owner = (OwnerAttributeProvider) otherProviders.get("owner");
        AclAttributeProvider aclProvider = new AclAttributeProvider(owner, aclCopy);
        return ImmutableSet.of(aclProvider);
      }
    };
  }

  /**
   * Returns a set of attribute views containing the "user" attribute view.
   */
  public static AttributeConfiguration user() {
    return new ProviderAttributeConfiguration(UserDefinedAttributeProvider.INSTANCE);
  }

  /**
   * Returns a default configuration for the "owner", "dos", "acl" and "user" attribute views, the
   * default set of views supported on Windows. Files will be created by default with the four
   * DOS attributes all set to false, the owner set to "user" and an empty ACL.
   */
  public static AttributeConfiguration windows() {
    return windows("owner", ImmutableList.<AclEntry>of());
  }

  /**
   * Returns a default configuration for the "owner", "dos", "acl" and "user" attribute views, the
   * default set of views supported on Windows. Files will be created by default with the four DOS
   * attributes all set to false, owner set to the given owner and the given ACL.
   */
  public static AttributeConfiguration windows(String owner, List<AclEntry> acl) {
    AttributeConfiguration ownerView = owner(owner);
    AttributeConfiguration dosView = dos();
    AttributeConfiguration aclView = acl(acl);
    AttributeConfiguration userView = user();
    return new AttributeConfigurationSet(ownerView, dosView, aclView, userView);
  }

  /**
   * Returns a configuration for the attribute view defined by the given attribute provider.
   */
  public static AttributeConfiguration view(AttributeProvider provider) {
    return views(provider);
  }

  /**
   * Returns a configuration for the attribute views defined by the given attribute providers.
   */
  public static AttributeConfiguration views(AttributeProvider... providers) {
    return new ProviderAttributeConfiguration(providers);
  }

  // restrict subclasses to this package
  AttributeConfiguration() {}

  /**
   * Returns the set of views that provides directly.
   */
  protected abstract ImmutableSet<String> provides();

  /**
   * Returns the set of view that this requires to create its views.
   */
  protected abstract ImmutableSet<String> requires();

  /**
   * Gets the providers this provides, creating them if necessary.
   */
  protected abstract Iterable<? extends AttributeProvider> getProviders(
      Map<String, AttributeProvider> otherProviders);
}
