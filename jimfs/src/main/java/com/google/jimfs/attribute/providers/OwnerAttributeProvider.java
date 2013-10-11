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

import static com.google.jimfs.attribute.UserLookupService.createUserPrincipal;

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AbstractAttributeProvider;
import com.google.jimfs.attribute.AbstractAttributeView;
import com.google.jimfs.attribute.Attribute;
import com.google.jimfs.attribute.AttributeViewProvider;
import com.google.jimfs.attribute.FileMetadata;
import com.google.jimfs.attribute.FileMetadataSupplier;

import java.io.IOException;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;

/**
 * Attribute provider that provides the {@link FileOwnerAttributeView} ("owner").
 *
 * @author Colin Decker
 */
public final class OwnerAttributeProvider extends AbstractAttributeProvider
    implements AttributeViewProvider<FileOwnerAttributeView> {

  public static final String VIEW = "owner";

  public static final String OWNER = "owner";

  private static final ImmutableSet<Attribute> ATTRIBUTES = ImmutableSet.of(
      Attribute.settableOnCreate(VIEW, OWNER, UserPrincipal.class)
  );

  private final UserPrincipal defaultOwner;

  public OwnerAttributeProvider() {
    this("user");
  }

  public OwnerAttributeProvider(String defaultOwner) {
    super(ATTRIBUTES);
    this.defaultOwner = createUserPrincipal(defaultOwner);
  }

  @Override
  public String name() {
    return VIEW;
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of();
  }

  @Override
  public void setInitial(FileMetadata metadata) {
    set(metadata, OWNER, defaultOwner);
  }

  @Override
  public Class<FileOwnerAttributeView> viewType() {
    return FileOwnerAttributeView.class;
  }

  @Override
  public FileOwnerAttributeView getView(FileMetadataSupplier supplier) {
    return new View(supplier);
  }

  private class View extends AbstractAttributeView implements FileOwnerAttributeView {

    public View(FileMetadataSupplier supplier) {
      super(OwnerAttributeProvider.this, supplier);
    }

    @Override
    public UserPrincipal getOwner() throws IOException {
      return get(OWNER);
    }

    @Override
    public void setOwner(UserPrincipal owner) throws IOException {
      set(OWNER, owner);
    }
  }
}
