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

package com.google.jimfs.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jimfs.path.PathType.ParseResult;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.jimfs.path.CaseSensitivity;
import com.google.jimfs.path.PathType;

import com.ibm.icu.text.Normalizer2;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.PathMatcher;

import javax.annotation.Nullable;

/**
 * Service for creating {@link JimfsPath} instances and handling other path-related operations.
 *
 * @author Colin Decker
 */
abstract class PathService {

  private final PathType type;
  private final NameFactory nameFactory;

  protected PathService(PathType type) {
    this.type = checkNotNull(type);
    switch (type.getCaseSensitivity()) {
      case CASE_SENSITIVE:
        this.nameFactory = NameFactory.CASE_SENSITIVE;
        break;
      case CASE_INSENSITIVE_ASCII:
        this.nameFactory = NameFactory.CASE_INSENSITIVE_ASCII;
        break;
      case CASE_INSENSITIVE_UNICODE:
        this.nameFactory = NameFactory.CASE_INSENSITIVE_UNICODE;
        break;
      default:
        throw new AssertionError();
    }
  }

  private volatile JimfsPath emptyPath;

  /**
   * Returns the default path separator.
   */
  public String getSeparator() {
    return type.getSeparator();
  }

  /**
   * Returns an empty path which has a single name, the empty string.
   */
  public final JimfsPath emptyPath() {
    JimfsPath result = emptyPath;
    if (result == null) {
      // use createPathInternal to avoid recursive call from createPath()
      result = createPathInternal(null, ImmutableList.of(nameFactory.name("")));
      emptyPath = result;
      return result;
    }
    return result;
  }

  /**
   * Returns the {@link Name} form of the given string.
   */
  public Name name(String name) {
    return nameFactory.name(name);
  }

  /**
   * Returns the {@link Name} forms of the given strings.
   */
  public Iterable<Name> names(Iterable<String> names) {
    return nameFactory.names(names);
  }

  /**
   * Returns a root path with the given name.
   */
  public final JimfsPath createRoot(Name root) {
    return createPath(checkNotNull(root), ImmutableList.<Name>of());
  }

  /**
   * Returns a single filename path with the given name.
   */
  public final JimfsPath createFileName(Name name) {
    return createPath(null, ImmutableList.of(name));
  }

  /**
   * Returns a relative path with the given names.
   */
  public final JimfsPath createRelativePath(Iterable<Name> names) {
    return createPath(null, ImmutableList.copyOf(names));
  }

  /**
   * Returns a path with the given root (or no root, if null) and the given names.
   */
  public final JimfsPath createPath(@Nullable Name root, Iterable<Name> names) {
    ImmutableList<Name> nameList = ImmutableList.copyOf(Iterables.filter(names, NOT_EMPTY));
    if (root == null && nameList.isEmpty()) {
      // ensure the canonical empty path (one empty string name) is used rather than a path with
      // no root and no names
      return emptyPath();
    }
    return createPathInternal(root, nameList);
  }

  /**
   * Returns a path with the given root (or no root, if null) and the given names.
   */
  protected abstract JimfsPath createPathInternal(@Nullable Name root, Iterable<Name> names);

  /**
   * Parses the given strings as a path.
   */
  public final JimfsPath parsePath(String first, String... more) {
    String joined = type.joiner()
        .join(Iterables.filter(Lists.asList(first, more), NOT_EMPTY));
    return toPath(type.parsePath(joined));
  }

  private JimfsPath toPath(ParseResult parsed) {
    Name root = parsed.root() == null ? null : nameFactory.name(parsed.root());
    Iterable<Name> names = nameFactory.names(parsed.names());
    return createPath(root, names);
  }

  /**
   * Returns the string form of the given path.
   */
  public final String toString(JimfsPath path) {
    Name root = path.root();
    String rootString = root == null ? null : root.toString();
    Iterable<String> names = Iterables.transform(path.names(), Functions.toStringFunction());
    return type.toString(rootString, names);
  }

  /**
   * Returns the URI for the given path. The given file system URI is the base against which the
   * path is resolved to create the returned URI.
   */
  public final URI toUri(URI fileSystemUri, JimfsPath path) {
    checkArgument(path.isAbsolute(), "path (%s) must be absolute", path);
    String root = String.valueOf(path.root());
    Iterable<String> names = Iterables.transform(path.names(), Functions.toStringFunction());
    return type.toUri(fileSystemUri, root, names);
  }

  /**
   * Converts the path of the given URI into a path for this file system.
   */
  public final JimfsPath fromUri(URI uri) {
    return toPath(type.fromUri(uri));
  }

  /**
   * Returns a {@link PathMatcher} for the given syntax and pattern as specified by
   * {@link FileSystem#getPathMatcher(String)}.
   */
  public final PathMatcher createPathMatcher(String syntaxAndPattern) {
    return PathMatchers.getPathMatcher(
        syntaxAndPattern, type.getSeparator() + type.getOtherSeparators());
  }

  private static final Predicate<Object> NOT_EMPTY = new Predicate<Object>() {
    @Override
    public boolean apply(Object input) {
      return !input.toString().isEmpty();
    }
  };

  /**
   * Equivalents to {@link CaseSensitivity} values that implement the creation of name objects for
   * the case sensitivity setting.
   */
  private enum NameFactory implements Function<String, Name> {
    CASE_SENSITIVE {
      @Override
      public Name name(String string) {
        return Name.simple(string);
      }
    },
    CASE_INSENSITIVE_ASCII {
      @Override
      public Name name(String string) {
        return Name.caseInsensitiveAscii(string);
      }
    },
    CASE_INSENSITIVE_UNICODE {
      @Override
      public Name name(String string) {
        return Name.normalizing(string, Normalizer2.getNFKCCasefoldInstance());
      }
    };

    public abstract Name name(String string);

    @Override
    public final Name apply(String string) {
      return name(string);
    }

    public final Iterable<Name> names(Iterable<String> strings) {
      return Iterables.transform(strings, this);
    }
  }
}
