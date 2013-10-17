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
 * This package contains the abstract {@link AttributeProvider} class, which implements the
 * handling of attributes for a file attribute view such as "basic" or "posix". See the Java
 * <a href="http://docs.oracle.com/javase/tutorial/essential/io/fileAttr.html">tutorial</a> on file
 * attributes for more information about attribute views.
 *
 * <p>The package also contains the abstract {@link Inode} class, which acts as the interface for
 * storing and retrieving file attributes, and the {@link UserPrincipals} class, which provides
 * methods for creating {@code UserPrincipal} and {@code GroupPrincipal} instances.
 *
 * <p>Finally, the package contains a standard set of {@code AttributeProvider} implementations,
 * accessible through the {@link StandardAttributeProviders} class.
 */
@ParametersAreNonnullByDefault
package com.google.jimfs.attribute;

import javax.annotation.ParametersAreNonnullByDefault;