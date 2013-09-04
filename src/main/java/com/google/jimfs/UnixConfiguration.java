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

import static com.google.jimfs.JimfsConfiguration.Feature.GROUPS;
import static com.google.jimfs.JimfsConfiguration.Feature.LINKS;
import static com.google.jimfs.JimfsConfiguration.Feature.SECURE_DIRECTORY_STREAMS;
import static com.google.jimfs.JimfsConfiguration.Feature.SYMBOLIC_LINKS;
import static com.google.jimfs.internal.attribute.UserLookupService.createGroupPrincipal;
import static com.google.jimfs.internal.attribute.UserLookupService.createUserPrincipal;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.internal.JimfsFileSystem;
import com.google.jimfs.internal.attribute.AttributeProvider;
import com.google.jimfs.internal.attribute.BasicAttributeProvider;
import com.google.jimfs.internal.attribute.OwnerAttributeProvider;
import com.google.jimfs.internal.attribute.PosixAttributeProvider;
import com.google.jimfs.internal.attribute.UnixAttributeProvider;
import com.google.jimfs.internal.path.JimfsPath;
import com.google.jimfs.internal.path.Name;

import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * Configuration for UNIX-like instances of {@link JimfsFileSystem}.
 *
 * @author Colin Decker
 */
public final class UnixConfiguration extends JimfsConfiguration {

  private static final Joiner JOINER = Joiner.on('/');
  private static final Splitter SPLITTER = Splitter.on('/').omitEmptyStrings();

  private final String workingDirectory;
  private final String defaultOwner;
  private final String defaultGroup;
  private final String defaultPermissions;

  public UnixConfiguration() {
    this("/work", "root", "root", "rw-r--r--");
  }

  public UnixConfiguration(String workingDirectory,
      String defaultOwner, String defaultGroup, String defaultPermissions) {
    this.workingDirectory = workingDirectory;
    this.defaultOwner = defaultOwner;
    this.defaultGroup = defaultGroup;
    this.defaultPermissions = defaultPermissions;
  }

  @Override
  public String getSeparator() {
    return "/";
  }

  @Override
  public Iterable<String> getRoots() {
    return ImmutableSet.of("/");
  }

  @Override
  public String getWorkingDirectory() {
    return workingDirectory;
  }

  @Override
  public boolean isHidden(Path path) {
    Path fileName = path.getFileName();
    return fileName != null && fileName.toString().startsWith(".");
  }

  @Override
  protected Iterable<Feature> getSupportedFeatures() {
    return ImmutableSet.of(SYMBOLIC_LINKS, LINKS, GROUPS, SECURE_DIRECTORY_STREAMS);
  }

  @Override
  public JimfsPath parsePath(JimfsFileSystem fileSystem, List<String> parts) {
    if (parts.isEmpty()) {
      return JimfsPath.empty(fileSystem);
    }

    String first = parts.get(0);
    Name root = null;
    if (first.startsWith("/")) {
      root = createName("/", true);
      if (first.length() == 1) {
        parts.remove(0);
      } else {
        parts.set(0, first.substring(1));
      }
    }

    String joined = JOINER.join(parts);
    Iterable<String> split = SPLITTER.split(joined);

    return JimfsPath.create(fileSystem, root, toNames(split));
  }

  @Override
  public Iterable<AttributeProvider> getAttributeProviders() {
    BasicAttributeProvider basic = new BasicAttributeProvider();
    OwnerAttributeProvider owner =
        new OwnerAttributeProvider(createUserPrincipal(defaultOwner));
    PosixAttributeProvider posix =
        new PosixAttributeProvider(createGroupPrincipal(defaultGroup),
            PosixFilePermissions.fromString(defaultPermissions), basic, owner);
    UnixAttributeProvider unix = new UnixAttributeProvider(posix);
    return ImmutableSet.<AttributeProvider>of(basic, owner, posix, unix);
  }
}
