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

package com.google.jimfs.attribute.providers;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.jimfs.attribute.UserLookupService.createUserPrincipal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.FileMetadata;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Attribute provider that provides the {@link FileOwnerAttributeView} ("owner").
 *
 * @author Colin Decker
 */
final class OwnerAttributeProvider extends AttributeProvider<FileOwnerAttributeView> {

  private static final ImmutableSet<String> ATTRIBUTES = ImmutableSet.of("owner");

  private static final UserPrincipal DEFAULT_OWNER = createUserPrincipal("user");

  @Override
  public String name() {
    return "owner";
  }

  @Override
  public ImmutableSet<String> fixedAttributes() {
    return ATTRIBUTES;
  }

  @Override
  public Map<String, ?> defaultValues(Map<String, ?> userProvidedDefaults) {
    Object userProvidedOwner = userProvidedDefaults.get("owner:owner");

    UserPrincipal owner = DEFAULT_OWNER;
    if (userProvidedOwner != null) {
      if (userProvidedOwner instanceof String) {
        owner = createUserPrincipal((String) userProvidedOwner);
      } else if (userProvidedOwner instanceof UserPrincipal) {
        owner = createUserPrincipal(userProvidedOwner.toString());
      } else {
        throw invalidType("owner", "owner", userProvidedOwner, String.class, UserPrincipal.class);
      }
    }

    return ImmutableMap.of("owner:owner", owner);
  }

  @Nullable
  @Override
  public Object get(FileMetadata metadata, String attribute) {
    if (attribute.equals("owner")) {
      return metadata.getAttribute("owner:owner");
    }
    return null;
  }

  @Override
  public void set(FileMetadata metadata, String view, String attribute, Object value,
      boolean create) {
    if (attribute.equals("owner")) {
      metadata.setAttribute("owner:owner",
          checkType(view, attribute, value, UserPrincipal.class));
    }
  }

  @Override
  public Class<FileOwnerAttributeView> viewType() {
    return FileOwnerAttributeView.class;
  }

  @Override
  public FileOwnerAttributeView view(FileMetadata.Lookup lookup,
      Map<String, FileAttributeView> inheritedViews) {
    return new View(lookup);
  }

  /**
   * Implementation of {@link FileOwnerAttributeView}.
   */
  private static final class View extends AbstractAttributeView implements FileOwnerAttributeView {

    public View(FileMetadata.Lookup lookup) {
      super(lookup);
    }

    @Override
    public String name() {
      return "owner";
    }

    @Override
    public UserPrincipal getOwner() throws IOException {
      return (UserPrincipal) lookupMetadata().getAttribute("owner:owner");
    }

    @Override
    public void setOwner(UserPrincipal owner) throws IOException {
      lookupMetadata().setAttribute("owner:owner", checkNotNull(owner));
    }
  }
}
