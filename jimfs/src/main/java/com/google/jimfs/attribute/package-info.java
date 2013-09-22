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
 * This package provides classes for configuring the handling of file attributes for a file system.
 * It centers around the {@link AttributeProvider} interface, which defines a group (or view) of
 * file attributes such as "basic" or "posix". See the Java
 * <a href="http://docs.oracle.com/javase/tutorial/essential/io/fileAttr.html">tutorial</a> on file
 * attributes for more information about attribute views.
 *
 * <p>Attribute providers may additionally implement {@link AttributeViewProvider} if they
 * have a {@link java.nio.file.attribute.FileAttributeView FileAttributeView} interface they can
 * provide and {@link AttributeReader} if they have a subclass of
 * {@link java.nio.file.attribute.BasicFileAttributes BasicFileAttributes} they can read for a
 * file.
 *
 * <p>Each attribute provider may also declare that it "inherits" one or more other attribute
 * views, allowing it to provide the attributes that view provides. For example, "posix" inherits
 * "basic", allowing "posix:isDirectory" to return the basic attribute "isDirectory".
 *
 * <p>The {@code providers} subpackage contains a standard set of providers covering the attributes
 * views available on a typical Unix or Windows machine.
 */
@ParametersAreNonnullByDefault
package com.google.jimfs.attribute;

import javax.annotation.ParametersAreNonnullByDefault;