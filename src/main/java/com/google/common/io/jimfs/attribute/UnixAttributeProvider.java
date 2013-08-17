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

package com.google.common.io.jimfs.attribute;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.jimfs.file.File;

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

  private final BasicAttributeProvider basic;
  private final OwnerAttributeProvider owner;
  private final PosixAttributeProvider posix;

  public UnixAttributeProvider(PosixAttributeProvider posix) {
    super(ATTRIBUTES);
    this.basic = checkNotNull(posix.basic);
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
  public void setInitial(File file) {
    // doesn't actually set anything in the attribute map
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object get(File file, String attribute) {
    switch (attribute) {
      case UID:
        UserPrincipal user = (UserPrincipal) owner.get(file, OwnerAttributeProvider.OWNER);
        return idCache.getUnchecked(user);
      case GID:
        GroupPrincipal group = (GroupPrincipal) posix.get(file, PosixAttributeProvider.GROUP);
        return idCache.getUnchecked(group);
      case MODE:
        Set<PosixFilePermission> permissions
            = (Set<PosixFilePermission>) posix.get(file, PosixAttributeProvider.PERMISSIONS);
        return toMode(permissions);
      case CTIME:
        return basic.get(file, BasicAttributeProvider.CREATION_TIME);
      case RDEV:
        return 0L;
      case DEV:
        return 1L;
      case INO:
        return idCache.getUnchecked(file);
      case NLINK:
        return file.links();
    }
    return super.get(file, attribute);
  }

  private static int toMode(Set<PosixFilePermission> permissions) {
    int result = 0;
    for (PosixFilePermission permission : permissions) {
      switch (permission) {
        case OWNER_READ:
          result |= 0x400;
          break;
        case OWNER_WRITE:
          result |= 0x200;
          break;
        case OWNER_EXECUTE:
          result |= 0x100;
          break;
        case GROUP_READ:
          result |= 0x040;
          break;
        case GROUP_WRITE:
          result |= 0x020;
          break;
        case GROUP_EXECUTE:
          result |= 0x010;
          break;
        case OTHERS_READ:
          result |= 0x004;
          break;
        case OTHERS_WRITE:
          result |= 0x002;
          break;
        case OTHERS_EXECUTE:
          result |= 0x001;
          break;
      }
    }
    return result;
  }
}
