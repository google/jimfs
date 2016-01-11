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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link WatchServiceConfiguration}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class WatchServiceConfigurationTest {

  private JimfsFileSystem fs;

  @Before
  public void setUp() {
    // kind of putting the cart before the horse maybe, but it's the easiest way to get valid
    // instances of both a FileSystemView and a PathService
    fs = (JimfsFileSystem) Jimfs.newFileSystem();
  }

  @After
  public void tearDown() throws IOException {
    fs.close();
    fs = null;
  }

  @Test
  public void testPollingConfig() {
    WatchServiceConfiguration polling = WatchServiceConfiguration.polling(50, MILLISECONDS);
    WatchService watchService = polling.newWatchService(fs.getDefaultView(), fs.getPathService());
    assertThat(watchService).isInstanceOf(PollingWatchService.class);

    PollingWatchService pollingWatchService = (PollingWatchService) watchService;
    assertThat(pollingWatchService.interval).isEqualTo(50);
    assertThat(pollingWatchService.timeUnit).isEqualTo(MILLISECONDS);
  }

  @Test
  public void testDefaultConfig() {
    WatchService watchService = WatchServiceConfiguration.DEFAULT
        .newWatchService(fs.getDefaultView(), fs.getPathService());
    assertThat(watchService).isInstanceOf(PollingWatchService.class);

    PollingWatchService pollingWatchService = (PollingWatchService) watchService;
    assertThat(pollingWatchService.interval).isEqualTo(5);
    assertThat(pollingWatchService.timeUnit).isEqualTo(SECONDS);
  }
}
