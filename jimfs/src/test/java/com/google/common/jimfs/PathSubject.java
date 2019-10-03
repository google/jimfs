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

package com.google.common.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Subject for doing assertions on file system paths.
 *
 * @author Colin Decker
 */
public final class PathSubject extends Subject {

  /** Returns the subject factory for doing assertions on paths. */
  public static Subject.Factory<PathSubject, Path> paths() {
    return new PathSubjectFactory();
  }

  private static final LinkOption[] FOLLOW_LINKS = new LinkOption[0];
  private static final LinkOption[] NOFOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

  private final Path actual;
  protected LinkOption[] linkOptions = FOLLOW_LINKS;
  private Charset charset = UTF_8;

  private PathSubject(FailureMetadata failureMetadata, Path subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  private Path toPath(String path) {
    return actual.getFileSystem().getPath(path);
  }

  /** Returns this, for readability of chained assertions. */
  public PathSubject and() {
    return this;
  }

  /** Do not follow links when looking up the path. */
  public PathSubject noFollowLinks() {
    this.linkOptions = NOFOLLOW_LINKS;
    return this;
  }

  /**
   * Set the given charset to be used when reading the file at this path as text. Default charset if
   * not set is UTF-8.
   */
  public PathSubject withCharset(Charset charset) {
    this.charset = checkNotNull(charset);
    return this;
  }

  /** Asserts that the path is absolute (it has a root component). */
  public PathSubject isAbsolute() {
    if (!actual.isAbsolute()) {
      failWithActual(simpleFact("expected to be absolute"));
    }
    return this;
  }

  /** Asserts that the path is relative (it has no root component). */
  public PathSubject isRelative() {
    if (actual.isAbsolute()) {
      failWithActual(simpleFact("expected to be relative"));
    }
    return this;
  }

  /** Asserts that the path has the given root component. */
  public PathSubject hasRootComponent(@NullableDecl String root) {
    Path rootComponent = actual.getRoot();
    if (root == null && rootComponent != null) {
      failWithActual("expected to have root component", root);
    } else if (root != null && !root.equals(rootComponent.toString())) {
      failWithActual("expected to have root component", root);
    }
    return this;
  }

  /** Asserts that the path has no name components. */
  public PathSubject hasNoNameComponents() {
    check("getNameCount()").that(actual.getNameCount()).isEqualTo(0);
    return this;
  }

  /** Asserts that the path has the given name components. */
  public PathSubject hasNameComponents(String... names) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (Path name : actual) {
      builder.add(name.toString());
    }

    if (!builder.build().equals(ImmutableList.copyOf(names))) {
      failWithActual("expected components", asList(names));
    }
    return this;
  }

  /** Asserts that the path matches the given syntax and pattern. */
  public PathSubject matches(String syntaxAndPattern) {
    PathMatcher matcher = actual.getFileSystem().getPathMatcher(syntaxAndPattern);
    if (!matcher.matches(actual)) {
      failWithActual("expected to match ", syntaxAndPattern);
    }
    return this;
  }

  /** Asserts that the path does not match the given syntax and pattern. */
  public PathSubject doesNotMatch(String syntaxAndPattern) {
    PathMatcher matcher = actual.getFileSystem().getPathMatcher(syntaxAndPattern);
    if (matcher.matches(actual)) {
      failWithActual("expected not to match", syntaxAndPattern);
    }
    return this;
  }

  /** Asserts that the path exists. */
  public PathSubject exists() {
    if (!Files.exists(actual, linkOptions)) {
      failWithActual(simpleFact("expected to exist"));
    }
    if (Files.notExists(actual, linkOptions)) {
      failWithActual(simpleFact("expected to exist"));
    }
    return this;
  }

  /** Asserts that the path does not exist. */
  public PathSubject doesNotExist() {
    if (!Files.notExists(actual, linkOptions)) {
      failWithActual(simpleFact("expected not to exist"));
    }
    if (Files.exists(actual, linkOptions)) {
      failWithActual(simpleFact("expected not to exist"));
    }
    return this;
  }

  /** Asserts that the path is a directory. */
  public PathSubject isDirectory() {
    exists(); // check for directoryness should imply check for existence

    if (!Files.isDirectory(actual, linkOptions)) {
      failWithActual(simpleFact("expected to be directory"));
    }
    return this;
  }

  /** Asserts that the path is a regular file. */
  public PathSubject isRegularFile() {
    exists(); // check for regular fileness should imply check for existence

    if (!Files.isRegularFile(actual, linkOptions)) {
      failWithActual(simpleFact("expected to be regular file"));
    }
    return this;
  }

  /** Asserts that the path is a symbolic link. */
  public PathSubject isSymbolicLink() {
    exists(); // check for symbolic linkness should imply check for existence

    if (!Files.isSymbolicLink(actual)) {
      failWithActual(simpleFact("expected to be symbolic link"));
    }
    return this;
  }

  /** Asserts that the path, which is a symbolic link, has the given path as a target. */
  public PathSubject withTarget(String targetPath) throws IOException {
    Path actualTarget = Files.readSymbolicLink(actual);
    if (!actualTarget.equals(toPath(targetPath))) {
      failWithoutActual(
          fact("expected link target", targetPath),
          fact("but target was", actualTarget),
          fact("for path", actual));
    }
    return this;
  }

  /**
   * Asserts that the file the path points to exists and has the given number of links to it. Fails
   * on a file system that does not support the "unix" view.
   */
  public PathSubject hasLinkCount(int count) throws IOException {
    exists();

    int linkCount = (int) Files.getAttribute(actual, "unix:nlink", linkOptions);
    if (linkCount != count) {
      failWithActual("expected to have link count", count);
    }
    return this;
  }

  /** Asserts that the path resolves to the same file as the given path. */
  public PathSubject isSameFileAs(String path) throws IOException {
    return isSameFileAs(toPath(path));
  }

  /** Asserts that the path resolves to the same file as the given path. */
  public PathSubject isSameFileAs(Path path) throws IOException {
    if (!Files.isSameFile(actual, path)) {
      failWithActual("expected to be same file as", path);
    }
    return this;
  }

  /** Asserts that the path does not resolve to the same file as the given path. */
  public PathSubject isNotSameFileAs(String path) throws IOException {
    if (Files.isSameFile(actual, toPath(path))) {
      failWithActual("expected not to be same file as", path);
    }
    return this;
  }

  /** Asserts that the directory has no children. */
  public PathSubject hasNoChildren() throws IOException {
    isDirectory();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(actual)) {
      if (stream.iterator().hasNext()) {
        failWithActual(simpleFact("expected to have no children"));
      }
    }
    return this;
  }

  /** Asserts that the directory has children with the given names, in the given order. */
  public PathSubject hasChildren(String... children) throws IOException {
    isDirectory();

    List<Path> expectedNames = new ArrayList<>();
    for (String child : children) {
      expectedNames.add(actual.getFileSystem().getPath(child));
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(actual)) {
      List<Path> actualNames = new ArrayList<>();
      for (Path path : stream) {
        actualNames.add(path.getFileName());
      }

      if (!actualNames.equals(expectedNames)) {
        failWithoutActual(
            fact("expected to have children", expectedNames),
            fact("but had children", actualNames),
            fact("for path", actual));
      }
    }
    return this;
  }

  /** Asserts that the file has the given size. */
  public PathSubject hasSize(long size) throws IOException {
    if (Files.size(actual) != size) {
      failWithActual("expected to have size", size);
    }
    return this;
  }

  /** Asserts that the file is a regular file containing no bytes. */
  public PathSubject containsNoBytes() throws IOException {
    return containsBytes(new byte[0]);
  }

  /**
   * Asserts that the file is a regular file containing exactly the byte values of the given ints.
   */
  public PathSubject containsBytes(int... bytes) throws IOException {
    byte[] realBytes = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      realBytes[i] = (byte) bytes[i];
    }
    return containsBytes(realBytes);
  }

  /** Asserts that the file is a regular file containing exactly the given bytes. */
  public PathSubject containsBytes(byte[] bytes) throws IOException {
    isRegularFile();
    hasSize(bytes.length);

    byte[] actual = Files.readAllBytes(this.actual);
    if (!Arrays.equals(bytes, actual)) {
      System.out.println(BaseEncoding.base16().encode(actual));
      System.out.println(BaseEncoding.base16().encode(bytes));
      failWithActual("expected to contain bytes", BaseEncoding.base16().encode(bytes));
    }
    return this;
  }

  /**
   * Asserts that the file is a regular file containing the same bytes as the regular file at the
   * given path.
   */
  public PathSubject containsSameBytesAs(String path) throws IOException {
    isRegularFile();

    byte[] expectedBytes = Files.readAllBytes(toPath(path));
    if (!Arrays.equals(expectedBytes, Files.readAllBytes(actual))) {
      failWithActual("expected to contain same bytes as", path);
    }
    return this;
  }

  /**
   * Asserts that the file is a regular file containing the given lines of text. By default, the
   * bytes are decoded as UTF-8; for a different charset, use {@link #withCharset(Charset)}.
   */
  public PathSubject containsLines(String... lines) throws IOException {
    return containsLines(Arrays.asList(lines));
  }

  /**
   * Asserts that the file is a regular file containing the given lines of text. By default, the
   * bytes are decoded as UTF-8; for a different charset, use {@link #withCharset(Charset)}.
   */
  public PathSubject containsLines(Iterable<String> lines) throws IOException {
    isRegularFile();

    List<String> expected = ImmutableList.copyOf(lines);
    List<String> actual = Files.readAllLines(this.actual, charset);
    check("lines()").that(actual).isEqualTo(expected);
    return this;
  }

  /** Returns an object for making assertions about the given attribute. */
  public Attribute attribute(final String attribute) {
    return new Attribute() {
      @Override
      public Attribute is(Object value) throws IOException {
        Object actualValue = Files.getAttribute(actual, attribute, linkOptions);
        check("attribute(%s)", attribute).that(actualValue).isEqualTo(value);
        return this;
      }

      @Override
      public Attribute isNot(Object value) throws IOException {
        Object actualValue = Files.getAttribute(actual, attribute, linkOptions);
        check("attribute(%s)", attribute).that(actualValue).isNotEqualTo(value);
        return this;
      }

      @Override
      public PathSubject and() {
        return PathSubject.this;
      }
    };
  }

  private static class PathSubjectFactory implements Subject.Factory<PathSubject, Path> {

    @Override
    public PathSubject createSubject(FailureMetadata failureMetadata, Path that) {
      return new PathSubject(failureMetadata, that);
    }
  }

  /** Interface for assertions about a file attribute. */
  public interface Attribute {

    /** Asserts that the value of this attribute is equal to the given value. */
    Attribute is(Object value) throws IOException;

    /** Asserts that the value of this attribute is not equal to the given value. */
    Attribute isNot(Object value) throws IOException;

    /** Returns the path subject for further chaining. */
    PathSubject and();
  }
}
