/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

/**
 * Attribute provider that provides the {@link PosixFileAttributeView} ("posix") and allows reading
 * of {@link PosixFileAttributes}.
 *
 * @author Colin Decker
 */
class PosixAttributeProvider extends AbstractAttributeProvider implements
    AttributeViewProvider<PosixFileAttributeView>, AttributeReader<PosixFileAttributes> {

  public static final String VIEW = "posix";

  public static final String GROUP = "group";
  public static final String PERMISSIONS = "permissions";

  private static final ImmutableSet<AttributeSpec> ATTRIBUTES = ImmutableSet.of(
      AttributeSpec.settable(GROUP, GroupPrincipal.class),
      AttributeSpec.settable(PERMISSIONS, Set.class));

  private final GroupPrincipal defaultGroup;
  private final ImmutableSet<PosixFilePermission> defaultPermissions;

  final BasicAttributeProvider basic;
  final OwnerAttributeProvider owner;

  public PosixAttributeProvider(
      GroupPrincipal defaultGroup, Iterable<PosixFilePermission> defaultPermissions,
      BasicAttributeProvider basic, OwnerAttributeProvider owner) {
    super(ATTRIBUTES);
    this.defaultGroup = checkNotNull(defaultGroup);
    this.defaultPermissions = ImmutableSet.copyOf(defaultPermissions);
    this.basic = checkNotNull(basic);
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
  public void setInitial(File file) {
    set(file, GROUP, defaultGroup);
    set(file, PERMISSIONS, defaultPermissions);
  }

  @Override
  public void set(File file, String attribute, Object value) {
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

        super.set(file, attribute, ImmutableSet.copyOf(set));
        break;
      default:
        super.set(file, attribute, value);
    }
  }

  @Override
  public Class<PosixFileAttributeView> viewType() {
    return PosixFileAttributeView.class;
  }

  @Override
  public View getView(FileProvider fileProvider) {
    return new View(this, fileProvider);
  }

  @Override
  public Class<PosixFileAttributes> attributesType() {
    return PosixFileAttributes.class;
  }

  @Override
  public PosixFileAttributes read(File file) {
    try {
      return new Attributes(getView(FileProvider.ofFile(file)));
    } catch (IOException e) {
      throw new AssertionError(e); // FileProvider.ofFile doesn't throw IOException
    }
  }

  /**
   * Implementation of {@link PosixFileAttributeView}.
   */
  private static class View extends AbstractAttributeView implements PosixFileAttributeView {

    private final BasicFileAttributeView basicView;
    private final FileOwnerAttributeView ownerView;

    protected View(PosixAttributeProvider provider, FileProvider fileProvider) {
      super(provider, fileProvider);
      this.basicView = provider.basic.getView(fileProvider);
      this.ownerView = provider.owner.getView(fileProvider);
    }

    @Override
    public PosixFileAttributes readAttributes() throws IOException {
      View view = new View((PosixAttributeProvider) provider(), FileProvider.ofFile(file()));
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
  public static class Attributes extends BasicAttributeProvider.Attributes implements PosixFileAttributes {

    private final UserPrincipal owner;
    private final GroupPrincipal group;
    private final ImmutableSet<PosixFilePermission> permissions;

    protected Attributes(View view) throws IOException {
      super(view.basicView.readAttributes());
      this.owner = view.get(OwnerAttributeProvider.OWNER);
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
