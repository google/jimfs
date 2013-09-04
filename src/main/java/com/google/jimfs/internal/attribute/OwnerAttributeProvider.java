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

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.internal.FileProvider;
import com.google.jimfs.internal.file.File;

import java.io.IOException;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;

/**
 * Attribute provider that provides the {@link FileOwnerAttributeView} ("owner").
 *
 * @author Colin Decker
 */
public class OwnerAttributeProvider extends AbstractAttributeProvider
    implements AttributeViewProvider<FileOwnerAttributeView> {

  public static final String VIEW = "owner";

  public static final String OWNER = "owner";

  private static final ImmutableSet<AttributeSpec> ATTRIBUTES = ImmutableSet.of(
      AttributeSpec.settableOnCreate(OWNER, UserPrincipal.class)
  );

  private final UserPrincipal defaultOwner;

  public OwnerAttributeProvider(UserPrincipal defaultOwner) {
    super(ATTRIBUTES);
    this.defaultOwner = checkNotNull(defaultOwner);
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
  public void setInitial(File file) {
    set(file, OWNER, defaultOwner);
  }

  @Override
  public Class<FileOwnerAttributeView> viewType() {
    return FileOwnerAttributeView.class;
  }

  @Override
  public FileOwnerAttributeView getView(FileProvider fileProvider) {
    return new View(fileProvider);
  }

  private class View extends AbstractAttributeView implements FileOwnerAttributeView {

    public View(FileProvider fileProvider) {
      super(OwnerAttributeProvider.this, fileProvider);
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
