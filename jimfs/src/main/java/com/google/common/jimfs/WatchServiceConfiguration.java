/*
 * Copyright 2016 Google Inc.
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
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for the {@link WatchService} implementation used by a file system.
 *
 * @author Colin Decker
 * @since 1.1
 */
public abstract class WatchServiceConfiguration {

  /**
   * The default configuration that's used if the user doesn't provide anything more specific.
   */
  static final WatchServiceConfiguration DEFAULT = polling(5, SECONDS);

  /**
   * Returns a configuration for a {@link WatchService} that polls watched directories for changes
   * every {@code interval} of the given {@code timeUnit} (e.g. every 5
   * {@link TimeUnit#SECONDS seconds}).
   */
  public static WatchServiceConfiguration polling(long interval, TimeUnit timeUnit) {
    return new PollingConfig(interval, timeUnit);
  }

  WatchServiceConfiguration() {}

  /**
   * Creates a new {@link AbstractWatchService} implementation.
   */
  // return type and parameters of this method subject to change if needed for any future
  // implementations
  abstract AbstractWatchService newWatchService(FileSystemView view, PathService pathService);

  /**
   * Implementation for {@link #polling}.
   */
  private static final class PollingConfig extends WatchServiceConfiguration {

    private final long interval;
    private final TimeUnit timeUnit;

    private PollingConfig(long interval, TimeUnit timeUnit) {
      checkArgument(interval > 0, "interval (%s) must be positive", interval);
      this.interval = interval;
      this.timeUnit = checkNotNull(timeUnit);
    }

    @Override
    AbstractWatchService newWatchService(FileSystemView view, PathService pathService) {
      return new PollingWatchService(view, pathService, view.state(), interval, timeUnit);
    }

    @Override
    public String toString() {
      return "WatchServiceConfiguration.polling(" + interval + ", " + timeUnit + ")";
    }
  }
}
