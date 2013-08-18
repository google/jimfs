package com.google.common.io.jimfs.attribute;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;

/**
 * @author Colin Decker
 */
interface TestAttributeView extends FileAttributeView {

  TestAttributes readAttributes() throws IOException;

  void setBar(long bar) throws IOException;

  void setBaz(int baz) throws IOException;
}
