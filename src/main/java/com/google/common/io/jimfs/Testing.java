package com.google.common.io.jimfs;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;

import com.ibm.icu.text.Normalizer2;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;

import javax.annotation.Nullable;

/**
 * @author Colin Decker
 */
public class Testing {

  public static void main(String[] args) throws IOException {
    Normalizer2 normalizer = Normalizer2.getNFKCCasefoldInstance();
    System.out.println(normalizer.normalize("aBcdEfG"));
    //System.out.println(Normalizer.normalize("aBcdEfG", Normalizer.Form.NFD));

    // testUsingTree();
    // testUsingApi();

    /*Path workingAbsolute = Paths.get("/Users/cgdecker/temp/working");
    Path workingRelative = Paths.get("");
    System.out.println(toStructuralString(workingRelative));

    System.out.println(Files.isSameFile(workingAbsolute, workingRelative));*/

    /*Files.createFile(Paths.get("bar/foo"));
    Files.delete(Paths.get("../working/bar/foo"));*/

    //System.out.println(toStructuralString(Paths.get("", "/", "", "a")));

    //System.out.println(toStructuralString(Jimfs.newUnixLikeFileSystem().getPath("", "/", "", "a")));

    //FileSystem fs = FileSystems.getDefault();
    /*FileSystem fs = new JimfsFileSystemProvider().newFileSystem(URI.create("jimfs://foo"), null);
    System.out.println(fs.getPath("", "", ""));
    System.out.println(toStructuralString(fs.getPath("", "", "")));*/
    //Files.move(fs.getPath("foo"), fs.getPath("bar"));
    /*System.out.println(Files.isHidden(fs.getPath(".baz")));
    System.out.println(toStructuralString(fs.getPath("").getParent()));
    System.out.println(toStructuralString(fs.getPath("foo").getParent()));
    System.out.println(toStructuralString(fs.getPath("foo").subpath(0, 0)));*/
    /*Path empty = fs.getPath("");
    System.out.println(empty.toAbsolutePath());
    System.out.println(empty.toRealPath());

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(empty)) {
      if (stream instanceof SecureDirectoryStream<?>) {
        System.out.println("secure");

        SecureDirectoryStream<Path> secureStream = (SecureDirectoryStream<Path>) stream;
        Iterator<Path> iterator = secureStream.iterator();

        Files.delete(empty);

        while (iterator.hasNext()) {
          System.out.println(iterator.next());
        }

        Object key = secureStream.getFileAttributeView(BasicFileAttributeView.class)
            .readAttributes()
            .fileKey();
        System.out.println(key);
      } else {
        System.out.println("not secure");
      }
    }
    Files.move(fs.getPath("foo/.."), empty.toAbsolutePath().resolveSibling("working2"));
    System.out.println(empty.toAbsolutePath());
    System.out.println(empty.toRealPath());*/

    /*Path relative = fs.getPath("relative");
    Path relativeParent = relative.resolve("..");
    System.out.println(toStructuralString(relative));
    System.out.println(toStructuralString(relativeParent));
    System.out.println(toStructuralString(relativeParent.normalize()));

    Path empty = fs.getPath("");
    System.out.println(toStructuralString(empty));
    System.out.println(empty.toRealPath());

    System.out.println(Files.exists(relativeParent.normalize()));

    System.out.println(Files.isSameFile(empty, relativeParent.normalize()));
    //System.out.println(relativeParent.toRealPath());
    System.out.println(relativeParent.normalize().toRealPath());

    System.out.println(toStructuralString(fs.getPath("/", "foo", "", "bar/baz")));*/
    /*FileSystem fs = FileSystems.getDefault();
    Path foo = fs.getPath("");
    System.out.println(foo.getRoot());
    Files.delete(foo);
    System.out.println(foo.getNameCount());
    System.out.println(foo.toAbsolutePath().getNameCount());
    System.out.println(foo.toAbsolutePath());*/
    /*Path workingDir = foo.toAbsolutePath().getParent();
    System.out.println(workingDir);
    //Files.move(workingDir, workingDir.resolveSibling("working2"));
    System.out.println(foo.toAbsolutePath());
    System.out.println(fs.getPath("foo").toAbsolutePath());

    Files.delete(workingDir);

    Files.createDirectory(fs.getPath("foo"));

    Files.move(workingDir.resolveSibling("working2"), workingDir);*/
  }

  /*private static void testUsingTree() throws IOException {
    JimfsFileSystem fs = new JimfsFileSystem(new JimfsFileSystemProvider(), "/",
        ImmutableSet.of("/"), "/");
    FileTree storage = fs.getSuperRoot();

    storage.print();

    JimfsPath root = fs.getPath("/");
    JimfsPath foo = root.resolve("foo");
    JimfsPath bar = foo.resolve("bar");
    JimfsPath baz = foo.resolveSibling("baz.txt");

    System.out.println(root);
    System.out.println(foo);
    System.out.println(bar);
    System.out.println(baz);
    storage.addFile(foo, storage.createDirectory());
    storage.addFile(bar, storage.createDirectory());
    storage.addFile(baz, storage.createRegularFile());
    storage.addFile(bar.resolve("asdf.txt"), storage.createRegularFile());
    storage.addFile(bar.resolve("link.txt"), storage.createSymbolicLink(fs.getPath("asdf.txt")));

    storage.print();

    storage.deleteFile(baz);

    storage.print();

    storage.addFile(foo.resolve("baz"), storage.createSymbolicLink(fs.getPath("bar")));

    storage.print();

    System.out
        .println(storage.getMetadata(fs.getPath("/foo/baz/link.txt"), LinkHandling.FOLLOW_LINKS));
  }*/

  private static void testUsingApi() throws IOException {
    JimfsFileSystemProvider provider = new JimfsFileSystemProvider();
    JimfsFileSystem fs = provider.newFileSystem(URI.create("jimfs://foo"), null);

    System.out.println(toStructuralString(fs.getPath("")));
    System.out.println(toStructuralString(fs.getPath("", "/", "a")));

    print(fs);

    Path root = fs.getPath("/");
    Path work = root.resolve("work");
    Path bar = work.resolve("bar");
    Path baz = work.resolveSibling("baz.txt");

    /*System.out.println(root);
    System.out.println(foo);
    System.out.println(bar);
    System.out.println(baz);*/
    //Files.createDirectory(foo);
    Files.createDirectory(bar);
    Files.createFile(baz);
    Files.createFile(bar.resolve("asdf.txt"));
    Files.createSymbolicLink(bar.resolve("link.txt"), fs.getPath("asdf.txt"));

    print(fs);

    Files.delete(baz);

    print(fs);

    Files.createSymbolicLink(work.resolve("baz"), fs.getPath("bar"));

    print(fs);

    Path relative = fs.getPath("relative");
    Files.createDirectory(relative);

    print(fs);

    Files.move(relative, bar.resolve("test"));

    print(fs);
  }

  private static void print(FileSystem fs) throws IOException {
    System.out.println("----------------------------------------------");
    for (Path path : fs.getRootDirectories()) {
      print(path, 0);
    }
  }

  private static void print(Path path, int depth) throws IOException {
    System.out.print(Strings.repeat("  ", depth));
    System.out.println(toString(path));
    if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
        for (Path entry : entries) {
          print(path.resolve(entry), depth + 1);
        }
      }
    }
  }

  private static String toString(Path path) throws IOException {
    Path name = path.getNameCount() > 0 ? path.getFileName() : path.getRoot();
    String string = name.toString();

    if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
      return string + " (D)";
    } else if (Files.isSymbolicLink(path)) {
      return string + " -> " + Files.readSymbolicLink(path);
    } else {
      return string;
    }
  }

  private static String toStructuralString(@Nullable Path path) {
    if (path == null) {
      return "null";
    }
    /*
     * return Stream.from(path)
     *     .transform(Testing::toQuotedString)
     *     .collect(toList());
     */
    String root = toQuotedString(path.getRoot());
    String names = FluentIterable.from(path)
        .transform(new Function<Path, String>() {
          @Override
          public String apply(@Nullable Path input) {
            return toQuotedString(input);
          }
        })
        .toString();
    return "Path{root=" + root + ", names=" + names + "}";
  }

  private static String toQuotedString(@Nullable Path path) {
    return path == null ? "null" : "'" + path + "'";
  }
}
