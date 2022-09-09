/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.math.LongMath;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.attribute.FileTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Fake implementation of {@link FileTimeSource}. */
final class FakeFileTimeSource implements FileTimeSource {

  private final Random random = new Random(System.currentTimeMillis());
  private FileTime now;

  FakeFileTimeSource() {
    randomize();
  }

  @CanIgnoreReturnValue
  FakeFileTimeSource setNow(FileTime now) {
    this.now = checkNotNull(now);
    return this;
  }

  @CanIgnoreReturnValue
  private FakeFileTimeSource setNowMillis(long millis) {
    return setNow(FileTime.fromMillis(millis));
  }

  @CanIgnoreReturnValue
  FakeFileTimeSource randomize() {
    // Use a random int rather than long as an easy way of ensuring we don't get something near
    // Long.MAX_VALUE
    return setNowMillis(random.nextInt());
  }

  @CanIgnoreReturnValue
  FakeFileTimeSource advance(long duration, TimeUnit unit) {
    return setNowMillis(LongMath.checkedAdd(now.toMillis(), unit.toMillis(duration)));
  }

  @Override
  public FileTime now() {
    return now;
  }
}
