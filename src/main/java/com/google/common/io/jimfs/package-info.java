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
 * Base package for the JIMFS file system. In addition to containing the implementations of
 * {@link java.nio.file.FileSystem FileSystem} and
 * {@link java.nio.file.spi.FileSystemProvider FileSystemProvider}, provides the {@link Jimfs}
 * class as an entry point for most users. This class contains simple factory methods for when you
 * just want to get an in-memory file system.
 */
package com.google.common.io.jimfs;

