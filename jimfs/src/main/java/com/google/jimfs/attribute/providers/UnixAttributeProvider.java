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

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AbstractAttributeProvider;
import com.google.jimfs.attribute.Attribute;
import com.google.jimfs.attribute.AttributeStore;

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
public class UnixAttributeProvider extends AbstractAttributeProvider {

  public static final String VIEW = "unix";

  public static final String UID = "uid";
  public static final String INO = "ino";
  public static final String DEV = "dev";
  public static final String NLINK = "nlink";
  public static final String RDEV = "rdev";
  public static final String CTIME = "ctime";
  public static final String MODE = "mode";
  public static final String GID = "gid";

  private static final ImmutableSet<Attribute> ATTRIBUTES = ImmutableSet.of(
      Attribute.unsettable(VIEW, UID, Integer.class),
      Attribute.unsettable(VIEW, INO, Long.class),
      Attribute.unsettable(VIEW, DEV, Long.class),
      Attribute.unsettable(VIEW, NLINK, Integer.class),
      Attribute.unsettable(VIEW, RDEV, Long.class),
      Attribute.unsettable(VIEW, CTIME, FileTime.class),
      Attribute.unsettable(VIEW, MODE, Integer.class),
      Attribute.unsettable(VIEW, GID, Integer.class));

  private final AtomicInteger uidGenerator = new AtomicInteger();
  private final ConcurrentMap<Object, Integer> idCache = new ConcurrentHashMap<>();

  private final OwnerAttributeProvider owner;
  private final PosixAttributeProvider posix;

  public UnixAttributeProvider(PosixAttributeProvider posix) {
    super(ATTRIBUTES);
    this.owner = checkNotNull(posix.owner);
    this.posix = checkNotNull(posix);
  }

  @Override
  public String name() {
    return VIEW;
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of("basic", "owner", "posix");
  }

  @Override
  public void setInitial(AttributeStore store) {
    // doesn't actually set anything in the attribute map
  }

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
  public Object get(AttributeStore store, String attribute) {
    switch (attribute) {
      case UID:
        UserPrincipal user = (UserPrincipal) owner.get(store, OwnerAttributeProvider.OWNER);
        return getUniqueId(user);
      case GID:
        GroupPrincipal group = (GroupPrincipal) posix.get(store, PosixAttributeProvider.GROUP);
        return getUniqueId(group);
      case MODE:
        Set<PosixFilePermission> permissions
            = (Set<PosixFilePermission>) posix.get(store, PosixAttributeProvider.PERMISSIONS);
        return toMode(permissions);
      case CTIME:
        return BasicAttributeProvider.INSTANCE.get(store, BasicAttributeProvider.CREATION_TIME);
      case RDEV:
        return 0L;
      case DEV:
        return 1L;
      case INO:
        return getUniqueId(store);
      case NLINK:
        return store.links();
    }
    return super.get(store, attribute);
  }

  @SuppressWarnings("OctalInteger")
  private static int toMode(Set<PosixFilePermission> permissions) {
    int result = 0;
    for (PosixFilePermission permission : permissions) {
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
      }
    }
    return result;
  }
}
