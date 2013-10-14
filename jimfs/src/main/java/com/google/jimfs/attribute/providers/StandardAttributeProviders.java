package com.google.jimfs.attribute.providers;

import com.google.common.collect.ImmutableMap;
import com.google.jimfs.attribute.AttributeProvider;

import javax.annotation.Nullable;

/**
 * Static registry of {@link AttributeProvider} implementations for the supported attribute views:
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
 * @author Colin Decker
 */
public final class StandardAttributeProviders {

  private StandardAttributeProviders() {}

  private static final ImmutableMap<String, AttributeProvider<?>> PROVIDERS =
      new ImmutableMap.Builder<String, AttributeProvider<?>>()
          .put("basic", new BasicAttributeProvider())
          .put("owner", new OwnerAttributeProvider())
          .put("posix", new PosixAttributeProvider())
          .put("unix", new UnixAttributeProvider())
          .put("dos", new DosAttributeProvider())
          .put("acl", new AclAttributeProvider())
          .put("user", new UserDefinedAttributeProvider())
          .build();

  /**
   * Returns the attribute provider for the given view, or {@code null} if the given view is not
   * one of the attribute views this supports.
   */
  @Nullable
  public static AttributeProvider<?> get(String view) {
    AttributeProvider<?> provider = PROVIDERS.get(view);

    if (provider == null && view.equals("unix")) {
      // create a new UnixAttributeProvider per file system, as it does some caching that should be
      // cleaned up when the file system is garbage collected
      return new UnixAttributeProvider();
    }

    return provider;
  }
}
