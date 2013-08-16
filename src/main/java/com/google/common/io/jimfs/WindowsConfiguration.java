package com.google.common.io.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.util.List;

/**
 * @author Colin Decker
 */
final class WindowsConfiguration extends JimfsConfiguration {

  private static final Joiner JOINER = Joiner.on('\\');
  private static final CharMatcher SEPARATOR_MATCHER = CharMatcher.anyOf("\\/");
  private static final Splitter SPLITTER = Splitter.on(SEPARATOR_MATCHER).omitEmptyStrings();

  private final String workingDirectory;
  private final String defaultUser;
  private final List<AclEntry> defaultAclEntries;
  private final ImmutableSet<String> roots;

  WindowsConfiguration() {
    this("C:\\work", "user", ImmutableList.<AclEntry>of(), "C:\\");
  }

  WindowsConfiguration(String workingDirectory, String defaultUser,
      List<AclEntry> defaultAclEntries, String... roots) {
    this.workingDirectory = checkNotNull(workingDirectory);
    this.defaultUser = checkNotNull(defaultUser);
    this.defaultAclEntries = ImmutableList.copyOf(defaultAclEntries);
    this.roots = ImmutableSet.copyOf(roots);
  }

  @Override
  public String getSeparator() {
    return "\\";
  }

  @Override
  protected Iterable<String> getAlternateSeparators() {
    return ImmutableSet.of("/");
  }

  @Override
  public Iterable<String> getRoots() {
    return roots;
  }

  @Override
  public String getWorkingDirectory() {
    return workingDirectory;
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    return (boolean) Files.getAttribute(path, "dos:hidden");
  }

  @Override
  protected Iterable<AttributeProvider> getAttributeProviders() {
    BasicAttributeProvider basic = new BasicAttributeProvider();
    OwnerAttributeProvider owner = new OwnerAttributeProvider(
        UserLookupService.createUserPrincipal(defaultUser));
    DosAttributeProvider dos = new DosAttributeProvider(basic);
    AclAttributeProvider acl = new AclAttributeProvider(owner, defaultAclEntries);
    UserAttributeProvider user = new UserAttributeProvider();
    return ImmutableList.<AttributeProvider>of(basic, owner, dos, acl, user);
  }

  @Override
  public JimfsPath parsePath(JimfsFileSystem fileSystem, List<String> path) {
    String joined = JOINER.join(path);
    String root = null;
    if (joined.length() >= 2
        && CharMatcher.JAVA_LETTER.matches(joined.charAt(0))
        && joined.charAt(1) == ':') {
      root = joined.substring(0, 2).toUpperCase();
      joined = joined.substring(2);
    }

    Iterable<String> split = SPLITTER.split(joined);
    return JimfsPath.create(fileSystem, root, split);
  }

  public static void main(String[] args) throws IOException {
    WindowsConfiguration config = new WindowsConfiguration();
    JimfsFileSystem fs = new JimfsFileSystem(new JimfsFileSystemProvider(), config);

    Path path = fs.getPath("C:\\").resolve("..").normalize();
    System.out.println(path);
    System.out.println(path.getRoot());
    System.out.println(Iterables.toString(path));

    System.out.println(Files.readAttributes(path, "dos:*"));
  }
}
