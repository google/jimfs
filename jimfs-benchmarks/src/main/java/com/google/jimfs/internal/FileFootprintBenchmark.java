package com.google.jimfs.internal;

import static com.google.jimfs.attribute.UserLookupService.createGroupPrincipal;
import static com.google.jimfs.attribute.UserLookupService.createUserPrincipal;

import com.google.caliper.Param;
import com.google.caliper.api.Footprint;
import com.google.caliper.memory.ObjectGraphMeasurer;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.providers.AclAttributeProvider;
import com.google.jimfs.attribute.providers.BasicAttributeProvider;
import com.google.jimfs.attribute.providers.DosAttributeProvider;
import com.google.jimfs.attribute.providers.OwnerAttributeProvider;
import com.google.jimfs.attribute.providers.PosixAttributeProvider;
import com.google.jimfs.attribute.providers.UnixAttributeProvider;
import com.google.jimfs.attribute.providers.UserDefinedAttributeProvider;

import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * @author Colin Decker
 */
public class FileFootprintBenchmark {

  @Param
  private AttributeConfiguration attributeConfiguration;

  @Footprint
  public File footprintEmptyContentFile() {
    File file = new File(1, NoContent.INSTANCE);
    for (AttributeProvider provider : attributeConfiguration.createProviders()) {
      provider.setInitial(file);
    }
    return file;
  }

  public static void main(String[] args) {
    FileFootprintBenchmark benchmark = new FileFootprintBenchmark();
    for (AttributeConfiguration attributeConfiguration : AttributeConfiguration.values()) {
      benchmark.attributeConfiguration = attributeConfiguration;

      System.out.println(attributeConfiguration);
      ObjectGraphMeasurer.Footprint footprint = ObjectGraphMeasurer.measure(
          benchmark.footprintEmptyContentFile());
      printFootprint(footprint);
      System.out.println();
    }


  }

  private static void printFootprint(ObjectGraphMeasurer.Footprint footprint) {
    System.out.println("Objects:        " + footprint.getObjects());
    System.out.println("Non-null refs:  " + footprint.getNonNullReferences());
    System.out.println("Null refs:      " + footprint.getNullReferences());
    System.out.println("Primitives:");
    for (Class<?> primitiveType : footprint.getPrimitives().elementSet()) {
      int count = footprint.getPrimitives().count(primitiveType);
      System.out.println("     " + Strings.padEnd(primitiveType.toString(), 11, ' ') + count);
    }
    System.out.println(getSize(footprint, true) + " bytes (with UseCompressedOops)");
    System.out.println(getSize(footprint, false) + " bytes (with -UseCompressedOops)");
  }

  private static int getSize(ObjectGraphMeasurer.Footprint footprint, boolean compressedOops) {
    int result = 0;
    result += footprint.getObjects() * 16; // header
    result += footprint.getAllReferences() * (compressedOops ? 4 : 8);
    for (Class<?> primitiveType : footprint.getPrimitives().elementSet()) {
      result += footprint.getPrimitives().count(primitiveType) * sizeOf(primitiveType);
    }
    return result;
  }

  /**
   * These are minimums. In reality, a byte could take 8 bytes due to the 8 byte memory alignment
   * requirement, but it all depends on whether or not multiple primitives can be packed in the same
   * 8 byte allocation (which they can in arrays if nothing else).
   */
  private static final ImmutableMap<Class<?>, Integer> PRIMITIVE_SIZES =
      ImmutableMap.<Class<?>, Integer>builder()
          .put(int.class, 4)
          .put(long.class, 8)
          .put(short.class, 2)
          .put(char.class, 2)
          .put(byte.class, 1)
          .put(boolean.class, 1)
          .put(float.class, 4)
          .put(double.class, 8)
          .build();

  private static int sizeOf(Class<?> primitiveType) {
    return PRIMITIVE_SIZES.get(primitiveType);
  }

  @SuppressWarnings("unused")
  private enum AttributeConfiguration {
    BASIC_ONLY {
      @Override
      public ImmutableSet<BasicAttributeProvider> createProviders() {
        return ImmutableSet.of(BasicAttributeProvider.INSTANCE);
      }
    },

    UNIX_ATTRIBUTES {
      @Override
      public ImmutableSet<? extends AttributeProvider> createProviders() {
        OwnerAttributeProvider owner = new OwnerAttributeProvider(createUserPrincipal("user"));

        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-r--r--");
        PosixAttributeProvider posix = new PosixAttributeProvider(createGroupPrincipal("group"),
            permissions, owner);
        UnixAttributeProvider unix = new UnixAttributeProvider(posix);
        return ImmutableSet.of(BasicAttributeProvider.INSTANCE, owner, posix, unix);
      }
    },

    WINDOWS_ATTRIBUTES {
      @Override
      public ImmutableSet<? extends AttributeProvider> createProviders() {
        OwnerAttributeProvider owner = new OwnerAttributeProvider(createUserPrincipal("user"));
        AclAttributeProvider acl = new AclAttributeProvider(owner, ImmutableList.<AclEntry>of());
        return ImmutableSet.of(
            BasicAttributeProvider.INSTANCE,
            owner,
            DosAttributeProvider.INSTANCE,
            acl,
            UserDefinedAttributeProvider.INSTANCE);
      }
    };

    public abstract ImmutableSet<? extends AttributeProvider> createProviders();
  }

  private static final class NoContent implements FileContent {

    private static final NoContent INSTANCE = new NoContent();

    @Override
    public FileContent copy() {
      return this;
    }

    @Override
    public long sizeInBytes() {
      return 0;
    }

    @Override
    public void delete() {
    }
  }
}
