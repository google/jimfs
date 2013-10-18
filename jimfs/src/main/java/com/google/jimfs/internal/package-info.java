/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Package containing the internal implementation of the JIMFS file system. With the exception of
 * {@link JimfsFileSystemProvider}, which should not be used directly anyway, classes in this
 * package are package-private.
 *
 * <h3>Overview of the file system design:</h3>
 *
 * {@link JimfsFileSystem} is the concrete implementation of {@link java.nio.file.FileSystem}.
 *
 * <p>Most of the details of the implementation of file system operations are found in the
 * {@link FileSystemService} class. The file system service contains two root directories. The
 * first, called the super root, is the root of the whole file hierarchy. It is a special directory
 * that links path root names (such as "/" or "C:\") to the actual root directory. It acts as the
 * base for <i>absolute path</i> operations. The second root directory is the <i>working
 * directory</i>. Located somewhere in the hierarchy under the super root, the working directory
 * acts the base for <i>relative path</i> operations. The file system can have additional
 * file system services for each {@link java.nio.file.SecureDirectoryStream} that is opened. Those
 * services may use a different working directory than the file system's default working directory.
 *
 * <p>While the {@code FileSystemView} handles most of the work for implementing file system
 * operations, lookups are done using the {@link LookupService}. It simply handles resolving paths
 * to files, including following symbolic links if necessary. Lookup results are returned as a
 * {@link LookupResult}, which contains not only the file that was located (if any) but its parent
 * directory as well.
 *
 * <p>{@link JimfsFileStore}, an implementation of {@link java.nio.file.FileStore}, handles file
 * creation and file attributes. The file store's handling of attributes is controlled by the set
 * of installed {@link com.google.jimfs.attribute.AttributeProvider AttributeProvider} instances,
 * which are stored in an {@link AttributeProviderRegistry}.
 *
 * <h3>Paths</h3>
 *
 * The implementation of {@link java.nio.file.Path} for the file system is {@link JimfsPath}. Paths
 * are created by a {@link PathService} with help from the file system's configured
 * {@link com.google.jimfs.path.PathType PathType}.
 *
 * <p>Paths are made up of {@link com.google.jimfs.internal.Name Name} objects, which also serve as the
 * file names in directories. Names are basically enhanced strings that may have different notions
 * of equality, such as case-insensitive equality.
 *
 * <h3>Files and attributes</h3>
 *
 * All files in the file system are an instance of {@link File}. A file object contains the file's
 * attributes as well as a reference to the file's {@linkplain FileContent content}.
 *
 * <p>There are three types of file content:
 *
 * <ul>
 *   <li>{@link DirectoryTable} - a map linking file names to file objects. A file with a
 *   directory table as its content is, obviously, a <i>directory</i>.</li>
 *   <li>{@link ByteStore} - an in-memory store for raw bytes. A file with a byte store as its
 *   content is a <i>regular file</i>.</li>
 *   <li>{@link JimfsPath} - A file with a path as its content is a <i>symbolic link</i>.</li>
 * </ul>
 *
 * <p>{@link JimfsFileChannel}, an implementation of
 * {@link java.nio.channels.FileChannel FileChannel} that uses the file's {@code ByteStore},
 * handles all reading and writing of regular files. The {@link java.io.InputStream InputStream}
 * and {@link java.io.OutputStream OutputStream} implementations for files are just standard
 * wrappers around a channel.
 *
 * <p>{@link JimfsDirectoryStream} and {@link JimfsSecureDirectoryStream} handle reading the
 * entries of a directory. The secure directory stream additionally contains a
 * {@code FileSystemView} with its directory as the working directory, allowing for operations
 * relative to the actual directory file rather than just the path to the file. This allows the
 * operations to continue to work as expected even if the directory is moved.
 *
 * <p>A directory can be watched for changes using the {@link java.nio.file.WatchService}
 * implementation, {@link PollingWatchService}.
 *
 * <h3>Linking</h3>
 *
 * When a file is mapped to a file name in a directory table, it is <i>linked</i>. Each type of
 * file has different rules governing how it is linked.
 *
 * <ul>
 *   <li>Directory - A directory has two or more links to it. The first is the link from
 *   its parent directory to it. This link is the name of the directory. The second is the
 *   <i>self</i> link (".") which links the directory to itself. The directory may also have any
 *   number of additional <i>parent</i> links ("..") from child directories back to it.</li>
 *   <li>Regular file - A regular file has one link from its parent directory by default. However,
 *   regular files are also allowed to have any number of additional user-created hard links, from
 *   the same directory with different names and/or from other directories with any names.</li>
 *   <li>Symbolic link - A symbolic link can only have one link, from its parent directory.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 *
 * All file system operations should be safe in a multithreaded environment. The file hierarchy
 * itself is protected by a file system level read-write lock. This ensures safety of all
 * modifications to directory tables as well as atomicity of operations like file moves. Regular
 * files are each protected by a read-write lock on their content which is obtained for each read
 * or write operation. File attributes are not protected by locks, but are stored in thread-safe
 * concurrent maps and atomic numbers.
 */
@ParametersAreNonnullByDefault
package com.google.jimfs.internal;

import javax.annotation.ParametersAreNonnullByDefault;