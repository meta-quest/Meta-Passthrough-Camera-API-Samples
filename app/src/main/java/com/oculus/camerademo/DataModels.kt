/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
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

package com.oculus.camerademo

enum class Position {
  Left,
  Right,
  Unknown;

  companion object {
    fun fromInt(value: Int?): Position =
        when (value) {
          0 -> Left
          1 -> Right
          else -> Unknown
        }
  }
}

data class CameraConfig(
    val id: String,
    val width: Int,
    val height: Int,
    val lensTranslation: FloatArray,
    val lensRotation: FloatArray,
    val isPassthrough: Boolean,
    val position: Position,
)

data class CameraUiState(
    val cameraBrightness: Float = 0.0f,
)

data class PermissionRequestState(
    val nativeCameraPermissionGranted: Boolean = false,
    val vendorCameraPermissionGranted: Boolean = false,
)

sealed class CameraEvent() {
  data object Empty : CameraEvent()

  data class NotificationEvent(val message: String) : CameraEvent()
}
