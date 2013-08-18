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
 * This package provides the implementation of core file system functionality. It provides
 * {@link File}, which represents a file and its metadata as well as the {@link FileContent}
 * interface that marks the classes which act as the actual content of files. It provides
 * {@link DirectoryTable}, which is the {@code FileContent} of a directory and links names to
 * files. {@link FileService} handles the creation and copying of {@code File} objects.
 *
 * <p>Finally, {@link FileTree} pulls it all together. A {@code FileTree} represents a tree of
 * directories and files with some particular directory as its base and provides methods that read
 * and modify the tree, given paths to files within the tree. A file system instance always
 * has two default file trees.
 *
 * <p>The first, called the "super root", has the super root pseudo-directory as its base. This
 * directory is not actually part of the visible file system, as no user action can affect it, but
 * rather just links the names of root directories (such as "/" or "C:\") to those root
 * directory files. All operations on absolute paths use the super root tree.
 *
 * <p>The second file tree is the working directory. The working directory is specified and created
 * when the file system is created. All operations on relative paths use the working directory
 * file tree.
 *
 * <p>File systems that support {@link java.nio.file.SecureDirectoryStream SecureDirectoryStream}
 * may have additional file trees, one for each {@code SecureDirectoryStream} that's created. These
 * file trees allow for the secure stream's behavior of resolving relative paths for its operations
 * against the actual directory object it represents, regardless of where that directory is located
 * in the full file tree and even if that directory is moved after it's created.
 */
package com.google.common.io.jimfs.file;

