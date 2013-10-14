package com.google.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.providers.StandardAttributeProviders;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * File attribute configuration for a file system. Allows configuration of the set of attribute
 * views the file system should support, addition of custom views through
 * {@linkplain AttributeProvider attribute providers} and configuration of the default values that
 * should be set for specific attributes when new files are created.
 *
 * @author Colin Decker
 */
public final class AttributeConfiguration {

  /**
   * Returns a new, mutable configuration for the {@code basic} attribute view only. This is the
   * minimal configuration.
   */
  public static AttributeConfiguration basic() {
    return create("basic");
  }

  /**
   * Returns a new, mutable configuration for a standard set of UNIX attribute views. The included
   * views are {@code basic}, {@code owner}, {@code posix} and {@code unix}.
   */
  public static AttributeConfiguration unix() {
    return create("basic", "owner", "posix", "unix");
  }

  /**
   * Returns a new, mutable configuration for a standard set of Windows attribute views. The
   * included views are {@code basic}, {@code owner}, {@code dos}, {@code acl} and {@code user}.
   */
  public static AttributeConfiguration windows() {
    return create("basic", "owner", "dos", "acl", "user");
  }

  /**
   * Returns a new, mutable configuration for the given set of attribute views. The following views
   * are supported by default:
   *
   * <ul>
   *   <li>{@code basic}</li>
   *   <li>{@code owner}</li>
   *   <li>{@code posix}</li>
   *   <li>{@code unix}</li>
   *   <li>{@code dos}</li>
   *   <li>{@code acl}</li>
   *   <li>{@code user}</li>
   * </ul>
   *
   * <p>If the name of any other view is given, a corresponding {@link AttributeProvider} must be
   * {@linkplain #addProvider(AttributeProvider) added}.
   */
  public static AttributeConfiguration create(String... views) {
    return create(ImmutableSet.copyOf(views));
  }

  /**
   * Returns a new, mutable configuration for the given set of attribute views. The following views
   * are supported by default:
   *
   * <ul>
   *   <li>{@code basic}</li>
   *   <li>{@code owner}</li>
   *   <li>{@code posix}</li>
   *   <li>{@code unix}</li>
   *   <li>{@code dos}</li>
   *   <li>{@code acl}</li>
   *   <li>{@code user}</li>
   * </ul>
   *
   * <p>If the name of any other view is given, a corresponding {@link AttributeProvider} must be
   * {@linkplain #addProvider(AttributeProvider) added}.
   */
  public static AttributeConfiguration create(Iterable<String> views) {
    return new AttributeConfiguration(views);
  }

  private final ImmutableSet<String> views;
  private Set<AttributeProvider<?>> providers;
  private Map<String, Object> defaultValues;

  private AttributeConfiguration(Iterable<String> views) {
    this.views = ImmutableSet.copyOf(views);
  }

  /**
   * Returns all attribute providers for this configuration.
   */
  public Iterable<AttributeProvider<?>> getProviders() {
    Map<String, AttributeProvider<?>> result = new HashMap<>();

    if (providers != null) {
      for (AttributeProvider<?> provider : providers) {
        result.put(provider.name(), provider);
      }
    }

    for (String view : views) {
      addStandardProvider(result, view);
    }

    Set<String> missingViews = new HashSet<>();
    for (AttributeProvider<?> provider : result.values()) {
      for (String inheritedView : provider.inherits()) {
        if (!result.containsKey(inheritedView)) {
          missingViews.add(inheritedView);
        }
      }
    }

    // add any inherited views that were not listed directly
    for (String view : missingViews) {
      addStandardProvider(result, view);
    }

    return Collections.unmodifiableCollection(result.values());
  }

  private void addStandardProvider(Map<String, AttributeProvider<?>> result, String view) {
    AttributeProvider<?> provider = StandardAttributeProviders.get(view);

    if (provider == null) {
      if (!result.containsKey(view)) {
        throw new IllegalStateException("no provider found for attribute view '" + view + "'");
      }
    } else {
      result.put(provider.name(), provider);
    }
  }

  /**
   * Returns an unmodifiable map of default attribute values.
   */
  public Map<String, Object> getDefaultValues() {
    return defaultValues == null
        ? ImmutableMap.<String, Object>of()
        : Collections.unmodifiableMap(defaultValues);
  }

  /**
   * Adds the given attribute provider to the set of providers available to the file system.
   */
  public AttributeConfiguration addProvider(AttributeProvider<?> provider) {
    checkNotNull(provider);

    if (providers == null) {
      providers = new HashSet<>();
    }

    providers.add(provider);
    return this;
  }

  /**
   * Sets the given attribute to use the given value by default when creating new files.
   *
   * <p>For the included attribute views, default values can be set for the following attributes:
   *
   * <table>
   *   <tr>
   *     <th>Attribute</th>
   *     <th>Legal Types</th>
   *   </tr>
   *   <tr>
   *     <td>{@code "owner:owner"}</td>
   *     <td>{@code String} (user name), {@code UserPrincipal}</td>
   *   </tr>
   *   <tr>
   *     <td>{@code "posix:group"}</td>
   *     <td>{@code String} (group name), {@code GroupPrincipal}</td>
   *   </tr>
   *   <tr>
   *     <td>{@code "posix:permissions"}</td>
   *     <td>{@code String} (format "rwxrw-r--"), {@code Set<PosixFilePermission>}</td>
   *   </tr>
   *   <tr>
   *     <td>{@code "dos:readonly"}</td>
   *     <td>{@code Boolean}</td>
   *   </tr>
   *   <tr>
   *     <td>{@code "dos:hidden"}</td>
   *     <td>{@code Boolean}</td>
   *   </tr>
   *   <tr>
   *     <td>{@code "dos:archive"}</td>
   *     <td>{@code Boolean}</td>
   *   </tr>
   *   <tr>
   *     <td>{@code "dos:system"}</td>
   *     <td>{@code Boolean}</td>
   *   </tr>
   *   <tr>
   *     <td>{@code "acl:acl"}</td>
   *     <td>{@code List<AclEntry>}</td>
   *   </tr>
   * </table>
   */
  public AttributeConfiguration setDefaultValue(String attribute, Object value) {
    checkNotNull(attribute);
    checkNotNull(value);

    if (defaultValues == null) {
      defaultValues = new HashMap<>();
    }
    defaultValues.put(attribute, value);
    return this;
  }
}
