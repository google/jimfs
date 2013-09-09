JIMFS
=====

JIMFS is an in-memory file system for Java 7 and above, implementing the
[java.nio.file](http://docs.oracle.com/javase/7/docs/api/java/nio/file/package-summary.html)
abstract file system APIs.

Basic use
---------

The simplest way to use JIMFS is to just get a new `FileSystem` instance from the `Jimfs` class and
start using it:

```java
import com.google.jimfs.Jimfs;
...

// For a file system with Unix-style paths and attributes:
FileSystem fs = Jimfs.newUnixLikeFileSystem();

Path foo = fs.getPath("/foo");
Files.createDirectory(foo);

Files.write(foo.resolve("hello.txt"), ImmutableList.of("hello"), StandardCharsets.UTF_8);

// Or for Windows:
FileSystem fs = Jimfs.newWindowsLikeFileSystem();
```

What's supported?
-----------------

JIMFS supports almost all the APIs under `java.nio.file`. It supports:

- Creating, deleting, moving and copying files and directories.
- Reading and writing files with `FileChannel` or `SeekableByteChannel`, `InputStream`,
  `OutputStream`, etc.
- Symbolic links.
- Hard links to regular files.
- `SecureDirectoryStream`, for operations relative to an _open_ directory.
- Glob and regex path filtering with `PathMatcher`.
- Watching for changes to a directory with a `WatchService`.
- File attributes. Built-in attribute views that can be supported include "basic", "owner",
  "posix", "unix", "dos", "acl" and "user". Do note, however, that not all attribute views provide
  _useful_ attributes. For example, while setting and reading POSIX file permissions is possible
  with the "posix" view, those permissions will not actually affect the behavior of the file system.

License
-------

```
Copyright 2013 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```