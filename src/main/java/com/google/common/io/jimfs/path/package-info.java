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
 * This package provides the implementation of {@link java.nio.file.Path Path} along with several
 * other classes related to paths. The {@link Name} class, in addition to being what paths are made
 * up of, is used for the keys in directory tables. The {@link PathMatchers} class provides a
 * factory method for creating {@link java.nio.file.PathMatcher PathMatcher} instances for a file
 * system.
 */
package com.google.common.io.jimfs.path;

