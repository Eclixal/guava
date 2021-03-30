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
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import junit.framework.TestCase;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.mockito.Mockito;

/**
 * Test for {@link IFutureCallback}.
 *
 * @author Anthony Zana
 */
@GwtCompatible(emulated = true)
public class IFutureCallbackTest extends TestCase {
  public void testSameThreadSuccess() {
    SettableFutureI<String> f = SettableFutureI.create();
    MockCallbackI callback = new MockCallbackI("foo");
    addCallback(f, callback, directExecutor());
    f.set("foo");
  }

  public void testExecutorSuccess() {
    CountingSameThreadExecutor ex = new CountingSameThreadExecutor();
    SettableFutureI<String> f = SettableFutureI.create();
    MockCallbackI callback = new MockCallbackI("foo");
    Futures.addCallback(f, callback, ex);
    f.set("foo");
    assertEquals(1, ex.runCount);
  }

  // Error cases
  public void testSameThreadExecutionException() {
    SettableFutureI<String> f = SettableFutureI.create();
    Exception e = new IllegalArgumentException("foo not found");
    MockCallbackI callback = new MockCallbackI(e);
    addCallback(f, callback, directExecutor());
    f.setException(e);
  }

  public void testCancel() {
    SettableFutureI<String> f = SettableFutureI.create();
    IFutureCallback<String> callback =
        new IFutureCallback<String>() {
          private boolean called = false;

          @Override
          public void onSuccess(String result) {
            fail("Was not expecting onSuccess() to be called.");
          }

          @Override
          public synchronized void onFailure(Throwable t) {
            assertFalse(called);
            assertThat(t).isInstanceOf(CancellationException.class);
            called = true;
          }
        };
    addCallback(f, callback, directExecutor());
    f.cancel(true);
  }

  public void testThrowErrorFromGet() {
    Error error = new AssertionError("ASSERT!");
    IListenableFuture<String> f = UncheckedThrowingFutureI.throwingError(error);
    MockCallbackI callback = new MockCallbackI(error);
    addCallback(f, callback, directExecutor());
  }

  public void testRuntimeExeceptionFromGet() {
    RuntimeException e = new IllegalArgumentException("foo not found");
    IListenableFuture<String> f = UncheckedThrowingFutureI.throwingRuntimeException(e);
    MockCallbackI callback = new MockCallbackI(e);
    addCallback(f, callback, directExecutor());
  }

  @GwtIncompatible // Mockito
  public void testOnSuccessThrowsRuntimeException() throws Exception {
    RuntimeException exception = new RuntimeException();
    String result = "result";
    SettableFutureI<String> future = SettableFutureI.create();
    @SuppressWarnings("unchecked") // Safe for a mock
    IFutureCallback<String> callback = Mockito.mock(IFutureCallback.class);
    addCallback(future, callback, directExecutor());
    Mockito.doThrow(exception).when(callback).onSuccess(result);
    future.set(result);
    assertEquals(result, future.get());
    Mockito.verify(callback).onSuccess(result);
    Mockito.verifyNoMoreInteractions(callback);
  }

  @GwtIncompatible // Mockito
  public void testOnSuccessThrowsError() throws Exception {
    class TestError extends Error {}
    TestError error = new TestError();
    String result = "result";
    SettableFutureI<String> future = SettableFutureI.create();
    @SuppressWarnings("unchecked") // Safe for a mock
    IFutureCallback<String> callback = Mockito.mock(IFutureCallback.class);
    addCallback(future, callback, directExecutor());
    Mockito.doThrow(error).when(callback).onSuccess(result);
    try {
      future.set(result);
      fail("Should have thrown");
    } catch (TestError e) {
      assertSame(error, e);
    }
    assertEquals(result, future.get());
    Mockito.verify(callback).onSuccess(result);
    Mockito.verifyNoMoreInteractions(callback);
  }

  public void testWildcardFuture() {
    SettableFutureI<String> settable = SettableFutureI.create();
    IListenableFuture<?> f = settable;
    IFutureCallback<Object> callback =
        new IFutureCallback<Object>() {
          @Override
          public void onSuccess(Object result) {}

          @Override
          public void onFailure(Throwable t) {}
        };
    addCallback(f, callback, directExecutor());
  }

  private class CountingSameThreadExecutor implements Executor {
    int runCount = 0;

    @Override
    public void execute(Runnable command) {
      command.run();
      runCount++;
    }
  }

  private final class MockCallbackI implements IFutureCallback<String> {
    @Nullable private String value = null;
    @Nullable private Throwable failure = null;
    private boolean wasCalled = false;

    MockCallbackI(String expectedValue) {
      this.value = expectedValue;
    }

    public MockCallbackI(Throwable expectedFailure) {
      this.failure = expectedFailure;
    }

    @Override
    public synchronized void onSuccess(String result) {
      assertFalse(wasCalled);
      wasCalled = true;
      assertEquals(value, result);
    }

    @Override
    public synchronized void onFailure(Throwable t) {
      assertFalse(wasCalled);
      wasCalled = true;
      assertEquals(failure, t);
    }
  }
}
