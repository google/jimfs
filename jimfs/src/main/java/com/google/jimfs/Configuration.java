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
import static com.google.common.base.Preconditions.checkState;
import static com.google.jimfs.Normalization.CASE_FOLD_ASCII;
import static com.google.jimfs.Normalization.NORMALIZE_NFC;
import static com.google.jimfs.Normalization.NORMALIZE_NFD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Configuration for an in-memory file system instance. Instances of this class are mutable.
 *
 * @author Colin Decker
 */
public final class Configuration {

  /**
   * Returns a new mutable {@link Configuration} instance with defaults for a UNIX-like file
   * system. Legal paths are described by {@link PathType#unix()}. Only one root, "/", is allowed.
   *
   * <h3>Default configuration</h3>
   *
   * <p>If no changes are made, a file system created with this configuration:
   *
   * <ul>
   *   <li>uses "/" as the path name separator (see {@link PathType#unix()} for more information on
   *   the path format)</li>
   *   <li>has root "/" and working directory "/work"</li>
   *   <li>supports symbolic links and hard links</li>
   *   <li>does case-sensitive lookup</li>
   *   <li>supports only the {@linkplain BasicFileAttributeView basic} file attribute view</li>
   * </ul>
   *
   * <h3>Advanced configuration</h3>
   *
   * These defaults balance working like a UNIX file system with avoiding overhead that may not be
   * needed such as the full set of UNIX file attribute views. The returned configuration can be
   * altered to suit your needs.
   *
   * <p>Example:
   *
   * <pre>
   *   Configuration config = Configuration.unix()
   *       .setAttributeViews(AttributeView.unix()) // full set of UNIX attribute views
   *       .setWorkingDirectory("/home/user") // different working dir  </pre>
   */
  public static Configuration unix() {
    return create(PathType.unix())
        .addRoots("/")
        .setWorkingDirectory("/work");
  }

  /**
   * Returns a new mutable {@link Configuration} instance with defaults for a Mac OS X-like file
   * system. Legal paths are described by {@link PathType#unix()}. Only one root, "/", is allowed.
   *
   * <p>The primary differences between this configuration and the default {@link #unix()}
   * configuration are that this configuration does Unicode normalization on the string form of
   * {@code Path} objects it creates and that it does case insensitive file lookup.
   *
   * <h3>Default configuration</h3>
   *
   * <p>If no changes are made, a file system created with this configuration:
   *
   * <ul>
   *   <li>uses "/" as the path name separator (see {@link PathType#unix()} for more information
   *   on the path format)</li>
   *   <li>has root "/" and working directory "/work"</li>
   *   <li>supports symbolic links and hard links</li>
   *   <li>does Unicode normalization on paths, both for lookup and for {@code Path} objects</li>
   *   <li>does case-insensitive (for ASCII characters only) lookup</li>
   *   <li>supports only the {@linkplain BasicFileAttributeView basic} file attribute view</li>
   * </ul>
   *
   * <h3>Advanced configuration</h3>
   *
   * These defaults balance working like an OS X file system with avoiding overhead that may not be
   * needed such as the full set of UNIX file attribute views. The returned configuration can be
   * altered to suit your needs.
   *
   * <p>Example:
   *
   * <pre>
   *   Configuration config = Configuration.osx()
   *       .setAttributeViews(AttributeView.unix()) // full set of UNIX attribute views
   *       .setWorkingDirectory("/Users/user") // different working dir
   *
   *   // use full Unicode case insensitivity for lookups; requires ICU4J
   *   config.setPathCanonicalNormalization(
   *       Normalization.NORMALIZE_NFD, Normalization.CASE_FOLD); </pre>
   */
  public static Configuration osx() {
    return unix()
        .setPathDisplayNormalization(NORMALIZE_NFC) // matches new JDK behavior as of 1.7u40
        .setPathCanonicalNormalization(NORMALIZE_NFD, CASE_FOLD_ASCII);
  }

  /**
   * Returns a new {@link Configuration} with defaults for a Windows-like file system. Legal roots
   * and paths are described by {@link PathType#windows()}.
   *
   * <h3>Default configuration</h3>
   *
   * <p>If no changes are made, a file system created with this configuration:
   *
   * <ul>
   *   <li>uses "\" as the path name separator and recognizes "/" as a separator when parsing
   *   paths (see {@link PathType#windows()} for more information on path format)</li>
   *   <li>has root "C:\" and working directory "C:\work"</li>
   *   <li>supports symbolic and hard links</li>
   *   <li>does Unicode normalization on paths for lookup</li>
   *   <li>uses case-insensitive (for ASCII characters only) file lookup</li>
   *   <li>supports only the {@linkplain BasicFileAttributeView basic} file attribute view</li>
   * </ul>
   *
   * <h3>Advanced configuration</h3>
   *
   * These defaults balance working like a Windows file system with avoiding overhead that may not
   * be needed such as the full set of Windows file attribute views. The returned configuration can
   * be altered to suit your needs.
   *
   * <p>Example:
   *
   * <pre>
   *   Configuration config = Configuration.windows()
   *       .setAttributeViews(AttributeView.windows()) // full set of Windows attribute views
   *       .setWorkingDirectory("C:\\Users\dir") // different working dir
   *
   *   // use full Unicode case insensitivity for lookups; requires ICU4J
   *   config.setPathCanonicalNormalization(Normalization.CASE_FOLD); </pre>
   */
  public static Configuration windows() {
    return create(PathType.windows())
        .addRoots("C:\\")
        .setWorkingDirectory("C:\\work")
        .setPathCanonicalNormalization(CASE_FOLD_ASCII)
        // Windows Path objects are case insensitive for equality
        .setPathEqualityUsesCanonicalForm();
  }

  /**
   * Returns a new {@link Configuration} using the given path type. The returned configuration has
   * no other options set; at a minimum, a root must be added before creating a file system.
   */
  public static Configuration create(PathType pathType) {
    return new Configuration(pathType);
  }

  private final PathType pathType;
  private ImmutableSet<Normalization> pathDisplayNormalization = ImmutableSet.of();
  private ImmutableSet<Normalization> pathCanonicalNormalization = ImmutableSet.of();
  private boolean pathEqualityUsesCanonicalForm = false;

  private final Set<String> roots = new LinkedHashSet<>();
  private String workingDirectory;

  private AttributeViews attributes = AttributeViews.basic();
  private Set<Jimfs.Feature> supportedFeatures = ImmutableSet.copyOf(
      EnumSet.allOf(Jimfs.Feature.class));

  Configuration(PathType pathType) {
    this.pathType = checkNotNull(pathType);
  }

  /**
   * Returns the configured path type for the file system.
   */
  public PathType getPathType() {
    return pathType;
  }

  /**
   * Returns the normalization that should be done when creating {@code Path} objects.
   */
  public ImmutableSet<Normalization> getPathDisplayNormalization() {
    return pathDisplayNormalization;
  }

  /**
   * Returns the normalization that should used internally for file lookups.
   */
  public ImmutableSet<Normalization> getPathCanonicalNormalization() {
    return pathCanonicalNormalization;
  }

  /**
   * Returns whether or not path equality should use the canonical form of names or not.
   */
  public boolean getPathEqualityUsesCanonicalForm() {
    return pathEqualityUsesCanonicalForm;
  }

  /**
   * Returns the configured roots for the file system.
   */
  public ImmutableList<String> getRoots() {
    return ImmutableList.copyOf(roots);
  }

  /**
   * Returns the configured working directory for the file system.
   */
  public String getWorkingDirectory() {
    if (workingDirectory == null) {
      String firstRoot = roots.iterator().next();
      return firstRoot + pathType.getSeparator() + "work";
    }
    return workingDirectory;
  }

  /**
   * Returns the configured set of attribute views for the file system.
   */
  public AttributeViews getAttributeViews() {
    return attributes;
  }

  /**
   * Returns the configured set of optional features the file system should support.
   */
  public Set<Jimfs.Feature> getSupportedFeatures() {
    return Collections.unmodifiableSet(supportedFeatures);
  }

  /**
   * Sets the normalization that should should be used for {@code toString()} form of {@code Path}
   * objects created by the file system. Unless {@link #setPathEqualityUsesCanonicalForm()} is set,
   * the display normalization is also used for determining equality and sort order of path
   * objects.
   *
   * <p>The given normalizations may include either {@link Normalization#NONE NONE} or at most one
   * of the Unicode normalizations ({@link Normalization#NORMALIZE_NFC NORMALIZE_NFC} and
   * {@link Normalization#NORMALIZE_NFD NORMALIZE_NFD}) and at most one of the case folding
   * normalizations ({@link Normalization#CASE_FOLD CASE_FOLD} and
   * {@link Normalization#CASE_FOLD_ASCII CASE_FOLD_ASCII}).
   *
   * @throws IllegalArgumentException if the given set of normalizations is invalid
   */
  public Configuration setPathDisplayNormalization(
      Normalization first, Normalization... more) {
    List<Normalization> normalizations = Lists.asList(first, more);
    checkNormalizations(normalizations);
    this.pathDisplayNormalization = ImmutableSet.copyOf(normalizations);
    return this;
  }

  /**
   * Sets the canonical normalization of {@code Path} objects created by the file system. The
   * canonical normalization is used to determine equality of two filenames when doing file lookup,
   * creation, etc. It can also be used for determining equality and sort order of path objects by
   * setting {@link #setPathEqualityUsesCanonicalForm()}.
   *
   * <p>The given normalizations may include either {@link Normalization#NONE NONE} or at most one
   * of the Unicode normalizations ({@link Normalization#NORMALIZE_NFC NORMALIZE_NFC} and
   * {@link Normalization#NORMALIZE_NFD NORMALIZE_NFD}) and at most one of the case folding
   * normalizations ({@link Normalization#CASE_FOLD CASE_FOLD} and
   * {@link Normalization#CASE_FOLD_ASCII CASE_FOLD_ASCII}).
   *
   * @throws IllegalArgumentException if the given set of normalizations is invalid
   */
  public Configuration setPathCanonicalNormalization(Normalization first, Normalization... more) {
    List<Normalization> normalizations = Lists.asList(first, more);
    checkNormalizations(normalizations);
    this.pathCanonicalNormalization = ImmutableSet.copyOf(normalizations);
    return this;
  }

  /**
   * Sets the file system to use the canonical normalization of filenames to determine the
   * equality and sort order of {@code Path} objects rather than using the string form of the path.
   */
  public Configuration setPathEqualityUsesCanonicalForm() {
    pathEqualityUsesCanonicalForm = true;
    return this;
  }

  private void checkNormalizations(List<Normalization> normalizations) {
    Normalization none = null;
    Normalization normalization = null;
    Normalization caseFold = null;
    for (Normalization n : normalizations) {
      checkNotNull(n);
      checkNormalizationNotSet(n, none);

      switch (n) {
        case NONE:
          none = n;
          break;
        case NORMALIZE_NFC:
        case NORMALIZE_NFD:
          checkNormalizationNotSet(n, normalization);
          normalization = n;
          break;
        case CASE_FOLD:
        case CASE_FOLD_ASCII:
          checkNormalizationNotSet(n, caseFold);
          caseFold = n;
      }
    }
  }

  private static void checkNormalizationNotSet(Normalization n, @Nullable Normalization set) {
    if (set != null) {
      throw new IllegalArgumentException("can't set normalization " + n
          + ": normalization " + set + " already set");
    }
  }

  /**
   * Adds the given root directories to the file system.
   *
   * @throws IllegalStateException if the path type does not allow multiple roots
   */
  public Configuration addRoots(String first, String... more) {
    List<String> roots = Lists.asList(first, more);
    checkState(this.roots.size() + roots.size() == 1 || pathType.allowsMultipleRoots(),
        "this path type does not allow multiple roots");
    for (String root : roots) {
      checkState(!this.roots.contains(root), "root " + root + " is already configured");
      this.roots.add(checkNotNull(root));
    }
    return this;
  }

  /**
   * Sets the working directory for the file system.
   *
   * <p>If not set, the default working directory will be a directory called "work" located in
   * the first root directory in the list of roots.
   */
  public Configuration setWorkingDirectory(String workingDirectory) {
    this.workingDirectory = checkNotNull(workingDirectory);
    return this;
  }

  /**
   * Sets the attribute views to use for the file system.
   *
   * <p>The default is the {@link AttributeViews#basic() basic} view only, to minimize overhead of
   * storing attributes when other attributes aren't needed.
   */
  public Configuration setAttributeViews(AttributeViews... views) {
    if (views.length == 0) {
      this.attributes = AttributeViews.basic();
    } else {
      this.attributes = new AttributeViewsSet(views);
    }
    return this;
  }

  /**
   * Sets the optional features the file system should support. Any supported features that were
   * previously set are replaced.
   */
  public Configuration setSupportedFeatures(Jimfs.Feature... features) {
    supportedFeatures = ImmutableSet.copyOf(features);
    return this;
  }
}
