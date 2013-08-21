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

package com.google.common.io.jimfs.path;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.jimfs.path.Name.PARENT;
import static com.google.common.io.jimfs.path.Name.SELF;
import static com.google.common.io.jimfs.util.ExceptionHelpers.throwProviderMismatch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.jimfs.JimfsFileSystem;
import com.google.common.io.jimfs.file.FileContent;
import com.google.common.io.jimfs.file.LinkHandling;

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
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Implementation of {@link Path}. Also implements {@link FileContent}, as the content of a
 * symbolic link is a path.
 *
 * @author Colin Decker
 */
public final class JimfsPath implements Path, FileContent {

  /**
   * Returns an empty path for the given file system.
   */
  public static JimfsPath empty(JimfsFileSystem fs) {
    // this is what an empty path seems to be in the UnixFileSystem anyway...
    return new JimfsPath(checkNotNull(fs), null, ImmutableList.of(fs.name("")));
  }

  /**
   * Returns a root path for the given file system.
   */
  public static JimfsPath root(JimfsFileSystem fs, Name name) {
    return new JimfsPath(checkNotNull(fs), name, ImmutableList.<Name>of());
  }

  /**
   * Returns a single name path for the given file system.
   */
  public static JimfsPath name(JimfsFileSystem fs, Name name) {
    return new JimfsPath(checkNotNull(fs), null, ImmutableList.of(name));
  }

  /**
   * Returns a path consisting of the given names and no root for the given file system.
   */
  public static JimfsPath names(JimfsFileSystem fs, Iterable<Name> names) {
    return new JimfsPath(checkNotNull(fs), null, names);
  }

  /**
   * Creates a new path with the given (optional) root and names for the given file system.
   */
  public static JimfsPath create(
      JimfsFileSystem fs, @Nullable Name root, Iterable<Name> names) {
    return new JimfsPath(checkNotNull(fs), root, names);
  }

  private final JimfsFileSystem fs;

  @Nullable
  private final Name root;
  private final ImmutableList<Name> names;

  /**
   * This constructor is for internal use and testing only.
   */
  @VisibleForTesting
  public JimfsPath(JimfsFileSystem fs, @Nullable Name root, Iterable<Name> names) {
    this.fs = fs;
    this.root = root;
    this.names = ImmutableList.copyOf(names);
  }

  @Override
  public JimfsPath copy() {
    // immutable
    return this;
  }

  @Override
  public int sizeInBytes() {
    return 0;
  }

  @Override
  public JimfsFileSystem getFileSystem() {
    return fs;
  }

  @Override
  public boolean isAbsolute() {
    return root != null;
  }

  @Override
  public JimfsPath getRoot() {
    if (root == null) {
      return null;
    }
    return names.isEmpty() ? this : root(fs, root);
  }

  @Override
  public JimfsPath getFileName() {
    switch (names.size()) {
      case 0: return null;
      case 1:
        if (root == null) {
          return this;
        }
        // else fall through to default
      default:
        return name(fs, Iterables.getLast(names));
    }
  }

  @Override
  public JimfsPath getParent() {
    switch (names.size()) {
      case 0: return null;
      case 1: return root == null ? null : root(fs, root);
      default: return create(fs, root, names.subList(0, names.size() - 1));
    }
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
    JimfsPath otherPath = checkPath(other);
    return otherPath != null
        && Objects.equal(root, otherPath.root)
        && startsWith(names, otherPath.names);
  }

  @Override
  public boolean startsWith(String other) {
    return startsWith(fs.getPath(other));
  }

  @Override
  public boolean endsWith(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      return false;
    }

    if (otherPath.root != null) {
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
    if (root == null) {
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
        } else if (root == null) {
          // if there's a root and we have an extra ".." that would go up above the root, ignore it
          newNames.add(name);
        }
      } else if (!SELF.equals(name)) {
        newNames.add(name);
      }
    }
    return newNames.equals(names) ? this : new JimfsPath(fs, root, newNames);
  }

  @Override
  public JimfsPath resolve(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      throw throwProviderMismatch(other);
    }

    if (otherPath.isAbsolute()) {
      return otherPath;
    }
    if (otherPath.getNameCount() == 0
        || (other.getNameCount() == 1 && other.getFileName().toString().equals(""))) {
      return this;
    }
    return new JimfsPath(fs, root, Iterables.concat(names, otherPath.names));
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
      throw throwProviderMismatch(other);
    }

    checkArgument(Objects.equal(root, otherPath.root), "Cannot relativize %s against %s--" +
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
    String uri = fs.provider().getScheme() + "://" + fs + "/" + toAbsolutePath().toString();
    return URI.create(uri);
  }

  @Override
  public JimfsPath toAbsolutePath() {
    return isAbsolute()
        ? this
        : fs.getWorkingDirectory().getBasePath().resolve(this);
  }

  @Override
  public JimfsPath toRealPath(LinkOption... options) throws IOException {
    return fs.getFileTree(this).toRealPath(this, LinkHandling.fromOptions(options));
  }

  @Override
  public File toFile() {
    // documented as unsupported for anything but the default file system
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
      WatchEvent.Modifier... modifiers) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
    throw new UnsupportedOperationException();
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
   * Returns the root name, or {@code null} if this path does not have a root.
   */
  @Nullable
  public Name getRootName() {
    return root;
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
  public Iterable<Name> allNames() {
    return Iterables.concat(Optional.fromNullable(root).asSet(), names);
  }

  @Override
  public int compareTo(Path other) {
    // TODO(cgdecker): should this ensure that it considers absolute paths before relative
    // or anything like that?
    return toString().compareTo(other.toString());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof JimfsPath) {
      JimfsPath other = (JimfsPath) obj;
      if (fs.equals(other.fs)) {
        // equality of paths should be based on the string value of each component, not the
        // equality used for lookup
        String rootString = root == null ? null : root.toString();
        String otherRootString = other.root == null ? null : other.root.toString();
        if (Objects.equal(rootString, otherRootString) && names.size() == other.names.size()) {
          for (int i = 0; i < names.size(); i++) {
            if (!names.get(i).toString().equals(other.names.get(i).toString())) {
              return false;
            }
          }
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 31;
    hash = 31 * hash + fs.hashCode();
    hash = 31 * hash + (root == null ? 0 : root.toString().hashCode());
    for (Name name : names) {
      hash = 31 * hash + name.toString().hashCode();
    }
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (root != null) {
      builder.append(root);
      if (getNameCount() > 0 && !root.toString().endsWith(fs.getSeparator())) {
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
