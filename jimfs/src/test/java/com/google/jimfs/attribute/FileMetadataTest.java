package com.google.jimfs.attribute;

import static org.truth0.Truth.ASSERT;

import org.junit.Test;

/**
 * Tests for {@link FileMetadata}.
 *
 * @author Colin Decker
 */
public class FileMetadataTest {

  @Test
  public void testAttributes() {
    // these methods are basically just thin wrappers around a map, so no need to test too
    // thoroughly

    FileMetadata metadata = new FakeFileMetadata(0L, false, true, false, 0);

    ASSERT.that(metadata.getAttributeKeys()).isEmpty();
    ASSERT.that(metadata.getAttribute("foo:foo")).isNull();

    metadata.deleteAttribute("foo:foo"); // doesn't throw

    metadata.setAttribute("foo:foo", "foo");

    ASSERT.that(metadata.getAttributeKeys()).iteratesAs("foo:foo");
    ASSERT.that(metadata.getAttribute("foo:foo")).is("foo");

    metadata.deleteAttribute("foo:foo");

    ASSERT.that(metadata.getAttributeKeys()).isEmpty();
    ASSERT.that(metadata.getAttribute("foo:foo")).isNull();
  }
}
