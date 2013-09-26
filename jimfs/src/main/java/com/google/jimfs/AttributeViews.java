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
 * return {@code AttributeViews} instances that define one or more attribute views. Multiple
 * {@code AttributeViews} may be combined to configure the full set of attribute views that a file
 * system supports, but providing more than one configuration that defines the same view will
 * result in an exception.
 *
 * @author Colin Decker
 */
public abstract class AttributeViews {

  /**
   * Returns a configuration for the "basic" attribute view.
   *
   * <p>This is the default, and a file system will always have a basic attribute view regardless
   * of what views are configured for it.
   */
  public static AttributeViews basic() {
    return fromProvider(BasicAttributeProvider.INSTANCE);
  }

  /**
   * Returns a configuration for the "owner" attribute view. Files will be created with the owner
   * "user" by default.
   */
  public static AttributeViews owner() {
    return owner("user");
  }

  /**
   * Returns a configuration for the "owner" attribute view. Files will be created with the given
   * owner by default.
   */
  public static AttributeViews owner(String owner) {
    return fromProvider(new OwnerAttributeProvider(owner));
  }

  /**
   * Returns a configuration for the "posix" attribute view. Files will be created with the group
   * "group" and permissions "rw-r--r--" by default.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeViews posix() {
    return posix("group", "rw-r--r--");
  }

  /**
   * Returns a configuration for the "posix" attribute view. Files will be created with the given
   * group and permissions by default.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeViews posix(final String group, final String permissions) {
    return new DependantAttributeViews("posix", "owner") {
      @Override
      public Iterable<? extends AttributeProvider> getProviders(
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
  public static AttributeViews unix() {
    return new DependantAttributeViews("unix", "posix") {
      @Override
      public Iterable<? extends AttributeProvider> getProviders(
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
  public static AttributeViews unix(String owner, String group, String permissions) {
    AttributeViews ownerView = owner(owner);
    AttributeViews posixView = posix(group, permissions);
    AttributeViews unixView = unix();
    return new AttributeViewsSet(ownerView, posixView, unixView);
  }

  /**
   * Returns a configuration for the "dos" attribute view. Files will be created with the four DOS
   * attributes all set to false.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeViews dos() {
    return fromProvider(DosAttributeProvider.INSTANCE);
  }

  /**
   * Returns a configuration for the "acl" attribute view. Files will be created with an empty ACL
   * by default.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeViews acl() {
    return acl(ImmutableList.<AclEntry>of());
  }

  /**
   * Returns a configuration for the "acl" attribute view. Files will be created with the given
   * ACL by default.
   *
   * <p>If an "owner" view configuration is not provided, the configuration defined by
   * {@link #owner() owner} will be used.
   */
  public static AttributeViews acl(List<AclEntry> acl) {
    final ImmutableList<AclEntry> aclCopy = ImmutableList.copyOf(acl);
    return new DependantAttributeViews("acl", "owner") {
      @Override
      public Iterable<? extends AttributeProvider> getProviders(
          Map<String, AttributeProvider> otherProviders) {
        OwnerAttributeProvider owner = (OwnerAttributeProvider) otherProviders.get("owner");
        AclAttributeProvider aclProvider = new AclAttributeProvider(owner, aclCopy);
        return ImmutableSet.of(aclProvider);
      }
    };
  }

  /**
   * Returns a configuration for the "user" attribute view.
   */
  public static AttributeViews user() {
    return new ProviderAttributeViews(UserDefinedAttributeProvider.INSTANCE);
  }

  /**
   * Returns a default configuration for the "owner", "dos", "acl" and "user" attribute views, the
   * default set of views supported on Windows. Files will be created by default with the four
   * DOS attributes all set to false, the owner set to "user" and an empty ACL.
   */
  public static AttributeViews windows() {
    return windows("owner", ImmutableList.<AclEntry>of());
  }

  /**
   * Returns a default configuration for the "owner", "dos", "acl" and "user" attribute views, the
   * default set of views supported on Windows. Files will be created by default with the four DOS
   * attributes all set to false, owner set to the given owner and the given ACL.
   */
  public static AttributeViews windows(String owner, List<AclEntry> acl) {
    AttributeViews ownerView = owner(owner);
    AttributeViews dosView = dos();
    AttributeViews aclView = acl(acl);
    AttributeViews userView = user();
    return new AttributeViewsSet(ownerView, dosView, aclView, userView);
  }

  /**
   * Returns a configuration for the attribute view defined by the given provider.
   */
  public static AttributeViews fromProvider(AttributeProvider provider) {
    return fromProviders(provider);
  }

  /**
   * Returns a configuration for the attribute views defined by the given providers.
   */
  public static AttributeViews fromProviders(AttributeProvider... providers) {
    return new ProviderAttributeViews(providers);
  }

  // restrict subclasses to this package
  AttributeViews() {}

  /**
   * Returns the set of views that provides directly.
   */
  abstract ImmutableSet<String> provides();

  /**
   * Returns the set of views that this requires to create its views.
   */
  abstract ImmutableSet<String> requires();

  /**
   * Gets the providers this provides, creating them if necessary.
   */
  public abstract Iterable<? extends AttributeProvider> getProviders(
      Map<String, AttributeProvider> otherProviders);
}
