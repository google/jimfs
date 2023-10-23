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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/** Fake implementation of {@link FileTimeSource}. */
final class FakeFileTimeSource implements FileTimeSource {

  private final Random random = new Random(System.currentTimeMillis());
  private FileTime now;

  FakeFileTimeSource() {
    randomize();
  }

  @CanIgnoreReturnValue
  FakeFileTimeSource randomize() {
    Instant randomNow =
        Instant.ofEpochSecond(
            random
                .longs(Instant.MIN.getEpochSecond(), Instant.MAX.getEpochSecond())
                .findAny()
                .getAsLong(),
            random.nextInt(1_000_000_000));
    this.now = FileTime.from(randomNow);
    return this;
  }

  @CanIgnoreReturnValue
  FakeFileTimeSource advance(Duration duration) {
    this.now = FileTime.from(now.toInstant().plus(duration));
    return this;
  }

  @Override
  public FileTime now() {
    return now;
  }
}
