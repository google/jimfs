package com.google.jimfs.internal.path;

import static com.google.jimfs.testing.PathSubject.paths;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.jimfs.path.Name;
import com.google.jimfs.path.PathType;
import com.google.jimfs.testing.TestPath;
import com.google.jimfs.testing.TestPathService;

import org.junit.Test;

/**
 * Tests for {@link PathService}.
 *
 * @author Colin Decker
 */
public class PathServiceTest {

  private final PathType type = PathType.unix();
  private final TestPathService service = new TestPathService(type);

  @Test
  public void testBasicProperties() {
    ASSERT.that(service.getSeparator()).is("/");
    ASSERT.that(new TestPathService(PathType.windows()).getSeparator()).is("\\");
  }

  @Test
  public void testPathCreation() {
    ASSERT.about(paths()).that(service.emptyPath())
        .hasRootComponent(null).and()
        .hasNameComponents("");

    ASSERT.about(paths()).that(service.createRoot(type.getRootName("/")))
        .isAbsolute().and()
        .hasRootComponent("/").and()
        .hasNoNameComponents();

    ASSERT.about(paths()).that(service.createFileName(type.getName("foo")))
        .hasRootComponent(null).and()
        .hasNameComponents("foo");

    JimfsPath relative = service.createRelativePath(type.asNames(ImmutableList.of("foo", "bar")));
    ASSERT.about(paths()).that(relative)
        .hasRootComponent(null).and()
        .hasNameComponents("foo", "bar");

    JimfsPath absolute = service.createPath(
        type.getRootName("/"), type.asNames(ImmutableList.of("foo", "bar")));
    ASSERT.about(paths()).that(absolute)
        .isAbsolute().and()
        .hasRootComponent("/").and()
        .hasNameComponents("foo", "bar");
  }

  @Test
  public void testPathCreation_emptyPath() {
    // normalized to empty path with single empty string name
    ASSERT.about(paths()).that(service.createPath(null, ImmutableList.<Name>of()))
        .hasRootComponent(null).and()
        .hasNameComponents("");
  }

  @Test
  public void testToString() {
    // not much to test for this since it just delegates to PathType anyway
    JimfsPath path = new TestPath(
        service, null, ImmutableList.of(Name.simple("foo"), Name.simple("bar")));
    ASSERT.that(service.toString(path)).is("foo/bar");

    path = new TestPath(service, Name.simple("/"), ImmutableList.of(Name.simple("foo")));
    ASSERT.that(service.toString(path)).is("/foo");
  }

  @Test
  public void testPathMatcher() {
    ASSERT.that(service.createPathMatcher("regex:foo")).isA(PathMatchers.RegexPathMatcher.class);
    ASSERT.that(service.createPathMatcher("glob:foo")).isA(PathMatchers.RegexPathMatcher.class);
  }
}
