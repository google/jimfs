package com.google.jimfs.internal;

import static org.truth0.Truth.ASSERT;

import com.google.jimfs.path.PathType;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link FileFactory}.
 *
 * @author Colin Decker
 */
public class FileFactoryTest {

  private FileFactory factory;

  @Before
  public void setUp() {
    factory = new FileFactory(new RegularFileStorage() {
      @Override
      public ByteStore createByteStore() {
        return new StubByteStore(0);
      }

      @Override
      public long getTotalSpace() {
        return 0;
      }

      @Override
      public long getUnallocatedSpace() {
        return 0;
      }
    });
  }

  @Test
  public void testCreateFiles_basic() {
    File file = factory.createDirectory();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();

    file = factory.createRegularFile();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();

    file = factory.createSymbolicLink(fakePath());
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
  }

  @Test
  public void testCreateFiles_withSupplier() {
    File file = factory.directorySupplier().get();
    ASSERT.that(file.id()).is(0L);
    ASSERT.that(file.isDirectory()).isTrue();

    file = factory.regularFileSupplier().get();
    ASSERT.that(file.id()).is(1L);
    ASSERT.that(file.isRegularFile()).isTrue();

    file = factory.symbolicLinkSupplier(fakePath()).get();
    ASSERT.that(file.id()).is(2L);
    ASSERT.that(file.isSymbolicLink()).isTrue();
  }

  private static JimfsPath fakePath() {
    return new TestPathService(PathType.unix()).emptyPath();
  }
}
