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

import com.google.common.base.Objects;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Abstract base implementation of {@link Path}. Implements all path query and manipulation methods,
 * leaving methods that need to access the file system itself in any way to subclasses. A
 * {@link PathService} is used to handle creating new path objects as needed.
 *
 * @author Colin Decker
 */
public abstract class JimfsPath implements Path {

  protected final PathService pathService;

  @Nullable
  private final Name root;
  private final ImmutableList<Name> names;

  public JimfsPath(PathService pathService, @Nullable Name root, Iterable<Name> names) {
    this.pathService = checkNotNull(pathService);
    this.root = root;
    this.names = ImmutableList.copyOf(names);
  }

  /**
   * Returns the root name for this path if there is one; null otherwise.
   */
  @Nullable
  public Name root() {
    return root;
  }

  /**
   * Returns the file name of this path. Unlike {@link #getFileName()}, this may return the name of
   * the root if this is a root path.
   */
  @Nullable
  public Name name() {
    if (!names.isEmpty()) {
      return Iterables.getLast(names);
    }
    return root;
  }

  /**
   * Returns the list of names (not including the root) in this path.
   */
  public ImmutableList<Name> names() {
    return names;
  }

  /**
   * Returns a view of this path as an immutable iterable of name objects (including the root).
   */
  public Iterable<Name> path() {
    return new Iterable<Name>() {
      @Override
      public Iterator<Name> iterator() {
        return new AbstractIterator<Name>() {
          private Iterator<Name> nameIterator;

          @Override
          protected Name computeNext() {
            if (nameIterator == null) {
              nameIterator = names.iterator();
              if (root != null) {
                return root;
              }
            }

            return nameIterator.hasNext() ? nameIterator.next() : endOfData();
          }
        };
      }
    };
  }

  /**
   * Returns whether or not this is the empty path, with no root and a single, empty string, name.
   */
  public boolean isEmptyPath() {
    return root == null && names.size() == 1 && names.get(0).toString().equals("");
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
    return pathService.createRoot(root);
  }

  @Override
  public JimfsPath getFileName() {
    return names.isEmpty() ? null : getName(names.size() - 1);
  }

  @Override
  public JimfsPath getParent() {
    if (names.isEmpty() || names.size() == 1 && root == null) {
      return null;
    }

    return pathService.createPath(root, names.subList(0, names.size() - 1));
  }

  @Override
  public int getNameCount() {
    return names.size();
  }

  @Override
  public JimfsPath getName(int index) {
    checkArgument(index >= 0 && index < names.size(),
        "index (%s) must be >= 0 and < name count (%s)", index, names.size());
    return pathService.createFileName(names.get(index));
  }

  @Override
  public JimfsPath subpath(int beginIndex, int endIndex) {
    checkArgument(beginIndex >= 0 && endIndex <= names.size() && endIndex > beginIndex,
        "beginIndex (%s) must be >= 0; endIndex (%s) must be <= name count (%s) and > beginIndex",
        beginIndex, endIndex, names.size());
    return pathService.createRelativePath(names.subList(beginIndex, endIndex));
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
        && getFileSystem().equals(otherPath.getFileSystem())
        && Objects.equal(root, otherPath.root)
        && startsWith(names, otherPath.names);
  }

  @Override
  public boolean startsWith(String other) {
    return startsWith(pathService.parsePath(other));
  }

  @Override
  public boolean endsWith(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      return false;
    }

    if (otherPath.isAbsolute()) {
      return compareTo(otherPath) == 0;
    }
    return startsWith(names.reverse(), otherPath.names.reverse());
  }

  @Override
  public boolean endsWith(String other) {
    return endsWith(pathService.parsePath(other));
  }

  @Override
  public JimfsPath normalize() {
    if (!isAbsolute()) {
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
        } else if (!isAbsolute()) {
          // if there's a root and we have an extra ".." that would go up above the root, ignore it
          newNames.add(name);
        }
      } else if (!SELF.equals(name)) {
        newNames.add(name);
      }
    }

    return newNames.equals(names) ? this : pathService.createPath(root, newNames);
  }

  @Override
  public JimfsPath resolve(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      throw new ProviderMismatchException(other.toString());
    }

    if (isEmptyPath() || otherPath.isAbsolute()) {
      return otherPath;
    }
    if (otherPath.isEmptyPath()) {
      return this;
    }
    return pathService.createPath(root, Iterables.concat(names, otherPath.names));
  }

  @Override
  public JimfsPath resolve(String other) {
    return resolve(pathService.parsePath(other));
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
    return resolveSibling(pathService.parsePath(other));
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
      return pathService.emptyPath();
    }

    if (isEmptyPath()) {
      return otherPath;
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

    return pathService.createRelativePath(parts);
  }

  @Override
  public File toFile() {
    // documented as unsupported for anything but the default file system
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

  private static final Ordering<Name> ROOT_ORDERING =
      Ordering.usingToString().nullsLast();
  private static final Ordering<Iterable<Name>> NAMES_ORDERING =
      Ordering.usingToString().lexicographical();

  @Override
  public int compareTo(Path other) {
    JimfsPath otherPath = checkPath(other);
    if (otherPath == null) {
      return -1;
    }
    return ComparisonChain.start()
        .compare(root, otherPath.root, ROOT_ORDERING)
        .compare(names, otherPath.names, NAMES_ORDERING)
        .result();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof JimfsPath) {
      JimfsPath other = (JimfsPath) obj;
      return getFileSystem().equals(other.getFileSystem()) && compareTo(other) == 0;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 31;
    hash = 31 * hash + getFileSystem().hashCode();
    hash = 31 * hash + (root == null ? 0 : root.hashCode());
    for (Name name : names) {
      hash = 31 * hash + name.toString().hashCode();
    }
    return hash;
  }

  @Override
  public String toString() {
    return pathService.toString(this);
  }

  @Nullable
  private JimfsPath checkPath(Path other) {
    if (checkNotNull(other) instanceof JimfsPath
        && other.getFileSystem().equals(getFileSystem())) {
      return (JimfsPath) other;
    }
    return null;
  }
}
