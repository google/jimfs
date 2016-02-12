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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Attribute provider that provides the "unix" attribute view.
 *
 * @author Colin Decker
 */
final class UnixAttributeProvider extends AttributeProvider {

  private static final ImmutableSet<String> ATTRIBUTES =
      ImmutableSet.of("uid", "ino", "dev", "nlink", "rdev", "ctime", "mode", "gid");

  private static final ImmutableSet<String> INHERITED_VIEWS =
      ImmutableSet.of("basic", "owner", "posix");

  private final AtomicInteger uidGenerator = new AtomicInteger();
  private final ConcurrentMap<Object, Integer> idCache = new ConcurrentHashMap<>();

  @Override
  public String name() {
    return "unix";
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
  public Class<UnixFileAttributeView> viewType() {
    return UnixFileAttributeView.class;
  }

  @Override
  public UnixFileAttributeView view(
      FileLookup lookup, ImmutableMap<String, FileAttributeView> inheritedViews) {
    // This method should not be called... and it cannot be called through the public APIs in
    // java.nio.file since there is no public UnixFileAttributeView type.
    throw new UnsupportedOperationException();
  }

  // TODO(cgdecker): Since we can now guarantee that the owner/group for an file are our own
  // implementation of UserPrincipal/GroupPrincipal, it would be nice to have them store a unique
  // ID themselves and just get that rather than doing caching here. Then this could be a singleton
  // like the rest of the AttributeProviders. However, that would require a way for the owner/posix
  // providers to create their default principals using the lookup service for the specific file
  // system.

  /**
   * Returns an ID that is guaranteed to be the same for any invocation with equal objects.
   */
  private Integer getUniqueId(Object object) {
    Integer id = idCache.get(object);
    if (id == null) {
      id = uidGenerator.incrementAndGet();
      Integer existing = idCache.putIfAbsent(object, id);
      if (existing != null) {
        return existing;
      }
    }
    return id;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object get(File file, String attribute) {
    switch (attribute) {
      case "uid":
        UserPrincipal user = (UserPrincipal) file.getAttribute("owner", "owner");
        return getUniqueId(user);
      case "gid":
        GroupPrincipal group = (GroupPrincipal) file.getAttribute("posix", "group");
        return getUniqueId(group);
      case "mode":
        Set<PosixFilePermission> permissions =
            (Set<PosixFilePermission>) file.getAttribute("posix", "permissions");
        return toMode(permissions);
      case "ctime":
        return FileTime.fromMillis(file.getCreationTime());
      case "rdev":
        return 0L;
      case "dev":
        return 1L;
      case "ino":
        return file.id();
      case "nlink":
        return file.links();
      default:
        return null;
    }
  }

  @Override
  public void set(File file, String view, String attribute, Object value, boolean create) {
    throw unsettable(view, attribute);
  }

  @SuppressWarnings("OctalInteger")
  private static int toMode(Set<PosixFilePermission> permissions) {
    int result = 0;
    for (PosixFilePermission permission : permissions) {
      checkNotNull(permission);
      switch (permission) {
        case OWNER_READ:
          result |= 0400;
          break;
        case OWNER_WRITE:
          result |= 0200;
          break;
        case OWNER_EXECUTE:
          result |= 0100;
          break;
        case GROUP_READ:
          result |= 0040;
          break;
        case GROUP_WRITE:
          result |= 0020;
          break;
        case GROUP_EXECUTE:
          result |= 0010;
          break;
        case OTHERS_READ:
          result |= 0004;
          break;
        case OTHERS_WRITE:
          result |= 0002;
          break;
        case OTHERS_EXECUTE:
          result |= 0001;
          break;
        default:
          throw new AssertionError(); // no other possible values
      }
    }
    return result;
  }
}
