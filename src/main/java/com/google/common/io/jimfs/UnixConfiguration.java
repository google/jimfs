package com.google.common.io.jimfs;

import static com.google.common.io.jimfs.JimfsConfiguration.Feature.CASE_INSENSITIVE_NAMES;
import static com.google.common.io.jimfs.JimfsConfiguration.Feature.GROUPS;
import static com.google.common.io.jimfs.JimfsConfiguration.Feature.LINKS;
import static com.google.common.io.jimfs.JimfsConfiguration.Feature.SECURE_DIRECTORY_STREAMS;
import static com.google.common.io.jimfs.JimfsConfiguration.Feature.SYMBOLIC_LINKS;
import static com.google.common.io.jimfs.UserLookupService.createGroupPrincipal;
import static com.google.common.io.jimfs.UserLookupService.createUserPrincipal;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * Configuration for UNIX-like instances of {@link JimfsFileSystem}.
 *
 * @author Colin Decker
 */
final class UnixConfiguration extends JimfsConfiguration {

  private static final Joiner JOINER = Joiner.on('/');
  private static final Splitter SPLITTER = Splitter.on('/').omitEmptyStrings();

  private final String workingDirectory;
  private final String defaultOwner;
  private final String defaultGroup;
  private final String defaultPermissions;

  public UnixConfiguration(String workingDirectory,
      String defaultOwner, String defaultGroup, String defaultPermissions) {
    this.workingDirectory = workingDirectory;
    this.defaultOwner = defaultOwner;
    this.defaultGroup = defaultGroup;
    this.defaultPermissions = defaultPermissions;
  }

  @Override
  public String getSeparator() {
    return "/";
  }

  @Override
  public Iterable<String> getRoots() {
    return ImmutableSet.of("/");
  }

  @Override
  public String getWorkingDirectory() {
    return workingDirectory;
  }

  @Override
  public boolean isHidden(Path path) {
    Path fileName = path.getFileName();
    return fileName != null && fileName.toString().startsWith(".");
  }

  @Override
  protected Iterable<Feature> getSupportedFeatures() {
    return ImmutableSet.of(SYMBOLIC_LINKS, LINKS, GROUPS, SECURE_DIRECTORY_STREAMS);
  }

  @Override
  public JimfsPath parsePath(JimfsFileSystem fileSystem, List<String> parts) {
    if (parts.isEmpty()) {
      return JimfsPath.empty(fileSystem);
    }

    String first = parts.get(0);
    Name root = null;
    if (first.startsWith("/")) {
      root = createName("/", true);
      if (first.length() == 1) {
        parts.remove(0);
      } else {
        parts.set(0, first.substring(1));
      }
    }

    String joined = JOINER.join(parts);
    Iterable<String> split = SPLITTER.split(joined);

    return JimfsPath.create(fileSystem, root, toNames(split));
  }

  @Override
  public Iterable<AttributeProvider> getAttributeProviders() {
    BasicAttributeProvider basic = new BasicAttributeProvider();
    OwnerAttributeProvider owner =
        new OwnerAttributeProvider(createUserPrincipal(defaultOwner));
    PosixAttributeProvider posix =
        new PosixAttributeProvider(createGroupPrincipal(defaultGroup),
            PosixFilePermissions.fromString(defaultPermissions), basic, owner);
    UnixAttributeProvider unix = new UnixAttributeProvider(posix);
    return ImmutableSet.<AttributeProvider>of(basic, owner, posix, unix);
  }
}
