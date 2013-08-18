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

package com.google.common.io.jimfs.attribute;

import static com.google.common.io.jimfs.attribute.AttributeService.SetMode.NORMAL;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/**
 * Tests for {@link BasicAttributeProvider}.
 *
 * @author Colin Decker
 */
public class BasicAttributeProviderTest extends AttributeProviderTest {

  @Override
  protected Iterable<BasicAttributeProvider> createProviders() {
    return ImmutableList.of(new BasicAttributeProvider());
  }

  @Test
  public void testInitialAttributes() {
    FileTime time = (FileTime) file.getAttribute("basic:creationTime");
    ASSERT.that(time).isNotNull();

    assertContainsAll(file,
        ImmutableMap.<String, Object>builder()
            .put("basic:fileKey", 0L)
            .put("basic:size", 0L)
            .put("basic:isDirectory", false)
            .put("basic:isRegularFile", false)
            .put("basic:isSymbolicLink", false)
            .put("basic:isOther", true)
            .put("basic:creationTime", time)
            .put("basic:lastAccessTime", time)
            .put("basic:lastModifiedTime", time)
            .build());

    time = (FileTime) dir.getAttribute("basic:creationTime");

    assertContainsAll(dir,
        ImmutableMap.<String, Object>builder()
            .put("basic:fileKey", 1L)
            .put("basic:size", 0L)
            .put("basic:isDirectory", true)
            .put("basic:isRegularFile", false)
            .put("basic:isSymbolicLink", false)
            .put("basic:isOther", false)
            .put("basic:creationTime", time)
            .put("basic:lastAccessTime", time)
            .put("basic:lastModifiedTime", time)
            .build());
  }

  @Test
  public void testSet() {
    FileTime time = FileTime.fromMillis(0L);
    assertSetAndGetSucceeds("basic:creationTime", time, NORMAL);
    assertSetAndGetSucceeds("basic:lastModifiedTime", time, NORMAL);
    assertSetAndGetSucceeds("basic:lastAccessTime", time, NORMAL);
    assertSetFails("basic:fileKey", 10L, NORMAL);
    assertSetFails("basic:size", 10L, NORMAL);
    assertSetFails("basic:isRegularFile", true, NORMAL);
    assertSetFails("basic:isDirectory", true, NORMAL);
    assertSetFails("basic:isSymbolicLink", true, NORMAL);
    assertSetFails("basic:isOther", true, NORMAL);
    assertSetFails("basic:creationTime", "foo", NORMAL);
  }

  @Test
  public void testView() throws IOException {
    BasicFileAttributeView view = service.getFileAttributeView(
        fileProvider(), BasicFileAttributeView.class);
    assert view != null;
    ASSERT.that(view.name()).is("basic");

    BasicFileAttributes attrs = view.readAttributes();
    ASSERT.that(attrs.fileKey()).is(0L);

    FileTime time = attrs.creationTime();
    ASSERT.that(attrs.lastAccessTime()).is(time);
    ASSERT.that(attrs.lastModifiedTime()).is(time);

    view.setTimes(null, null, null);

    attrs = view.readAttributes();
    ASSERT.that(attrs.creationTime()).is(time);
    ASSERT.that(attrs.lastAccessTime()).is(time);
    ASSERT.that(attrs.lastModifiedTime()).is(time);

    view.setTimes(FileTime.fromMillis(0L), null, null);

    attrs = view.readAttributes();
    ASSERT.that(attrs.creationTime()).is(time);
    ASSERT.that(attrs.lastAccessTime()).is(time);
    ASSERT.that(attrs.lastModifiedTime()).is(FileTime.fromMillis(0L));
  }

  @Test
  public void testAttributes() {
    BasicFileAttributes attrs = service.readAttributes(file, BasicFileAttributes.class);
    ASSERT.that(attrs.fileKey()).is(0L);

    attrs = service.readAttributes(dir, BasicFileAttributes.class);
    ASSERT.that(attrs.fileKey()).is(1L);
  }
}
