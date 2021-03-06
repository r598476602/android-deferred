/*
 * Copyright (c) Tuenti Technologies S.L. All rights reserved.
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

package com.tuenti.deferred;

class ImmediatelyFilterStrategy implements FilterStrategy {

	@Override
	public <D, F, P, D_OUT, F_OUT, P_OUT> void resolve(FilteredPromise<D, F, P, D_OUT, F_OUT, P_OUT> filteredPromise, D result) {
		filteredPromise.resolveImmediately(result);
	}

	@Override
	public <D, F, P, D_OUT, F_OUT, P_OUT> void reject(FilteredPromise<D, F, P, D_OUT, F_OUT, P_OUT> filteredPromise, F result) {
		filteredPromise.rejectImmediately(result);
	}

	@Override
	public <D, F, P, D_OUT, F_OUT, P_OUT> void notify(FilteredPromise<D, F, P, D_OUT, F_OUT, P_OUT> filteredPromise, P progress) {
		filteredPromise.notifyImmediately(progress);
	}
}
