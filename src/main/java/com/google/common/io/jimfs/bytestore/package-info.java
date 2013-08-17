/**
 * This package provides the abstract {@link ByteStore} class and concrete subclasses of it.
 * {@code ByteStore}, which acts as the
 * {@link com.google.common.io.jimfs.file.FileContent FileContent} for a regular file, is an
 * in-memory, random access, read-write locking store of bytes. It also provides implementations of
 * {@link java.nio.channels.FileChannel FileChannel}, {@link java.io.InputStream InputStream} and
 * {@link java.io.OutputStream OutputStream} for reading and writing.
 */
package com.google.common.io.jimfs.bytestore;

