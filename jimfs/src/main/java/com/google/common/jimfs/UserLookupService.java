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

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;

/**
 * {@link UserPrincipalLookupService} implementation.
 *
 * @author Colin Decker
 */
final class UserLookupService extends UserPrincipalLookupService {

  private final boolean supportsGroups;

  public UserLookupService(boolean supportsGroups) {
    this.supportsGroups = supportsGroups;
  }

  @Override
  public UserPrincipal lookupPrincipalByName(String name) {
    return createUserPrincipal(name);
  }

  @Override
  public GroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
    if (!supportsGroups) {
      throw new UserPrincipalNotFoundException(group); // required by spec
    }
    return createGroupPrincipal(group);
  }

  /**
   * Creates a {@link UserPrincipal} for the given user name.
   */
  static UserPrincipal createUserPrincipal(String name) {
    return new JimfsUserPrincipal(name);
  }

  /**
   * Creates a {@link GroupPrincipal} for the given group name.
   */
  static GroupPrincipal createGroupPrincipal(String name) {
    return new JimfsGroupPrincipal(name);
  }

  /**
   * Base class for {@link UserPrincipal} and {@link GroupPrincipal} implementations.
   */
  private abstract static class NamedPrincipal implements UserPrincipal {

    protected final String name;

    private NamedPrincipal(String name) {
      this.name = checkNotNull(name);
    }

    @Override
    public final String getName() {
      return name;
    }

    @Override
    public final int hashCode() {
      return name.hashCode();
    }

    @Override
    public final String toString() {
      return name;
    }
  }

  /**
   * {@link UserPrincipal} implementation.
   */
  static final class JimfsUserPrincipal extends NamedPrincipal {

    private JimfsUserPrincipal(String name) {
      super(name);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof JimfsUserPrincipal
          && getName().equals(((JimfsUserPrincipal) obj).getName());
    }
  }

  /**
   * {@link GroupPrincipal} implementation.
   */
  static final class JimfsGroupPrincipal extends NamedPrincipal implements GroupPrincipal {

    private JimfsGroupPrincipal(String name) {
      super(name);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof JimfsGroupPrincipal && ((JimfsGroupPrincipal) obj).name.equals(name);
    }
  }
}
