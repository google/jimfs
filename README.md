Jimfs
=====

Jimfs is an in-memory file system for Java 8 and above, implementing the
[java.nio.file](http://docs.oracle.com/javase/7/docs/api/java/nio/file/package-summary.html)
abstract file system APIs.

[![Build Status](https://github.com/google/jimfs/workflows/CI/badge.svg?branch=master)](https://github.com/google/jimfs/actions)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.jimfs/jimfs/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.jimfs/jimfs)

Getting started
---------------

The latest release is
[1.3.0](https://github.com/google/jimfs/releases/tag/v1.3.0).

It is available in Maven Central as
[com.google.jimfs:jimfs:1.3.0](http://search.maven.org/#artifactdetails%7Ccom.google.jimfs%7Cjimfs%7C1.3.0%7Cjar):

```xml
<dependency>
  <groupId>com.google.jimfs</groupId>
  <artifactId>jimfs</artifactId>
  <version>1.3.0</version>
</dependency>
```

Basic use
---------

The simplest way to use Jimfs is to just get a new `FileSystem` instance from the `Jimfs` class and
start using it:

```java
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
...

// For a simple file system with Unix-style paths and behavior:
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
Path foo = fs.getPath("/foo");
Files.createDirectory(foo);

Path hello = foo.resolve("hello.txt"); // /foo/hello.txt
Files.write(hello, ImmutableList.of("hello world"), StandardCharsets.UTF_8);
```

What's supported?
-----------------

Jimfs supports almost all the APIs under `java.nio.file`. It supports:

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

Jimfs also supports creating file systems that, for example, use Windows-style paths and (to an
extent) behavior. In general, however, file system behavior is modeled after UNIX and may not
exactly match any particular real file system or platform.

OSGi considerations
-----------------

The SPI mechanism that registers file system providers is not
OSGi-friendly. It will load the provider class into the system class
loader, and then if you try to use `Paths.get(URI)` from inside your
OSGi code, you'll fail with an `IllegalAccessException`. 

There are two ways around this. In spite of the fact that the Jimfs
jar has full OSGi metadata, you can set up the `com.google.common.jimfs`
as part of the system bundle. This is, by far, the most convenient if
you are just using Jimfs for testing.

Your alternative is to never use `Paths.get(URI)` or any other API
that uses the SPI to obtain the file system provider. If you always
create your own `FileSystem` objects and call `getPath(...)` on them,
it all works fine.


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
