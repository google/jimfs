package com.google.common.io.jimfs;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.List;

/**
 * Provider of configuration options for an instance of {@link JimfsFileSystem}.
 *
 * @author Colin Decker
 */
abstract class JimfsConfiguration {

  private volatile AttributeManager attributeManager;
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
   * Returns whether or not file lookup is case sensitive. Default is {@code false}.
   */
  public boolean isLookupCaseSensitive() {
    return false;
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
  protected abstract Iterable<AttributeProvider> getAttributeProviders();

  /**
   * Gets the attribute manager for this configuration.
   */
  public final AttributeManager getAttributeManager() {
    if (attributeManager == null) {
      AttributeManager manager = new AttributeManager(getAttributeProviders());
      attributeManager = manager;
      return manager;
    }

    return attributeManager;
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
    SECURE_DIRECTORY_STREAMS
  }

}
