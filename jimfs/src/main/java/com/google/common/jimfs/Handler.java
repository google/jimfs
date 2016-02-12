/*
 * Copyright 2015 Google Inc.
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

package com.google.common.jimfs;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * {@link URLStreamHandler} implementation for jimfs. Named {@code Handler} so that the class can
 * be found by Java as described in the documentation for
 * {@link URL#URL(String, String, int, String) URL}.
 *
 * <p>This class is only public because it is necessary for Java to find it. It is not intended
 * to be used directly.
 *
 * @author Colin Decker
 * @since 1.1
 */
public final class Handler extends URLStreamHandler {

  private static final String JAVA_PROTOCOL_HANDLER_PACKAGES = "java.protocol.handler.pkgs";

  /**
   * Registers this handler by adding the package {@code com.google.common} to the system property
   * {@code "java.protocol.handler.pkgs"}. Java will then look for this class in the {@code jimfs}
   * (the name of the protocol) package of {@code com.google.common}.
   *
   * @throws SecurityException if the system property that needs to be set to register this handler
   *     can't be read or written.
   */
  static void register() {
    register(Handler.class);
  }

  /**
   * Generic method that would allow registration of any properly placed {@code Handler} class.
   */
  static void register(Class<? extends URLStreamHandler> handlerClass) {
    checkArgument("Handler".equals(handlerClass.getSimpleName()));

    String pkg = handlerClass.getPackage().getName();
    int lastDot = pkg.lastIndexOf('.');
    checkArgument(lastDot > 0, "package for Handler (%s) must have a parent package", pkg);

    String parentPackage = pkg.substring(0, lastDot);

    String packages = System.getProperty(JAVA_PROTOCOL_HANDLER_PACKAGES);
    if (packages == null) {
      packages = parentPackage;
    } else {
      packages += "|" + parentPackage;
    }
    System.setProperty(JAVA_PROTOCOL_HANDLER_PACKAGES, packages);
  }

  /**
   * @deprecated Not intended to be called directly; this class is only for use by Java itself.
   */
  @Deprecated
  public Handler() {} // a public, no-arg constructor is required

  @Override
  protected URLConnection openConnection(URL url) throws IOException {
    return new PathURLConnection(url);
  }
}
