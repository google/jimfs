package com.google.jimfs.internal;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.common.collect.ImmutableSet;

import java.nio.file.LinkOption;
import java.util.Arrays;
import java.util.Set;

/**
 * Options controlling the behavior of move and copy operations.
 *
 * @author Colin Decker
 */
final class CopyOptions extends LinkOptions {

  private static final CopyOptions MOVE = new CopyOptions(true, ImmutableSet.of());
  private static final CopyOptions COPY = new CopyOptions(false, ImmutableSet.of());

  /**
   * Creates a new options object for a move operation from the given options.
   */
  public static CopyOptions move(Object... options) {
    return from(true, Arrays.asList(options));
  }

  /**
   * Creates a new options object for a copy operation from the given options.
   */
  public static CopyOptions copy(Object... options) {
    return from(false, Arrays.asList(options));
  }

  /**
   * Creates a new options object from the given options.
   */
  private static CopyOptions from(boolean move, Iterable<?> options) {
    Set<?> optionsSet = options instanceof Set<?> ? (Set<?>) options : ImmutableSet.copyOf(options);
    if (optionsSet.isEmpty()) {
      return move ? MOVE : COPY;
    }
    return new CopyOptions(move, optionsSet);
  }

  private final boolean move;
  private final boolean copyAttributes;
  private final boolean atomicMove;
  private final boolean replaceExisting;

  private CopyOptions(boolean move, Set<?> options) {
    super(move ? ImmutableSet.of(LinkOption.NOFOLLOW_LINKS) : options);
    this.move = move;
    this.copyAttributes = options.contains(COPY_ATTRIBUTES);
    this.atomicMove = options.contains(ATOMIC_MOVE);
    this.replaceExisting = options.contains(REPLACE_EXISTING);
  }

  /**
   * Returns whether or not these options are for a move operation.
   */
  public boolean isMove() {
    return move;
  }

  /**
   * returns whether or not these options are for a copy operation.
   */
  public boolean isCopy() {
    return !move;
  }

  /**
   * Returns whether or not attributes should be copied as well.
   */
  public boolean isCopyAttributes() {
    return copyAttributes;
  }

  /**
   * Returns whether or not a move must be an atomic move.
   */
  public boolean isAtomicMove() {
    return atomicMove;
  }

  /**
   * Returns whether or not an existing file at the destination should be replaced.
   */
  public boolean isReplaceExisting() {
    return replaceExisting;
  }
}
