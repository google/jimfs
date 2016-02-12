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

package com.google.common.jimfs;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Name}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class NameTest {

  @Test
  public void testNames() {
    assertThat(Name.create("foo", "foo")).isEqualTo(Name.create("foo", "foo"));
    assertThat(Name.create("FOO", "foo")).isEqualTo(Name.create("foo", "foo"));
    assertThat(Name.create("FOO", "foo")).isNotEqualTo(Name.create("FOO", "FOO"));

    assertThat(Name.create("a", "b").toString()).isEqualTo("a");
  }
}
