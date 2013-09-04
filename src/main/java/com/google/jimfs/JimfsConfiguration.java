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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.jimfs.internal.JimfsFileSystem;
import com.google.jimfs.internal.attribute.AttributeProvider;
import com.google.jimfs.internal.path.JimfsPath;
import com.google.jimfs.internal.path.Name;

import com.ibm.icu.text.Normalizer2;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.text.Collator;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Provider of configuration options for an instance of {@link JimfsFileSystem}.
 *
 * @author Colin Decker
 */
public abstract class JimfsConfiguration {

  private volatile String recognizedSeparators;
  private volatile ImmutableSet<Feature> supportedFeatures;

  /**
   * Returns the separator for the file system.
   */
  public abstract String getSeparator();

  /**
   * Returns alternate separators that are recognized when parsing a path. Returns the empty set
   * by default.
   */
  protected Iterable<String> getAlternateSeparators() {
    return ImmutableSet.of();
  }

  /**
   * Returns a string containing the separators that are recognized for this configuration. Each
   * character in the string is one separator.
   */
  public final String getRecognizedSeparators() {
    if (recognizedSeparators == null) {
      StringBuilder builder = new StringBuilder();
      builder.append(getSeparator());
      for (String separator : getAlternateSeparators()) {
        builder.append(separator);
      }
      String separators = builder.toString();
      recognizedSeparators = separators;
      return separators;
    }

    return recognizedSeparators;
  }

  /**
   * Creates a {@link Name} from the given string.
   */
  public Name createName(String name, boolean root) {
    return defaultCreateName(name);
  }

  /**
   * Creates a default name. The name's case sensitivity is determined by the presence or absence
   * of the {@link Feature#CASE_INSENSITIVE_NAMES CASE_INSENSITIVE_NAMES} feature.
   */
  protected final Name defaultCreateName(String name) {
    if (supportsFeature(Feature.CASE_INSENSITIVE_NAMES)) {
      return createCaseInsensitiveName(name);
    } else if (supportsFeature(Feature.CASE_INSENSITIVE_ASCII_NAMES)) {
      return Name.caseInsensitiveAscii(name);
    } else {
      return Name.simple(name);
    }
  }

  /**
   * Creates an immutable list of name objects from the given strings.
   */
  public final ImmutableList<Name> toNames(Iterable<String> names) {
    ImmutableList.Builder<Name> builder = ImmutableList.builder();
    for (String name : names) {
      builder.add(createName(name, false));
    }
    return builder.build();
  }

  private Name createCaseInsensitiveName(String name) {
    return Name.normalizing(name, Normalizer2.getNFKCCasefoldInstance());
  }

  private Collator createCollator() {
    Collator c = Collator.getInstance();
    c.setStrength(Collator.SECONDARY);
    return c;
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
   * Implements the file-system specific method for determining if a file is considered hidden.
   */
  public abstract boolean isHidden(Path path) throws IOException;

  /**
   * Returns the names identifying the attribute views the file system supports.
   */
  public abstract Iterable<AttributeProvider> getAttributeProviders();

  /**
   * Returns the set of file attribute views the file system supports.
   */
  public final ImmutableSet<String> supportedFileAttributeViews() {
    return ImmutableSet.copyOf(Iterables.transform(getAttributeProviders(),
        new Function<AttributeProvider, String>() {
          @Nullable
          @Override
          public String apply(AttributeProvider input) {
            return input.name();
          }
        }));
  }

  /**
   * Handles path parsing for the file system. The given list is the list of path parts provided
   * by the user, with all empty strings removed.
   */
  public abstract JimfsPath parsePath(JimfsFileSystem fileSystem, List<String> path);

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
    SECURE_DIRECTORY_STREAMS,
    /** File names are not case sensitive. */
    CASE_INSENSITIVE_NAMES,
    /** File names are not case sensitive for ASCII letters they contain. */
    CASE_INSENSITIVE_ASCII_NAMES
  }

}
