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
// For a file system with Unix-style paths and attributes:
FileSystem fs = Jimfs.newUnixLikeFileSystem();

Path foo = fs.getPath("/foo");
Files.createDirectory(foo);

Files.write(foo.resolve("hello.txt"), ImmutableList.of("hello"), StandardCharsets.UTF_8);

// Or for Windows:
FileSystem fs = Jimfs.newWindowsLikeFileSystem();
```

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