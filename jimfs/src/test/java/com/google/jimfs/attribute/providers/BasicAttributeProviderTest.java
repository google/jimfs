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

import static org.truth0.Truth.ASSERT;

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
public class BasicAttributeProviderTest extends AttributeProviderTest<BasicAttributeProvider> {

  @Override
  protected BasicAttributeProvider createProvider() {
    return BasicAttributeProvider.INSTANCE;
  }

  @Test
  public void testInitialAttributes() {
    long time = metadata.getCreationTime();
    ASSERT.that(time).isNotEqualTo(0L);
    ASSERT.that(time).isEqualTo(metadata.getLastAccessTime());
    ASSERT.that(time).isEqualTo(metadata.getLastModifiedTime());

    assertContainsAll(metadata,
        ImmutableMap.<String, Object>builder()
            .put("fileKey", 0L)
            .put("size", 0L)
            .put("isDirectory", true)
            .put("isRegularFile", false)
            .put("isSymbolicLink", false)
            .put("isOther", false)
            .build());
  }

  @Test
  public void testSet() {
    FileTime time = FileTime.fromMillis(0L);
    assertSetAndGetSucceeds("creationTime", time);
    assertSetAndGetSucceeds("lastModifiedTime", time);
    assertSetAndGetSucceeds("lastAccessTime", time);
    assertCannotSet("fileKey");
    assertCannotSet("size");
    assertCannotSet("isRegularFile");
    assertCannotSet("isDirectory");
    assertCannotSet("isSymbolicLink");
    assertCannotSet("isOther");
    assertSetFails("creationTime", "foo");
  }

  @Test
  public void testView() throws IOException {
    BasicFileAttributeView view = provider.getView(metadataSupplier());
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
    BasicFileAttributes attrs = provider.read(metadata);
    ASSERT.that(attrs.fileKey()).is(0L);
    ASSERT.that(attrs.isDirectory()).isTrue();
    ASSERT.that(attrs.isRegularFile()).isFalse();
    ASSERT.that(attrs.creationTime()).isNotNull();
  }
}
