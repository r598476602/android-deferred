/*
 * Copyright 2013 Ray Tsang
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tuenti.deferred;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static com.tuenti.deferred.LoggerProvider.getLogger;

/**
 * @author Ray Tsang
 * @see Promise
 */
abstract class AbstractPromise<D, F, P> implements Promise<D, F, P> {
	protected volatile State state = State.PENDING;

	protected final List<DoneCallback<D>> doneCallbacks = new CopyOnWriteArrayList<>();
	protected final List<FailCallback<F>> failCallbacks = new CopyOnWriteArrayList<>();
	protected final List<ProgressCallback<P>> progressCallbacks = new CopyOnWriteArrayList<>();
	protected final List<AlwaysCallback<D, F>> alwaysCallbacks = new CopyOnWriteArrayList<>();

	private static final CallbackStrategy immediatelyStrategyCallback = new ImmediatelyStrategyCallback();
	private static final CallbackStrategy uiStrategyCallback = new UIStrategyCallback();
	private static final CallbackStrategy computationStrategyCallback = new ComputationStrategyCallback();
	private static final CallbackStrategy diskStrategyCallback = new DiskStrategyCallback();
	private static final CallbackStrategy networkStrategyCallback = new NetworkStrategyCallback();

	private final ExecutorProvider executorProvider;

	protected D resolveResult;
	protected F rejectResult;

	public AbstractPromise(ExecutorProvider executorProvider) {
		this.executorProvider = executorProvider;
	}

	@Override
	public State state() {
		return state;
	}

	@Override
	public Promise<D, F, P> done(DoneCallback<D> callback) {
		synchronized (this) {
			if (isResolved()) {
				triggerDone(callback, resolveResult);
			} else {
				doneCallbacks.add(callback);
			}
		}
		return this;
	}

	@Override
	public Promise<D, F, P> fail(FailCallback<F> callback) {
		synchronized (this) {
			if (isRejected()) {
				triggerFail(callback, rejectResult);
			} else {
				failCallbacks.add(callback);
			}
		}
		return this;
	}

	@Override
	public Promise<D, F, P> always(AlwaysCallback<D, F> callback) {
		synchronized (this) {
			if (isPending()) {
				alwaysCallbacks.add(callback);
			} else {
				triggerAlways(callback, state, resolveResult, rejectResult);
			}
		}
		return this;
	}

	protected void triggerDone(D resolved) {
		for (DoneCallback<D> callback : doneCallbacks) {
			try {
				triggerDone(callback, resolved);
			} catch (Exception e) {
				getLogger().error("an uncaught exception occured in a DoneCallback", e);
			}
		}
		doneCallbacks.clear();
	}

	protected void triggerDone(DoneCallback<D> callback, D resolved) {
		getStrategyForCallback(callback).triggerDone(this, callback, resolved);
	}

	private void notifyDoneToCallback(DoneCallback<D> callback, D resolved) {
		callback.onDone(resolved);
	}

	protected void triggerFail(F rejected) {
		for (FailCallback<F> callback : failCallbacks) {
			try {
				triggerFail(callback, rejected);
			} catch (Exception e) {
				getLogger().error("an uncaught exception occured in a FailCallback", e);
			}
		}
		failCallbacks.clear();
	}

	protected void triggerFail(FailCallback<F> callback, F rejected) {
		getStrategyForCallback(callback).triggerFail(this, callback, rejected);
	}

	private void notifyFailToCallback(FailCallback<F> callback, F rejected) {
		callback.onFail(rejected);
	}

	protected void triggerProgress(P progress) {
		for (ProgressCallback<P> callback : progressCallbacks) {
			try {
				triggerProgress(callback, progress);
			} catch (Exception e) {
				getLogger().error("an uncaught exception occured in a ProgressCallback", e);
			}
		}
	}

	protected void triggerProgress(ProgressCallback<P> callback, P progress) {
		getStrategyForCallback(callback).triggerProgress(this, callback, progress);
	}

	private void notifyProgressToCallback(ProgressCallback<P> callback, P progress) {
		callback.onProgress(progress);
	}

	protected void triggerAlways(State state, D resolve, F reject) {
		for (AlwaysCallback<D, F> callback : alwaysCallbacks) {
			try {
				triggerAlways(callback, state, resolve, reject);
			} catch (Exception e) {
				getLogger().error("an uncaught exception occured in a AlwaysCallback", e);
			}
		}
		alwaysCallbacks.clear();
	}

	protected void triggerAlways(AlwaysCallback<D, F> callback, State state, D resolve, F reject) {
		getStrategyForCallback(callback).triggerAlways(this, callback, state, resolve, reject);
	}

	private void notifyAlwaysToCallback(AlwaysCallback<D, F> callback, State state, D resolve, F reject) {
		callback.onAlways(state, resolve, reject);
	}

	@Override
	public Promise<D, F, P> progress(ProgressCallback<P> callback) {
		progressCallbacks.add(callback);
		return this;
	}

	@Override
	public Promise<D, F, P> then(DoneCallback<D> callback) {
		return done(callback);
	}

	@Override
	public Promise<D, F, P> then(DoneCallback<D> doneCallback, FailCallback<F> failCallback) {
		done(doneCallback);
		fail(failCallback);
		return this;
	}

	@Override
	public Promise<D, F, P> then(DoneCallback<D> doneCallback, FailCallback<F> failCallback,
			ProgressCallback<P> progressCallback) {
		done(doneCallback);
		fail(failCallback);
		progress(progressCallback);
		return this;
	}

	@Override
	public <D_OUT> Promise<D_OUT, F, P> then(DoneFilter<D, D_OUT> doneFilter) {
		return new FilteredPromise<>(executorProvider, this, doneFilter, null, null);
	}

	@Override
	public <F_OUT> Promise<D, F_OUT, P> then(FailFilter<F, F_OUT> failFilter) {
		return new FilteredPromise<>(executorProvider, this, null, failFilter, null);
	}

	@Override
	public <P_OUT> Promise<D, F, P_OUT> then(ProgressFilter<P, P_OUT> progressFilter) {
		return new FilteredPromise<>(executorProvider, this, null, null, progressFilter);
	}

	@Override
	public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(
			DoneFilter<D, D_OUT> doneFilter, FailFilter<F, F_OUT> failFilter) {
		return new FilteredPromise<>(executorProvider, this, doneFilter, failFilter, null);
	}

	@Override
	public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(
			DoneFilter<D, D_OUT> doneFilter, FailFilter<F, F_OUT> failFilter,
			ProgressFilter<P, P_OUT> progressFilter) {
		return new FilteredPromise<>(executorProvider, this, doneFilter, failFilter, progressFilter);
	}

	@Override
	public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(
			DonePipe<D, D_OUT, F_OUT, P_OUT> doneFilter) {
		return new PipedPromise<>(executorProvider, this, doneFilter, null, null);
	}

	@Override
	public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(
			FailPipe<F, D_OUT, F_OUT, P_OUT> failPipe) {
		return new PipedPromise<>(executorProvider, this, null, failPipe, null);
	}

	@Override
	public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(
			ProgressPipe<P, D_OUT, F_OUT, P_OUT> progressPipe) {
		return new PipedPromise<>(executorProvider, this, null, null, progressPipe);
	}

	@Override
	public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(
			DonePipe<D, D_OUT, F_OUT, P_OUT> doneFilter,
			FailPipe<F, D_OUT, F_OUT, P_OUT> failFilter) {
		return new PipedPromise<>(executorProvider, this, doneFilter, failFilter, null);
	}

	@Override
	public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(
			DonePipe<D, D_OUT, F_OUT, P_OUT> doneFilter,
			FailPipe<F, D_OUT, F_OUT, P_OUT> failFilter,
			ProgressPipe<P, D_OUT, F_OUT, P_OUT> progressFilter) {
		return new PipedPromise<>(executorProvider, this, doneFilter, failFilter, progressFilter);
	}

	@Override
	public boolean isPending() {
		return state == State.PENDING;
	}

	@Override
	public boolean isResolved() {
		return state == State.RESOLVED;
	}

	@Override
	public boolean isRejected() {
		return state == State.REJECTED;
	}

	public void waitSafely() throws InterruptedException {
		waitSafely(-1);
	}

	public void waitSafely(long timeout) throws InterruptedException {
		final long startTime = System.currentTimeMillis();
		synchronized (this) {
			while (this.isPending()) {
				try {
					if (timeout <= 0) {
						wait();
					} else {
						final long elapsed = System.currentTimeMillis() - startTime;
						final long waitTime = timeout - elapsed;
						wait(waitTime);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw e;
				}

				final long elapsed = System.currentTimeMillis() - startTime;
				if (timeout > 0 && elapsed >= timeout) {
					return;
				}
			}
		}
	}

	@Override
	public void ignoreCallbacks() {
		synchronized (this) {
			clearCallbacks();
		}
	}

	private void clearCallbacks() {
		doneCallbacks.clear();
		failCallbacks.clear();
		progressCallbacks.clear();
		alwaysCallbacks.clear();
	}


	private CallbackStrategy getStrategyForCallback(Object callback) {
		CallbackStrategy result;

		if (callback instanceof UICallback) {
			result = uiStrategyCallback;
		} else if (callback instanceof ComputationCallback) {
			result = computationStrategyCallback;
		} else if (callback instanceof DiskCallback) {
			result = diskStrategyCallback;
		} else if (callback instanceof NetworkCallback) {
			result = networkStrategyCallback;
		} else {
			result = immediatelyStrategyCallback;
		}

		return result;
	}
	protected void triggerDoneOnExecutor(final DoneCallback<D> callback, final D resolved, Executor executor) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				notifyDoneToCallback(callback, resolved);
			}
		});
	}

	protected void triggerFailOnExecutor(final FailCallback<F> callback, final F rejected, Executor executor) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				notifyFailToCallback(callback, rejected);
			}
		});
	}

	protected void triggerAlwaysOnExecutor(final AlwaysCallback<D, F> callback, final State state, final D resolve, final F
			reject, Executor executor) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				notifyAlwaysToCallback(callback, state, resolve, reject);
			}
		});
	}

	protected void triggerProgressOnExecutor(final ProgressCallback<P> callback, final P progress, Executor executor) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				notifyProgressToCallback(callback, progress);
			}
		});
	}

	protected void triggerDoneImmediately(DoneCallback<D> doneCallback, D resolved) {
		notifyDoneToCallback(doneCallback, resolved);
	}

	protected void triggerFailImmediately(FailCallback<F> failCallback, F reject) {
		notifyFailToCallback(failCallback, reject);
	}

	protected void triggerAlwaysImmediately(AlwaysCallback<D, F> callback, State state, D resolve, F reject) {
		notifyAlwaysToCallback(callback, state, resolve, reject);
	}

	protected void triggerProgressImmediately(ProgressCallback<P> callback, P progress) {
		notifyProgressToCallback(callback, progress);
	}

	protected ExecutorProvider getExecutorProvider() {
		return executorProvider;
	}

}
