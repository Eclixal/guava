/*
 * Copyright (C) 2006 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.util.concurrent;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Futures.getDone;
import static com.google.common.util.concurrent.MoreExecutors.rejectionPropagatingExecutor;
import static com.google.common.util.concurrent.Platform.isInstanceOfThrowableClass;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Function;
import com.google.common.util.concurrent.internal.InternalFutureFailureAccess;
import com.google.common.util.concurrent.internal.InternalFutures;
import com.google.errorprone.annotations.ForOverride;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Implementations of {@code Futures.catching*}. */
@GwtCompatible
abstract class AbstractCatchingFutureI<V, X extends Throwable, F, T>
    extends FluentFuture.TrustedFutureI<V> implements Runnable {
  static <V, X extends Throwable> IListenableFuture<V> create(
      IListenableFuture<? extends V> input,
      Class<X> exceptionType,
      Function<? super X, ? extends V> fallback,
      Executor executor) {
    CatchingFutureI<V, X> future = new CatchingFutureI<>(input, exceptionType, fallback);
    input.addListener(future, rejectionPropagatingExecutor(executor, future));
    return future;
  }

  static <X extends Throwable, V> IListenableFuture<V> create(
      IListenableFuture<? extends V> input,
      Class<X> exceptionType,
      IAsyncFunction<? super X, ? extends V> fallback,
      Executor executor) {
    AsyncCatchingFutureI<V, X> future = new AsyncCatchingFutureI<>(input, exceptionType, fallback);
    input.addListener(future, rejectionPropagatingExecutor(executor, future));
    return future;
  }

  /*
   * In certain circumstances, this field might theoretically not be visible to an afterDone() call
   * triggered by cancel(). For details, see the comments on the fields of TimeoutFuture.
   */
  @Nullable IListenableFuture<? extends V> inputFuture;
  @Nullable Class<X> exceptionType;
  @Nullable F fallback;

  AbstractCatchingFutureI(
          IListenableFuture<? extends V> inputFuture, Class<X> exceptionType, F fallback) {
    this.inputFuture = checkNotNull(inputFuture);
    this.exceptionType = checkNotNull(exceptionType);
    this.fallback = checkNotNull(fallback);
  }

  @Override
  public final void run() {
    IListenableFuture<? extends V> localInputFuture = inputFuture;
    Class<X> localExceptionType = exceptionType;
    F localFallback = fallback;
    if (localInputFuture == null | localExceptionType == null | localFallback == null
        // This check, unlike all the others, is a volatile read
        || isCancelled()) {
      return;
    }
    inputFuture = null;

    // For an explanation of the cases here, see the comments on AbstractTransformFuture.run.
    V sourceResult = null;
    Throwable throwable = null;
    try {
      if (localInputFuture instanceof InternalFutureFailureAccess) {
        throwable =
            InternalFutures.tryInternalFastPathGetFailure(
                (InternalFutureFailureAccess) localInputFuture);
      }
      if (throwable == null) {
        sourceResult = getDone(localInputFuture);
      }
    } catch (ExecutionException e) {
      throwable = e.getCause();
      if (throwable == null) {
        throwable =
            new NullPointerException(
                "Future type "
                    + localInputFuture.getClass()
                    + " threw "
                    + e.getClass()
                    + " without a cause");
      }
    } catch (Throwable e) { // this includes cancellation exception
      throwable = e;
    }

    if (throwable == null) {
      set(sourceResult);
      return;
    }

    if (!isInstanceOfThrowableClass(throwable, localExceptionType)) {
      setFuture(localInputFuture);
      // TODO(cpovirk): Test that fallback is not run in this case.
      return;
    }

    @SuppressWarnings("unchecked") // verified safe by isInstanceOfThrowableClass
    X castThrowable = (X) throwable;
    T fallbackResult;
    try {
      fallbackResult = doFallback(localFallback, castThrowable);
    } catch (Throwable t) {
      setException(t);
      return;
    } finally {
      exceptionType = null;
      fallback = null;
    }

    setResult(fallbackResult);
  }

  @Override
  protected String pendingToString() {
    IListenableFuture<? extends V> localInputFuture = inputFuture;
    Class<X> localExceptionType = exceptionType;
    F localFallback = fallback;
    String superString = super.pendingToString();
    String resultString = "";
    if (localInputFuture != null) {
      resultString = "inputFuture=[" + localInputFuture + "], ";
    }
    if (localExceptionType != null && localFallback != null) {
      return resultString
          + "exceptionType=["
          + localExceptionType
          + "], fallback=["
          + localFallback
          + "]";
    } else if (superString != null) {
      return resultString + superString;
    }
    return null;
  }

  /** Template method for subtypes to actually run the fallback. */
  @ForOverride
  abstract @Nullable T doFallback(F fallback, X throwable) throws Exception;

  /** Template method for subtypes to actually set the result. */
  @ForOverride
  abstract void setResult(@Nullable T result);

  @Override
  protected final void afterDone() {
    maybePropagateCancellationTo(inputFuture);
    this.inputFuture = null;
    this.exceptionType = null;
    this.fallback = null;
  }

  /**
   * An {@link AbstractCatchingFutureI} that delegates to an {@link IAsyncFunction} and {@link
   * #setFuture(IListenableFuture)}.
   */
  private static final class AsyncCatchingFutureI<V, X extends Throwable>
      extends AbstractCatchingFutureI<
                V, X, IAsyncFunction<? super X, ? extends V>, IListenableFuture<? extends V>> {
    AsyncCatchingFutureI(
        IListenableFuture<? extends V> input,
        Class<X> exceptionType,
        IAsyncFunction<? super X, ? extends V> fallback) {
      super(input, exceptionType, fallback);
    }

    @Override
    IListenableFuture<? extends V> doFallback(
            IAsyncFunction<? super X, ? extends V> fallback, X cause) throws Exception {
      IListenableFuture<? extends V> replacement = fallback.apply(cause);
      checkNotNull(
          replacement,
          "AsyncFunction.apply returned null instead of a Future. "
              + "Did you mean to return immediateFuture(null)? %s",
          fallback);
      return replacement;
    }

    @Override
    void setResult(IListenableFuture<? extends V> result) {
      setFuture(result);
    }
  }

  /**
   * An {@link AbstractCatchingFutureI} that delegates to a {@link Function} and {@link
   * #set(Object)}.
   */
  private static final class CatchingFutureI<V, X extends Throwable>
      extends AbstractCatchingFutureI<V, X, Function<? super X, ? extends V>, V> {
    CatchingFutureI(
        IListenableFuture<? extends V> input,
        Class<X> exceptionType,
        Function<? super X, ? extends V> fallback) {
      super(input, exceptionType, fallback);
    }

    @Override
    @Nullable
    V doFallback(Function<? super X, ? extends V> fallback, X cause) throws Exception {
      return fallback.apply(cause);
    }

    @Override
    void setResult(@Nullable V result) {
      set(result);
    }
  }
}
