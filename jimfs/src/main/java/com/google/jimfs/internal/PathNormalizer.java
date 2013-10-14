package com.google.jimfs.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.jimfs.Normalization;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Normalizer for path names that applies a sequence of {@linkplain Normalization normalizations}
 * to names.
 *
 * @author Colin Decker
 */
final class PathNormalizer {

  private static final PathNormalizer NONE = new PathNormalizer(ImmutableSet.<Normalization>of());

  /**
   * Returns a normalizer that does no normalizations.
   */
  public static PathNormalizer none() {
    return NONE;
  }

  /**
   * Returns a normalizer using the given set of normalizations.
   */
  @VisibleForTesting
  static PathNormalizer create(Normalization... normalizations) {
    return create(ImmutableSet.copyOf(normalizations));
  }

  /**
   * Returns a normalizer using the given set of normalizations.
   */
  public static PathNormalizer create(Collection<Normalization> normalizations) {
    if (normalizations.isEmpty() || normalizations.contains(Normalization.NONE)) {
      return NONE;
    }

    return new PathNormalizer(normalizations);
  }

  private final ImmutableSet<Normalization> normalizations;

  private PathNormalizer(Collection<Normalization> normalizations) {
    // sort the normalizations to ensure that normalization happens before case folding
    this.normalizations = ImmutableSet.copyOf(
        Ordering.natural().sortedCopy(normalizations));
  }

  /**
   * Normalizes the given string.
   */
  public String normalize(String string) {
    if (normalizations.isEmpty()) {
      return string;
    }

    String result = string;
    for (Normalization normalization : normalizations) {
      result = normalization.normalize(result);
    }
    return result;
  }

  /**
   * Compiles a regex {@link Pattern} which should approximate the behavior of this normalizer when
   * matching.
   */
  @SuppressWarnings("MagicConstant")
  public Pattern compilePattern(String regex) {
    int flags = 0;
    for (Normalization normalization : normalizations) {
      flags |= normalization.getPatternFlags();
    }
    return Pattern.compile(regex, flags);
  }
}
