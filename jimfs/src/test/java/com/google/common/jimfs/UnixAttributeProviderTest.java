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

import static com.google.common.jimfs.UserLookupService.createGroupPrincipal;
import static com.google.common.jimfs.UserLookupService.createUserPrincipal;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Tests for {@link UnixAttributeProvider}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
@SuppressWarnings("OctalInteger")
public class UnixAttributeProviderTest
    extends AbstractAttributeProviderTest<UnixAttributeProvider> {

  @Override
  protected UnixAttributeProvider createProvider() {
    return new UnixAttributeProvider();
  }

  @Override
  protected Set<? extends AttributeProvider> createInheritedProviders() {
    return ImmutableSet.of(
        new BasicAttributeProvider(), new OwnerAttributeProvider(), new PosixAttributeProvider());
  }

  @Test
  public void testInitialAttributes() {
    // unix provider relies on other providers to set their initial attributes
    file.setAttribute("owner", "owner", createUserPrincipal("foo"));
    file.setAttribute("posix", "group", createGroupPrincipal("bar"));
    file.setAttribute(
        "posix", "permissions", ImmutableSet.copyOf(PosixFilePermissions.fromString("rw-r--r--")));

    // these are pretty much meaningless here since they aren't properties this
    // file system actually has, so don't really care about the exact value of these
    assertThat(provider.get(file, "uid")).isInstanceOf(Integer.class);
    assertThat(provider.get(file, "gid")).isInstanceOf(Integer.class);
    assertThat(provider.get(file, "rdev")).isEqualTo(0L);
    assertThat(provider.get(file, "dev")).isEqualTo(1L);
    assertThat(provider.get(file, "ino")).isInstanceOf(Integer.class);

    // these have logical origins in attributes from other views
    assertThat(provider.get(file, "mode")).isEqualTo(0644); // rw-r--r--
    assertThat(provider.get(file, "ctime")).isEqualTo(FileTime.fromMillis(file.getCreationTime()));

    // this is based on a property this file system does actually have
    assertThat(provider.get(file, "nlink")).isEqualTo(1);

    file.incrementLinkCount();
    assertThat(provider.get(file, "nlink")).isEqualTo(2);
    file.decrementLinkCount();
    assertThat(provider.get(file, "nlink")).isEqualTo(1);
  }

  @Test
  public void testSet() {
    assertSetFails("unix:uid", 1);
    assertSetFails("unix:gid", 1);
    assertSetFails("unix:rdev", 1L);
    assertSetFails("unix:dev", 1L);
    assertSetFails("unix:ino", 1);
    assertSetFails("unix:mode", 1);
    assertSetFails("unix:ctime", 1L);
    assertSetFails("unix:nlink", 1);
  }
}
