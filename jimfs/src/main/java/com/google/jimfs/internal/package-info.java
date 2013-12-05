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
 * Package containing the internal implementation of the JimFS file system. With the exception of
 * {@link com.google.jimfs.internal.JimfsFileSystemProvider JimfsFileSystemProvider}, which should
 * not be used directly anyway, all classes in this package are package-private.
 *
 * <h3>Overview</h3>
 *
 * {@link com.google.jimfs.internal.JimfsFileSystem JimfsFileSystem} instances are created by
 * {@link com.google.jimfs.internal.JimfsFileSystems JimfsFileSystems} using a user-provided
 * {@link com.google.jimfs.Configuration Configuration}. The configuration is used to create the
 * various classes that implement the file system with the correct settings and to create the file
 * system root directories and working directory. The file system is then used to create the
 * {@code Path} objects that all file system operations use.
 *
 * <p>Once created, the primary entry points to the file system are
 * {@link com.google.jimfs.internal.JimfsFileSystemProvider JimfsFileSystemProvider}, which handles
 * calls to methods in {@link java.nio.file.Files}, and
 * {@link com.google.jimfs.internal.JimfsSecureDirectoryStream JimfsSecureDirectoryStream}, which
 * provides methods that are similar to those of the file system provider but which treat relative
 * paths as relative to the stream's directory rather than the file system's working directory.
 *
 * <p>The implementation of the methods on both of those classes is handled by the
 * {@link com.google.jimfs.internal.FileSystemView FileSystemView} class, which acts as a view of
 * the file system with a specific working directory. The file system provider uses the file
 * system's default view, while each secure directory stream uses a view specific to that stream.
 *
 * <p>File system views make use of the file system's singleton
 * {@link com.google.jimfs.internal.JimfsFileStore JimfsFileStore} which handles file creation,
 * storage and attributes. The file store delegates to several other classes to handle each of
 * these:
 *
 * <ul>
 *   <li>{@link com.google.jimfs.internal.FileFactory FileFactory} handles creation of new file
 *   objects.</li>
 *   <li>{@link com.google.jimfs.internal.HeapDisk HeapDisk} handles creation and storage of
 *   {@link com.google.jimfs.internal.ByteStore ByteStore} instances, which act as the content of
 *   regular files.</li>
 *   <li>{@link com.google.jimfs.internal.FileTree FileTree} stores the root of the file hierarchy
 *   and handles file lookup.</li>
 *   <li>{@link com.google.jimfs.internal.AttributeService AttributeService} handles file
 *   attributes, using a set of
 *   {@link com.google.jimfs.attribute.AttributeProvider AttributeProvider} implementations to
 *   handle each supported file attribute view.</li>
 * </ul>
 *
 * <h3>Paths</h3>
 *
 * The implementation of {@link java.nio.file.Path} for the file system is
 * {@link com.google.jimfs.internal.JimfsPath JimfsPath}. Paths are created by a
 * {@link com.google.jimfs.internal.PathService PathService} with help from the file system's
 * configured {@link com.google.jimfs.path.PathType PathType}.
 *
 * <p>Paths are made up of {@link com.google.jimfs.internal.Name Name} objects, which also serve as
 * the file names in directories. A name has two forms:
 *
 * <ul>
 *   <li>The <b>display form</b> is used in {@code Path} for {@code toString()}. It is also used for
 *   determining the equality and sort order of {@code Path} objects for most file systems.</li>
 *   <li>The <b>canonical form</b> is used for equality of two {@code Name} objects. This affects
 *   the notion of name equality in the file system itself for file lookup. A file system may be
 *   configured to use the canonical form of the name for path equality (a Windows-like file system
 *   configuration does this, as the real Windows file system implementation uses case-insensitive
 *   equality for its path objects.</li>
 * </ul>
 *
 * <p>The canonical form of a name is created by applying a series of
 * {@linkplain com.google.jimfs.path.Normalization normalizations} to the original string. These
 * normalization may be either a Unicode normalization (e.g. NFD) or case folding normalization for
 * case-insensitivity. Normalizations may also be applied to the display form of a name, but this
 * is currently only done for a Mac OS X type configuration.
 *
 * <h3>Files</h3>
 *
 * All files in the file system are an instance of {@link com.google.jimfs.internal.File File}. A
 * file object contains the file's attributes as well as a reference to the file's
 * {@linkplain com.google.jimfs.internal.FileContent content}.
 *
 * <p>There are three types of file content:
 *
 * <ul>
 *   <li>{@link com.google.jimfs.internal.DirectoryTable DirectoryTable} - a map linking file names
 *   to {@linkplain com.google.jimfs.internal.DirectoryEntry directory entries}. A file with a
 *   directory table as its content is, obviously, a <i>directory</i>.</li>
 *   <li>{@link com.google.jimfs.internal.ByteStore ByteStore} - an in-memory store for raw bytes.
 *   A file with a byte store as its content is a <i>regular file</i>.</li>
 *   <li>{@link com.google.jimfs.internal.JimfsPath JimfsPath} - A file with a path as its content
 *   is a <i>symbolic link</i>.</li>
 * </ul>
 *
 * <p>{@link com.google.jimfs.internal.JimfsFileChannel JimfsFileChannel},
 * {@link com.google.jimfs.internal.JimfsInputStream JimfsInputStream} and
 * {@link com.google.jimfs.internal.JimfsOutputStream JimfsOutputStream} implement the standard
 * channel/stream APIs for regular files.
 *
 * <p>{@link com.google.jimfs.internal.JimfsDirectoryStream JimfsDirectoryStream} and
 * {@link com.google.jimfs.internal.JimfsSecureDirectoryStream JimfsSecureDirectoryStream} handle
 * reading the entries of a directory. The secure directory stream additionally contains a
 * {@code FileSystemView} with its directory as the working directory, allowing for operations
 * relative to the actual directory file rather than just the path to the file. This allows the
 * operations to continue to work as expected even if the directory is moved.
 *
 * <p>A directory can be watched for changes using the {@link java.nio.file.WatchService}
 * implementation, {@link com.google.jimfs.internal.PollingWatchService PollingWatchService}.
 *
 * <h3>Regular files</h3>
 *
 * Currently, the only implementation for regular file content is
 * {@link com.google.jimfs.internal.ByteStore ByteStore}, which makes use of a singleton
 * {@link com.google.jimfs.internal.HeapDisk HeapDisk}. A disk is a resizable factory and cache for
 * fixed size blocks of memory. These blocks are allocated to files as needed and returned to the
 * disk when a file is deleted or truncated. When cached free blocks are available, those blocks
 * are allocated to files first. If more blocks are needed, they are created.
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