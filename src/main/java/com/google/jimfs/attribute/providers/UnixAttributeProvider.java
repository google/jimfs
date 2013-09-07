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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AbstractAttributeProvider;
import com.google.jimfs.attribute.AttributeSpec;
import com.google.jimfs.attribute.AttributeStore;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Attribute provider that provides the "unix" attribute view.
 *
 * @author Colin Decker
 */
public class UnixAttributeProvider extends AbstractAttributeProvider {

  public static final String UID = "uid";
  public static final String INO = "ino";
  public static final String DEV = "dev";
  public static final String NLINK = "nlink";
  public static final String RDEV = "rdev";
  public static final String CTIME = "ctime";
  public static final String MODE = "mode";
  public static final String GID = "gid";

  private static final ImmutableSet<AttributeSpec> ATTRIBUTES = ImmutableSet.of(
      AttributeSpec.unsettable(UID, Integer.class),
      AttributeSpec.unsettable(INO, Long.class),
      AttributeSpec.unsettable(DEV, Long.class),
      AttributeSpec.unsettable(NLINK, Integer.class),
      AttributeSpec.unsettable(RDEV, Long.class),
      AttributeSpec.unsettable(CTIME, FileTime.class),
      AttributeSpec.unsettable(MODE, Integer.class),
      AttributeSpec.unsettable(GID, Integer.class));

  private final LoadingCache<Object, Integer> idCache = CacheBuilder.newBuilder()
      .build(new CacheLoader<Object, Integer>() {
        AtomicInteger uidGenerator = new AtomicInteger();

        @Override
        public Integer load(Object key) throws Exception {
          return uidGenerator.incrementAndGet();
        }
      });

  private final OwnerAttributeProvider owner;
  private final PosixAttributeProvider posix;

  public UnixAttributeProvider(PosixAttributeProvider posix) {
    super(ATTRIBUTES);
    this.owner = checkNotNull(posix.owner);
    this.posix = checkNotNull(posix);
  }

  @Override
  public String name() {
    return "unix";
  }

  @Override
  public ImmutableSet<String> inherits() {
    return ImmutableSet.of("basic", "owner", "posix");
  }

  @Override
  public void setInitial(AttributeStore store) {
    // doesn't actually set anything in the attribute map
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object get(AttributeStore store, String attribute) {
    switch (attribute) {
      case UID:
        UserPrincipal user = (UserPrincipal) owner.get(store, OwnerAttributeProvider.OWNER);
        return idCache.getUnchecked(user);
      case GID:
        GroupPrincipal group = (GroupPrincipal) posix.get(store, PosixAttributeProvider.GROUP);
        return idCache.getUnchecked(group);
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
        return idCache.getUnchecked(store);
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
