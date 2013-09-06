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

package com.google.jimfs.internal.path;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jimfs.internal.path.Name.PARENT;
import static com.google.jimfs.internal.path.Name.SELF;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.jimfs.internal.JimfsFileSystem;
import com.google.jimfs.internal.JimfsFileSystemProvider;
import com.google.jimfs.internal.LinkHandling;
import com.google.jimfs.internal.watch.AbstractWatchService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Implementation of {@link Path}.
 *
 * @author Colin Decker
 */
public final class JimfsPath implements Path {

  /**
   * Returns an empty path for the given file system.
   */
  public static JimfsPath empty(JimfsFileSystem fs) {
    // this is what an empty path seems to be in the UnixFileSystem anyway...
    return new JimfsPath(checkNotNull(fs), ImmutableList.of(fs.name("")), false);
  }

  /**
   * Returns a root path for the given file system.
   */
  public static JimfsPath root(JimfsFileSystem fs, Name name) {
    return new JimfsPath(checkNotNull(fs), ImmutableList.of(name), true);
  }

  /**
   * Returns a single name path for the given file system.
   */
  public static JimfsPath name(JimfsFileSystem fs, Name name) {
    return new JimfsPath(checkNotNull(fs), ImmutableList.of(name), false);
  }

  /**
   * Returns a path consisting of the given names and no root for the given file system.
   */
  public static JimfsPath names(JimfsFileSystem fs, Iterable<Name> names) {
    return new JimfsPath(checkNotNull(fs), names, false);
  }

  /**
   * Creates a new path with the given (optional) root and names for the given file system.
   */
  public static JimfsPath create(
      JimfsFileSystem fs, Iterable<Name> path, boolean absolute) {
    return new JimfsPath(checkNotNull(fs), path, absolute);
  }

  private final JimfsFileSystem fs;

  private final ImmutableList<Name> path;
  private final ImmutableList<Name> names;
  private final boolean absolute;

  /**
   * This constructor is for internal use and testing only.
   */
  @VisibleForTesting
  public JimfsPath(JimfsFileSystem fs, Iterable<Name> path, boolean absolute) {
    this.fs = fs;
    this.path = ImmutableList.copyOf(path);
    this.names = absolute ? this.path.subList(1, this.path.size()) : this.path;
    this.absolute = absolute;
  }

  @Override
  public JimfsFileSystem getFileSystem() {
    return fs;
  }

  /**
   * Returns the root name for this path if there is one; null otherwise.
   */
  @Nullable
  public Name root() {
    return absolute ? path.get(0) : null;
  }

  @Override
  public boolean isAbsolute() {
    return absolute;
  }

  @Override
  public JimfsPath getRoot() {
    if (!absolute) {
      return null;
    }
    return new JimfsPath(fs, ImmutableList.of(root()), true);
  }

  @Override
  public JimfsPath getFileName() {
    return names.isEmpty()
        ? null
        : subpath(names.size() - 1, names.size());
  }

  @Override
  public JimfsPath getParent() {
    if (path.size() <= 1) {
      return null;
    }

    return new JimfsPath(fs, path.subList(0, path.size() - 1), absolute);
  }

  @Override
  public int getNameCount() {
    return names.size();
  }

  @Override
  public JimfsPath getName(int index) {
    checkArgument(index >= 0 && index < names.size(),
        "index (%s) must be >= 0 and < name count (%s)", index, names.size());
    return name(fs, names.get(index));
  }

  @Override
  public JimfsPath subpath(int beginIndex, int endIndex) {
    checkArgument(beginIndex >= 0 && endIndex <= names.size() && endIndex > beginIndex,
        "beginIndex (%s) must be >= 0; endIndex (%s) must be <= name count (%s) and > beginIndex",
        beginIndex, endIndex, names.size());
    return names(fs, names.subList(beginIndex, endIndex));
  }

  /**
   * Returns true if list starts with all elements of other in the same order.
   */
  private static boolean startsWith(List<?> list, List<?> other) {
    return list.size() >= other.size() && list.subList(0, other.size()).equals(other);
  }

  @Override
  public boolean startsWith(Path other) {
    checkNotNull(other);
    if (!(other instanceof JimfsPath)) {
      return false;
    }

    JimfsPath otherPath = (JimfsPath) other;
    return fs.equals(otherPath.fs)
        && absolute == otherPath.absolute
        && startsWith(path, otherPath.path);
  }

  @Override
  public boolean startsWith(String other) {
    return startsWith(fs.getPath(other));
  }

  @Override
  public boolean endsWith(Path other) {
    checkNotNull(other);
    if (!(other instanceof JimfsPath)) {
      return false;
    }

    JimfsPath otherPath = (JimfsPath) other;
    if (otherPath.absolute) {
      return equals(otherPath);
    }
    return startsWith(names.reverse(), otherPath.names.reverse());
  }

  @Override
  public boolean endsWith(String other) {
    return endsWith(fs.getPath(other));
  }

  @Override
  public JimfsPath normalize() {
    if (!absolute) {
      if (getNameCount() <= 1) {
        return this;
      }
    } else if (getNameCount() == 0) {
      return this;
    }

    Deque<Name> newNames = new ArrayDeque<>();
    for (Name name : names) {
      if (name.equals(PARENT)) {
        Name lastName = Iterables.getLast(newNames, null);
        if (lastName != null && !lastName.equals(PARENT)) {
          newNames.removeLast();
        } else if (!absolute) {
          // if there's a root and we have an extra ".." that would go up above the root, ignore it
          newNames.add(name);
        }
      } else if (!SELF.equals(name)) {
        newNames.add(name);
      }
    }

    if (absolute) {
      newNames.addFirst(path.get(0));
    }
    return newNames.equals(path) ? this : new JimfsPath(fs, newNames, absolute);
  }

  @Override
  public JimfsPath resolve(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      throw new ProviderMismatchException(other.toString());
    }

    if (otherPath.isAbsolute()) {
      return otherPath;
    }
    if (otherPath.getNameCount() == 0
        || (other.getNameCount() == 1 && other.getFileName().toString().equals(""))) {
      return this;
    }
    return new JimfsPath(fs, Iterables.concat(path, otherPath.path), absolute);
  }

  @Override
  public JimfsPath resolve(String other) {
    return resolve(fs.getPath(other));
  }

  @Override
  public JimfsPath resolveSibling(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      throw new ProviderMismatchException(other.toString());
    }

    if (otherPath.isAbsolute()) {
      return otherPath;
    }
    JimfsPath parent = getParent();
    if (parent == null) {
      return otherPath;
    }
    return parent.resolve(other);
  }

  @Override
  public JimfsPath resolveSibling(String other) {
    return resolveSibling(fs.getPath(other));
  }

  @Override
  public JimfsPath relativize(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      throw new ProviderMismatchException(other.toString());
    }

    checkArgument(Objects.equal(root(), otherPath.root()), "Cannot relativize %s against %s--" +
        "both paths must have no root or the same root.", other, this);

    if (equals(other)) {
      return empty(fs);
    }

    ImmutableList<Name> otherNames = otherPath.names;
    int sharedSubsequenceLength = 0;
    for (int i = 0; i < Math.min(getNameCount(), otherNames.size()); i++) {
      if (names.get(i).equals(otherNames.get(i))) {
        sharedSubsequenceLength++;
      } else {
        break;
      }
    }

    int extraNamesInThis = Math.max(0, getNameCount() - sharedSubsequenceLength);

    Iterable<Name> extraNamesInOther = (otherNames.size() <= sharedSubsequenceLength)
        ? ImmutableList.<Name>of()
        : otherNames.subList(sharedSubsequenceLength, otherNames.size());

    List<Name> parts = new ArrayList<>();

    // add .. for each extra name in this path
    Iterables.addAll(parts, Collections.nCopies(extraNamesInThis, PARENT));
    // add each extra name in the other path
    Iterables.addAll(parts, extraNamesInOther);

    return names(fs, parts);
  }

  @Override
  public URI toUri() {
    return getFileSystem().getUri(this);
  }

  @Override
  public JimfsPath toAbsolutePath() {
    return fs.getWorkingDirectory().resolve(this);
  }

  @Override
  public JimfsPath toRealPath(LinkOption... options) throws IOException {
    return JimfsFileSystemProvider.getFileTree(this)
        .toRealPath(this, LinkHandling.fromOptions(options));
  }

  @Override
  public File toFile() {
    // documented as unsupported for anything but the default file system
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
      WatchEvent.Modifier... modifiers) throws IOException {
    checkNotNull(modifiers);
    return register(watcher, events);
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
    checkNotNull(watcher);
    checkNotNull(events);
    if (!(watcher instanceof AbstractWatchService)) {
      throw new IllegalArgumentException(
          "watcher (" + watcher + ") is not associated with this file system");
    }

    AbstractWatchService service = (AbstractWatchService) watcher;
    return service.register(this, Arrays.asList(events));
  }

  @Override
  public Iterator<Path> iterator() {
    return asList().iterator();
  }

  private List<Path> asList() {
    return new AbstractList<Path>() {
      @Override
      public Path get(int index) {
        return getName(index);
      }

      @Override
      public int size() {
        return getNameCount();
      }
    };
  }

  /**
   * Returns the list of {@link Name} objects that this path consists of, not including the root
   * name.
   */
  public ImmutableList<Name> asNameList() {
    return names;
  }

  /**
   * Returns an iterable of all names in the path, including the root if present.
   */
  public ImmutableList<Name> allNames() {
    return path;
  }

  private static final Ordering<Iterable<Name>> ORDERING =
      Ordering.usingToString().lexicographical();

  @Override
  public int compareTo(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      return -1;
    }
    return ComparisonChain.start()
        .compareTrueFirst(absolute, otherPath.absolute)
        .compare(path, otherPath.path, ORDERING)
        .result();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof JimfsPath) {
      JimfsPath other = (JimfsPath) obj;
      return fs.equals(other.fs)
          && compareTo(other) == 0;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 31;
    hash = 31 * hash + fs.hashCode();
    hash = 31 * hash + (absolute ? 0 : 1);
    for (Name name : path) {
      hash = 31 * hash + name.toString().hashCode();
    }
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    Name root = root();
    if (root != null) {
      String rootString = root.toString();
      builder.append(rootString);
      if (getNameCount() > 0 && !rootString.endsWith(fs.getSeparator())) {
        builder.append(fs.getSeparator());
      }
    }
    Joiner.on(fs.getSeparator())
        .appendTo(builder, names);
    return builder.toString();
  }

  @Nullable
  private JimfsPath checkPath(Path other) {
    checkNotNull(other);
    if (other instanceof JimfsPath && other.getFileSystem().equals(getFileSystem())) {
      return (JimfsPath) other;
    }
    return null;
  }
}
