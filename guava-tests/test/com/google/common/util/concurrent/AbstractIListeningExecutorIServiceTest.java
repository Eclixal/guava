/*
 * Copyright (C) 2011 The Guava Authors
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

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 * Tests for {@link AbstractIListeningExecutorService}.
 *
 * @author Colin Decker
 */
public class AbstractIListeningExecutorIServiceTest extends TestCase {

  public void testSubmit() throws Exception {
    /*
     * Mostly just tests that TrustedListenableFutureTask are created and run; tests for
     * TrustedListenableFutureTask should ensure that listeners are called correctly.
     */

    TestIListeningExecutorService e = new TestIListeningExecutorService();

    TestRunnable runnable = new TestRunnable();
    IListenableFuture<?> runnableFuture = e.submit(runnable);
    assertThat(runnableFuture).isInstanceOf(TrustedIListenableFutureTask.class);
    assertTrue(runnableFuture.isDone());
    assertTrue(runnable.run);

    IListenableFuture<String> callableFuture = e.submit(new TestCallable());
    assertThat(callableFuture).isInstanceOf(TrustedIListenableFutureTask.class);
    assertTrue(callableFuture.isDone());
    assertEquals("foo", callableFuture.get());

    TestRunnable runnable2 = new TestRunnable();
    IListenableFuture<Integer> runnableFuture2 = e.submit(runnable2, 3);
    assertThat(runnableFuture2).isInstanceOf(TrustedIListenableFutureTask.class);
    assertTrue(runnableFuture2.isDone());
    assertTrue(runnable2.run);
    assertEquals((Integer) 3, runnableFuture2.get());
  }

  private static class TestRunnable implements Runnable {
    boolean run = false;

    @Override
    public void run() {
      run = true;
    }
  }

  private static class TestCallable implements Callable<String> {
    @Override
    public String call() {
      return "foo";
    }
  }

  /** Simple same thread listening executor service that doesn't handle shutdown. */
  private static class TestIListeningExecutorService extends AbstractIListeningExecutorService {

    @Override
    public void execute(Runnable runnable) {
      assertThat(runnable).isInstanceOf(TrustedIListenableFutureTask.class);
      runnable.run();
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return ImmutableList.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return false;
    }
  }
}
