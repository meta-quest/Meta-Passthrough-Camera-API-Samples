/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oculus.camerademo

import android.util.Log

private const val LOG_TAG = "com.oculus.camerademo"

fun logv(msg: String?) {
  if (msg != null) {
    Log.v(LOG_TAG, msg)
  }
}

fun loge(msg: String?) {
  if (msg != null) {
    Log.e(LOG_TAG, msg)
  }
}
