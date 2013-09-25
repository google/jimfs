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
import static com.google.jimfs.attribute.UserLookupService.createGroupPrincipal;

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AbstractAttributeProvider;
import com.google.jimfs.attribute.AbstractAttributeView;
import com.google.jimfs.attribute.Attribute;
import com.google.jimfs.attribute.AttributeReader;
import com.google.jimfs.attribute.AttributeStore;
import com.google.jimfs.attribute.AttributeViewProvider;
import com.google.jimfs.common.IoSupplier;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

/**
 * Attribute provider that provides the {@link PosixFileAttributeView} ("posix") and allows reading
 * of {@link PosixFileAttributes}.
 *
 * @author Colin Decker
 */
public final class PosixAttributeProvider extends AbstractAttributeProvider implements
    AttributeViewProvider<PosixFileAttributeView>, AttributeReader<PosixFileAttributes> {

  public static final String VIEW = "posix";

  public static final String GROUP = "group";
  public static final String PERMISSIONS = "permissions";

  private static final ImmutableSet<Attribute> ATTRIBUTES = ImmutableSet.of(
      Attribute.settable(VIEW, GROUP, GroupPrincipal.class),
      Attribute.settableOnCreate(VIEW, PERMISSIONS, Set.class));

  private final GroupPrincipal defaultGroup;
  private final ImmutableSet<PosixFilePermission> defaultPermissions;

  final OwnerAttributeProvider owner;

  public PosixAttributeProvider(OwnerAttributeProvider owner) {
    this("group", "rw-r--r--", owner);
  }

  public PosixAttributeProvider(
      String defaultGroup, String defaultPermissions, OwnerAttributeProvider owner) {
    super(ATTRIBUTES);
    this.defaultGroup = createGroupPrincipal(defaultGroup);
    this.defaultPermissions = ImmutableSet.copyOf(
        PosixFilePermissions.fromString(defaultPermissions));
    this.owner = checkNotNull(owner);
  }

  @Override
  public String name() {
    return VIEW;
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of("basic", "owner");
  }

  @Override
  public void setInitial(AttributeStore store) {
    set(store, GROUP, defaultGroup);
    set(store, PERMISSIONS, defaultPermissions);
  }

  @Override
  public void set(AttributeStore store, String attribute, Object value) {
    switch (attribute) {
      case PERMISSIONS:
        Set<?> set = (Set<?>) value;
        for (Object obj : set) {
          checkNotNull(obj);
          if (!(obj instanceof PosixFilePermission)) {
            throw new IllegalArgumentException("invalid element for attribute '" + name() + ":"
                + attribute + "': should be Set<PosixFilePermission>, found element of type "
                + obj.getClass());
          }
        }

        super.set(store, attribute, ImmutableSet.copyOf(set));
        break;
      default:
        super.set(store, attribute, value);
    }
  }

  @Override
  public Class<PosixFileAttributeView> viewType() {
    return PosixFileAttributeView.class;
  }

  @Override
  public View getView(IoSupplier<? extends AttributeStore> supplier) {
    return new View(this, supplier);
  }

  @Override
  public Class<PosixFileAttributes> attributesType() {
    return PosixFileAttributes.class;
  }

  @Override
  public PosixFileAttributes read(AttributeStore store) {
    try {
      return new Attributes(getView(IoSupplier.of(store)));
    } catch (IOException e) {
      throw new AssertionError(e); // IoSupplier<AttributeStore>.ofFile doesn't throw IOException
    }
  }

  /**
   * Implementation of {@link PosixFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements PosixFileAttributeView {

    private final BasicFileAttributeView basicView;
    private final FileOwnerAttributeView ownerView;

    protected View(PosixAttributeProvider provider, IoSupplier<? extends AttributeStore> supplier) {
      super(provider, supplier);
      this.basicView = BasicAttributeProvider.INSTANCE.getView(supplier);
      this.ownerView = provider.owner.getView(supplier);
    }

    @Override
    public PosixFileAttributes readAttributes() throws IOException {
      View view = new View((PosixAttributeProvider) provider(), IoSupplier.of(store()));
      return new Attributes(view);
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
        throws IOException {
      basicView.setTimes(lastModifiedTime, lastAccessTime, createTime);
    }

    @Override
    public void setPermissions(Set<PosixFilePermission> perms) throws IOException {
      set(PERMISSIONS, perms);
    }

    @Override
    public void setGroup(GroupPrincipal group) throws IOException {
      set(GROUP, group);
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

  /**
   * Implementation of {@link PosixFileAttributes}.
   */
  public static class Attributes
      extends BasicAttributeProvider.Attributes implements PosixFileAttributes {

    private final UserPrincipal owner;
    private final GroupPrincipal group;
    private final ImmutableSet<PosixFilePermission> permissions;

    protected Attributes(View view) throws IOException {
      super(view.basicView.readAttributes());
      this.owner = view.ownerView.getOwner();
      this.group = view.get(GROUP);
      this.permissions = view.get(PERMISSIONS);
    }

    @Override
    public UserPrincipal owner() {
      return owner;
    }

    @Override
    public GroupPrincipal group() {
      return group;
    }

    @Override
    public ImmutableSet<PosixFilePermission> permissions() {
      return permissions;
    }
  }
}
