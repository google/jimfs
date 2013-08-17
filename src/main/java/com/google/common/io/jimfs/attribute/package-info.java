/**
 * This package provides handling for file attributes. It centers around the
 * {@link AttributeProvider} interface, which defines a group of attributes such as "basic" or
 * "posix". Attribute providers may additionally implement {@link AttributeViewProvider} if they
 * have a {@link java.nio.file.attribute.FileAttributeView FileAttributeView} interface they can
 * provide and {@link AttributeReader} if they have a subclass of
 * {@link java.nio.file.attribute.BasicFileAttributes BasicFileAttributes} they can read for file.
 * Each attribute provider may also declare that it "inherits" one or more other attribute groups,
 * allowing it to provide the attributes that group provides. For example, "posix" inherits
 * "basic", allowing "posix:isDirectory" to return the basic attribute "isDirectory".
 *
 * <p>One or more attribute providers are combined in an {@link AttributeService}, which acts as
 * a central service for all attribute handling for a file system instance.
 */
package com.google.common.io.jimfs.attribute;

