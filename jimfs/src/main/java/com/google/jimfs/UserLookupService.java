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
    return UserPrincipals.createUserPrincipal(name);
  }

  @Override
  public GroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
    if (!supportsGroups) {
      throw new UserPrincipalNotFoundException(group); // required by spec
    }
    return UserPrincipals.createGroupPrincipal(group);
  }
}
