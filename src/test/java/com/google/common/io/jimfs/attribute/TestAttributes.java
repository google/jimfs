package com.google.common.io.jimfs.attribute;

import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author Colin Decker
 */
interface TestAttributes extends BasicFileAttributes {

  String foo();

  long bar();

  int baz();
}
