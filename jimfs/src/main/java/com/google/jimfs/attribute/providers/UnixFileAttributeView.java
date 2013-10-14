package com.google.jimfs.attribute.providers;

import java.nio.file.attribute.FileAttributeView;

/**
 * Dummy view interface for the "unix" view, which doesn't have a public view interface.
 *
 * @author Colin Decker
 */
interface UnixFileAttributeView extends FileAttributeView {
}
