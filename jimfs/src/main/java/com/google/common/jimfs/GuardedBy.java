/*
 * Copyright 2017 The Error Prone Authors.
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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

// TODO(cpovirk): Delete this in favor of the copy in Error Prone once that has a module name.
/** Indicates that the annotated element should be used only while holding the specified lock. */
@Target({FIELD, METHOD})
@Retention(CLASS)
@interface GuardedBy {
  /**
   * The lock that should be held, specified in the format <a
   * href="http://jcip.net/annotations/doc/net/jcip/annotations/GuardedBy.html">given in Java
   * Concurrency in Practice</a>.
   */
  String value();
}
