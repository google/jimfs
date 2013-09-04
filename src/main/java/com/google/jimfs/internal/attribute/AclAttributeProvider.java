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

package com.google.jimfs.internal.attribute;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.internal.FileProvider;
import com.google.jimfs.internal.file.File;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;

/**
 * Attribute provider that provides the {@link AclFileAttributeView} ("acl").
 *
 * @author Colin Decker
 */
public class AclAttributeProvider extends AbstractAttributeProvider
    implements AttributeViewProvider<AclFileAttributeView> {

  public static final String ACL = "acl";

  private static final ImmutableSet<AttributeSpec> ATTRIBUTES = ImmutableSet.of(
      AttributeSpec.settable(ACL, List.class));

  private final OwnerAttributeProvider owner;

  private final ImmutableList<AclEntry> defaultAcl;

  public AclAttributeProvider(OwnerAttributeProvider owner, List<AclEntry> defaultAcl) {
    super(ATTRIBUTES);
    this.owner = checkNotNull(owner);
    this.defaultAcl = ImmutableList.copyOf(defaultAcl);
  }

  @Override
  public String name() {
    return "acl";
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of("owner");
  }

  @Override
  public void setInitial(File file) {
    set(file, ACL, defaultAcl);
  }

  @Override
  public void set(File file, String attribute, Object value) {
    switch (attribute) {
      case ACL:
        List<?> list = (List<?>) value;
        for (Object obj : list) {
          checkNotNull(obj);
          if (!(obj instanceof AclEntry)) {
            throw new IllegalArgumentException("invalid element for attribute '" + name() + ":"
                + attribute + "': should be List<AclEntry>, found element of type "
                + obj.getClass());
          }
        }
        super.set(file, attribute, ImmutableList.copyOf(list));
        break;
      default:
        super.set(file, attribute, value);
    }
  }

  @Override
  public Class<AclFileAttributeView> viewType() {
    return AclFileAttributeView.class;
  }

  @Override
  public AclFileAttributeView getView(FileProvider fileProvider) {
    return new View(this, fileProvider);
  }

  private static final class View extends AbstractAttributeView implements AclFileAttributeView {

    private final FileOwnerAttributeView ownerView;

    public View(AclAttributeProvider attributeProvider, FileProvider fileProvider) {
      super(attributeProvider, fileProvider);
      this.ownerView = attributeProvider.owner.getView(fileProvider);
    }

    @Override
    public List<AclEntry> getAcl() throws IOException {
      return get(ACL);
    }

    @Override
    public void setAcl(List<AclEntry> acl) throws IOException {
      set(ACL, acl);
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
