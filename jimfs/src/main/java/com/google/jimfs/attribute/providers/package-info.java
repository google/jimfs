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
 * Package containing a standard set of
 * {@link com.google.jimfs.attribute.AttributeProvider AttributeProvider} implementations.
 *
 * <p>The providers contained in this package are:
 *
 * <table>
 *   <tr>
 *     <td><b>AttributeProvider</b></td>
 *     <td><b>View Name</b></td>
 *     <td><b>View Interface</b></td>
 *     <td><b>Attributes Interface</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@link BasicAttributeProvider}</td>
 *     <td>{@code "basic"}</td>
 *     <td>{@link java.nio.file.attribute.BasicFileAttributeView BasicFileAttributeView}</td>
 *     <td>{@link java.nio.file.attribute.BasicFileAttributes BasicFileAttributes}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link OwnerAttributeProvider}</td>
 *     <td>{@code "owner"}</td>
 *     <td>{@link java.nio.file.attribute.FileOwnerAttributeView FileOwnerAttributeView}</td>
 *     <td>--</td>
 *   </tr>
 *   <tr>
 *     <td>{@link PosixAttributeProvider}</td>
 *     <td>{@code "posix"}</td>
 *     <td>{@link java.nio.file.attribute.PosixFileAttributeView PosixFileAttributeView}</td>
 *     <td>{@link java.nio.file.attribute.PosixFileAttributes PosixFileAttributes}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link UnixAttributeProvider}</td>
 *     <td>{@code "unix"}</td>
 *     <td>--</td>
 *     <td>--</td>
 *   </tr>
 *   <tr>
 *     <td>{@link DosAttributeProvider}</td>
 *     <td>{@code "dos"}</td>
 *     <td>{@link java.nio.file.attribute.DosFileAttributeView DosFileAttributeView}</td>
 *     <td>{@link java.nio.file.attribute.DosFileAttributes DosFileAttributes}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link AclAttributeProvider}</td>
 *     <td>{@code "acl"}</td>
 *     <td>{@link java.nio.file.attribute.AclFileAttributeView AclFileAttributeView}</td>
 *     <td>--</td>
 *   </tr>
 *   <tr>
 *     <td>{@link UserDefinedAttributeProvider}</td>
 *     <td>{@code "user"}</td>
 *     <td>{@link java.nio.file.attribute.UserDefinedFileAttributeView UserDefinedFileAttributeView}</td>
 *     <td>--</td>
 *   </tr>
 * </table>
 */
@ParametersAreNonnullByDefault
package com.google.jimfs.attribute.providers;

import javax.annotation.ParametersAreNonnullByDefault;