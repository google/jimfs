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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Attribute provider that provides the {@link AclFileAttributeView} ("acl").
 *
 * @author Colin Decker
 */
final class AclAttributeProvider extends AttributeProvider {

  private static final ImmutableSet<String> ATTRIBUTES = ImmutableSet.of("acl");

  private static final ImmutableSet<String> INHERITED_VIEWS = ImmutableSet.of("owner");

  private static final ImmutableList<AclEntry> DEFAULT_ACL = ImmutableList.of();

  @Override
  public String name() {
    return "acl";
  }

  @Override
  public ImmutableSet<String> inherits() {
    return INHERITED_VIEWS;
  }

  @Override
  public ImmutableSet<String> fixedAttributes() {
    return ATTRIBUTES;
  }

  @Override
  public ImmutableMap<String, ?> defaultValues(Map<String, ?> userProvidedDefaults) {
    Object userProvidedAcl = userProvidedDefaults.get("acl:acl");

    ImmutableList<AclEntry> acl = DEFAULT_ACL;
    if (userProvidedAcl != null) {
      acl = toAcl(checkType("acl", "acl", userProvidedAcl, List.class));
    }

    return ImmutableMap.of("acl:acl", acl);
  }

  @Nullable
  @Override
  public Object get(File file, String attribute) {
    if (attribute.equals("acl")) {
      return file.getAttribute("acl", "acl");
    }

    return null;
  }

  @Override
  public void set(File file, String view, String attribute, Object value, boolean create) {
    if (attribute.equals("acl")) {
      checkNotCreate(view, attribute, create);
      file.setAttribute("acl", "acl", toAcl(checkType(view, attribute, value, List.class)));
    }
  }

  @SuppressWarnings("unchecked") // only cast after checking each element's type
  private static ImmutableList<AclEntry> toAcl(List<?> list) {
    ImmutableList<?> copy = ImmutableList.copyOf(list);
    for (Object obj : copy) {
      if (!(obj instanceof AclEntry)) {
        throw new IllegalArgumentException(
            "invalid element for attribute 'acl:acl': should be List<AclEntry>, "
                + "found element of type " + obj.getClass());
      }
    }

    return (ImmutableList<AclEntry>) copy;
  }

  @Override
  public Class<AclFileAttributeView> viewType() {
    return AclFileAttributeView.class;
  }

  @Override
  public AclFileAttributeView view(
      FileLookup lookup, ImmutableMap<String, FileAttributeView> inheritedViews) {
    return new View(lookup, (FileOwnerAttributeView) inheritedViews.get("owner"));
  }

  /**
   * Implementation of {@link AclFileAttributeView}.
   */
  private static final class View extends AbstractAttributeView implements AclFileAttributeView {

    private final FileOwnerAttributeView ownerView;

    public View(FileLookup lookup, FileOwnerAttributeView ownerView) {
      super(lookup);
      this.ownerView = checkNotNull(ownerView);
    }

    @Override
    public String name() {
      return "acl";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AclEntry> getAcl() throws IOException {
      return (List<AclEntry>) lookupFile().getAttribute("acl", "acl");
    }

    @Override
    public void setAcl(List<AclEntry> acl) throws IOException {
      checkNotNull(acl);
      lookupFile().setAttribute("acl", "acl", ImmutableList.copyOf(acl));
    }

    @Override
    public UserPrincipal getOwner() throws IOException {
      return ownerView.getOwner();
    }

    @Override
    public void setOwner(UserPrincipal owner) throws IOException {
      ownerView.setOwner(owner);
    }
  }
}
