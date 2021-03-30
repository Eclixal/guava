/*
 * Copyright (C) 2020 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.util.concurrent;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/** Tests for default methods of the interface. */
public class ListeningScheduledExecutorIServiceTest extends TestCase {

  private Runnable recordedCommand;
  private long recordedDelay;
  private long recordedInterval;
  private TimeUnit recordedTimeUnit;

  private final IListeningScheduledExecutorService executorService = new FakeExecutorServiceI();

  public void testScheduleRunnable() throws Exception {
    Runnable command = () -> {};

    IListenableScheduledFuture<?> future = executorService.schedule(command, Duration.ofSeconds(12));

    assertThat(future.get()).isEqualTo("schedule");
    assertThat(recordedCommand).isSameInstanceAs(command);
    assertThat(recordedTimeUnit).isEqualTo(TimeUnit.NANOSECONDS);
    assertThat(Duration.ofNanos(recordedDelay)).isEqualTo(Duration.ofSeconds(12));
  }

  public void testScheduleCallable() throws Exception {
    Callable<String> callable = () -> "hello";

    IListenableScheduledFuture<String> future =
        executorService.schedule(callable, Duration.ofMinutes(12));

    assertThat(future.get()).isEqualTo("hello");
    assertThat(recordedTimeUnit).isEqualTo(TimeUnit.NANOSECONDS);
    assertThat(Duration.ofNanos(recordedDelay)).isEqualTo(Duration.ofMinutes(12));
  }

  public void testScheduleAtFixedRate() throws Exception {
    Runnable command = () -> {};

    IListenableScheduledFuture<?> future =
        executorService.scheduleAtFixedRate(command, Duration.ofDays(2), Duration.ofHours(4));

    assertThat(future.get()).isEqualTo("scheduleAtFixedRate");
    assertThat(recordedCommand).isSameInstanceAs(command);
    assertThat(recordedTimeUnit).isEqualTo(TimeUnit.NANOSECONDS);
    assertThat(Duration.ofNanos(recordedDelay)).isEqualTo(Duration.ofDays(2));
    assertThat(Duration.ofNanos(recordedInterval)).isEqualTo(Duration.ofHours(4));
  }

  public void testScheduleWithFixedDelay() throws Exception {
    Runnable command = () -> {};

    IListenableScheduledFuture<?> future =
        executorService.scheduleWithFixedDelay(command, Duration.ofDays(8), Duration.ofHours(16));

    assertThat(future.get()).isEqualTo("scheduleWithFixedDelay");
    assertThat(recordedCommand).isSameInstanceAs(command);
    assertThat(recordedTimeUnit).isEqualTo(TimeUnit.NANOSECONDS);
    assertThat(Duration.ofNanos(recordedDelay)).isEqualTo(Duration.ofDays(8));
    assertThat(Duration.ofNanos(recordedInterval)).isEqualTo(Duration.ofHours(16));
  }

  private class FakeExecutorServiceI extends AbstractIListeningExecutorService
      implements IListeningScheduledExecutorService {
    @Override
    public IListenableScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      recordedCommand = command;
      recordedDelay = delay;
      recordedTimeUnit = unit;
      return ImmediateScheduledFutureI.of("schedule");
    }

    @Override
    public <V> IListenableScheduledFuture<V> schedule(
        Callable<V> callable, long delay, TimeUnit unit) {
      recordedDelay = delay;
      recordedTimeUnit = unit;
      try {
        return ImmediateScheduledFutureI.of(callable.call());
      } catch (Exception e) {
        return ImmediateScheduledFutureI.failed(e);
      }
    }

    @Override
    public IListenableScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      recordedCommand = command;
      recordedDelay = initialDelay;
      recordedInterval = period;
      recordedTimeUnit = unit;
      return ImmediateScheduledFutureI.of("scheduleAtFixedRate");
    }

    @Override
    public IListenableScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      recordedCommand = command;
      recordedDelay = initialDelay;
      recordedInterval = delay;
      recordedTimeUnit = unit;
      return ImmediateScheduledFutureI.of("scheduleWithFixedDelay");
    }

    @Override
    public void execute(Runnable runnable) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Runnable> shutdownNow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isTerminated() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }
  }

  private static class ImmediateScheduledFutureI<V> extends ForwardingIListenableFuture.SimpleForwardingIListenableFuture<V>
      implements IListenableScheduledFuture<V> {
    static <V> IListenableScheduledFuture<V> of(V value) {
      return new ImmediateScheduledFutureI<>(Futures.immediateFuture(value));
    }

    static <V> IListenableScheduledFuture<V> failed(Throwable t) {
      return new ImmediateScheduledFutureI<>(Futures.immediateFailedFuture(t));
    }

    ImmediateScheduledFutureI(IListenableFuture<V> delegate) {
      super(delegate);
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(Delayed other) {
      return 0;
    }
  }
}
