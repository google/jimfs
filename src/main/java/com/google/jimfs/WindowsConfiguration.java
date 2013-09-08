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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jimfs.attribute.UserLookupService.createUserPrincipal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.providers.AclAttributeProvider;
import com.google.jimfs.attribute.providers.DosAttributeProvider;
import com.google.jimfs.attribute.providers.OwnerAttributeProvider;
import com.google.jimfs.attribute.providers.UserDefinedAttributeProvider;
import com.google.jimfs.path.PathType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.util.List;

/**
 * @author Colin Decker
 */
public final class WindowsConfiguration extends JimfsConfiguration {

  private final String workingDirectory;
  private final String defaultUser;
  private final List<AclEntry> defaultAclEntries;
  private final ImmutableSet<String> roots;

  public WindowsConfiguration() {
    this("C:\\");
  }

  public WindowsConfiguration(String... roots) {
    this("C:\\work", "user", ImmutableList.<AclEntry>of(), roots);
  }

  public WindowsConfiguration(String workingDirectory, String defaultUser,
      List<AclEntry> defaultAclEntries, String... roots) {
    super(PathType.windows());
    this.workingDirectory = checkNotNull(workingDirectory);
    this.defaultUser = checkNotNull(defaultUser);
    this.defaultAclEntries = ImmutableList.copyOf(defaultAclEntries);
    this.roots = ImmutableSet.copyOf(roots);
  }

  @Override
  public Iterable<String> getRoots() {
    return roots;
  }

  @Override
  public String getWorkingDirectory() {
    return workingDirectory;
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    return (boolean) Files.getAttribute(path, "dos:hidden");
  }

  @Override
  public Iterable<AttributeProvider> getAttributeProviders() {
    OwnerAttributeProvider owner = new OwnerAttributeProvider(createUserPrincipal(defaultUser));
    AclAttributeProvider acl = new AclAttributeProvider(owner, defaultAclEntries);
    return ImmutableList.<AttributeProvider>of(
        owner,
        DosAttributeProvider.INSTANCE,
        acl,
        UserDefinedAttributeProvider.INSTANCE);
  }
}
