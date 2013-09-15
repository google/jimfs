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
import com.google.common.collect.Sets;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.providers.BasicAttributeProvider;
import com.google.jimfs.path.PathType;

import java.nio.file.SecureDirectoryStream;
import java.util.Set;

/**
 * Provider of configuration options for a file system.
 *
 * @author Colin Decker
 */
public abstract class JimfsConfiguration {

  private final PathType pathType;
  private volatile ImmutableSet<Feature> supportedFeatures;

  protected JimfsConfiguration(PathType pathType) {
    this.pathType = checkNotNull(pathType);
  }

  /**
   * Gets the path type for this configuration.
   */
  public final PathType getPathType() {
    return pathType;
  }

  /**
   * Returns the names of the root directories for the file system.
   */
  public abstract Iterable<String> getRoots();

  /**
   * Returns the absolute path of the working directory for the file system, the directory against
   * which all relative paths are resolved. The working directory will be created along with the
   * file system.
   */
  public abstract String getWorkingDirectory();

  /**
   * Returns the attribute providers the file system supports.
   */
  public final ImmutableSet<AttributeProvider> getAllAttributeProviders() {
    BasicAttributeProvider basic = BasicAttributeProvider.INSTANCE;
    Set<AttributeProvider> providers = Sets.newHashSet(getAttributeProviders());
    providers.add(basic);
    return ImmutableSet.copyOf(providers);
  }

  /**
   * Returns the attribute providers the file system should support. The
   * {@link BasicAttributeProvider}, which the file system is guaranteed to support, need not be
   * included in the returned set of providers.
   */
  protected abstract Iterable<AttributeProvider> getAttributeProviders();


  /**
   * Returns the optional features the file system supports. By default, this returns
   * {@link Feature#SYMBOLIC_LINKS SYMBOLIC_LINKS} and {@link Feature#LINKS LINKS}.
   */
  protected Iterable<Feature> getSupportedFeatures() {
    return ImmutableSet.of(Feature.SYMBOLIC_LINKS, Feature.LINKS);
  }

  /**
   * Returns whether or not this configuration supports the given feature.
   */
  public boolean supportsFeature(Feature feature) {
    if (supportedFeatures == null) {
      ImmutableSet<Feature> featureSet = ImmutableSet.copyOf(getSupportedFeatures());
      supportedFeatures = featureSet;
      return featureSet.contains(feature);
    }

    return supportedFeatures.contains(feature);
  }

  /**
   * Features that a file system may or may not support.
   */
  public enum Feature {
    /** Symbolic links are supported. */
    SYMBOLIC_LINKS,
    /** Hard links are supported. */
    LINKS,
    /** Supports the lookup of group principals. */
    GROUPS,
    /** {@link SecureDirectoryStream} is supported. */
    SECURE_DIRECTORY_STREAMS
  }

}
