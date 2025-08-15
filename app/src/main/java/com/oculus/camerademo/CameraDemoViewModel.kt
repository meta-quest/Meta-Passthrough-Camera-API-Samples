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
 *limitations under the License.
 */

package com.oculus.camerademo

import android.app.Application
import android.content.Context.CAMERA_SERVICE
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

class XrCameraDemoViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    const val IMAGE_BUFFER_SIZE = 3
  }

  private val permissionManager = PermissionManager()

  private val cameraManager: CameraManager by
      lazy(LazyThreadSafetyMode.NONE) {
        application.applicationContext.getSystemService(CAMERA_SERVICE) as CameraManager
      }

  private val cameraConfigs = ArrayList<CameraConfig>()
  private var activeConfig: CameraConfig? = null

  private var _uiState = MutableLiveData(CameraUiState())
  val uiState: LiveData<CameraUiState> = _uiState

  private var _permissionRequestState =
      MutableLiveData(
          PermissionRequestState(
              nativeCameraPermissionGranted =
                  permissionManager.checkPermissions(
                      application,
                      PermissionManager.ANDROID_CAMERA_PERMISSION,
                  ),
              vendorCameraPermissionGranted =
                  permissionManager.checkPermissions(
                      application,
                      PermissionManager.HZOS_CAMERA_PERMISSION,
                  ),
          ))
  val permissionRequestState: LiveData<PermissionRequestState> = _permissionRequestState

  private lateinit var imageReader: ImageReader
  private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
  private val imageReaderHandler = Handler(imageReaderThread.looper)

  private var camera: CameraDevice? = null
  private var cameraSession: CameraCaptureSession? = null
  private val cameraThread = HandlerThread("cameraThread").apply { start() }

  private val cameraHandler = Handler(cameraThread.looper)

  private val mainHandler = Handler(Looper.getMainLooper())

  private val cameraSessionExecutor = Executors.newSingleThreadExecutor()

  private val _eventLiveData = MutableLiveData<CameraEvent>(CameraEvent.Empty)
  val cameraEvents: LiveData<CameraEvent> = _eventLiveData

  val isCameraActive: Boolean
    get() = activeConfig != null

  var cameraWasActive: Boolean = false

  fun init() {
    cameraConfigs.clear()
    logv("Init")
    cameraManager.cameraIdList.forEach { cameraId ->
      val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
      val pixelSize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
      val position = Position.fromInt(cameraCharacteristics.get(KEY_POSITION))
      val lensRotation = cameraCharacteristics.get(CameraCharacteristics.LENS_POSE_ROTATION)
      val lensTranslation = cameraCharacteristics.get(CameraCharacteristics.LENS_POSE_TRANSLATION)
      val cameraSource = cameraCharacteristics.get(KEY_SOURCE)

      cameraConfigs.add(
          CameraConfig(
              id = cameraId,
              width = pixelSize?.width ?: 0,
              height = pixelSize?.height ?: 0,
              lensRotation = lensRotation ?: floatArrayOf(),
              lensTranslation = lensTranslation ?: floatArrayOf(),
              position = position,
              isPassthrough = cameraSource == CAMERA_SOURCE_PASSTHROUGH,
          ))
    }

    logConfigs(cameraConfigs)
  }

  fun startCamera(previewSurface: Surface) {
    if (activeConfig == null) {
      openCameraAtPosition(Position.Right, previewSurface)
    }
  }

  fun stopCamera() {
    activeConfig = null

    cameraSession?.stopRepeating()
    cameraSession = null

    camera?.close()
    camera = null
  }

  fun shutdown() {
    stopCamera()
  }

  fun onResume(surface: Surface) {
    if (cameraWasActive) {
      startCamera(surface)
      cameraWasActive = false
    }
  }

  fun onPause() {
    cameraWasActive = isCameraActive
    stopCamera()
  }

  fun resetState() {
    stopCamera()
  }

  fun onHandleCameraEvent() {
    _eventLiveData.value = CameraEvent.Empty
  }

  fun onPermissionGranted(requestResult: Map<String, Boolean>) {
    val androidPermissionGranted =
        requestResult.getOrDefault(
            PermissionManager.ANDROID_CAMERA_PERMISSION,
            _permissionRequestState.value?.nativeCameraPermissionGranted ?: false,
        )
    val vendorPermissionGranted =
        requestResult.getOrDefault(
            PermissionManager.HZOS_CAMERA_PERMISSION,
            _permissionRequestState.value?.vendorCameraPermissionGranted ?: false,
        )

    val androidPermissionStateChanged =
        (androidPermissionGranted != _permissionRequestState.value?.nativeCameraPermissionGranted)
    val vendorPermissionStateChanged =
        (vendorPermissionGranted != permissionRequestState.value?.vendorCameraPermissionGranted)

    if (androidPermissionStateChanged || vendorPermissionStateChanged) {
      _permissionRequestState.value =
          _permissionRequestState.value?.copy(
              nativeCameraPermissionGranted = androidPermissionGranted,
              vendorCameraPermissionGranted = vendorPermissionGranted,
          )
    }
  }

  private fun openCameraAtPosition(position: Position, previewSurface: Surface) {
    val permissionRequestState = _permissionRequestState.value ?: return
    if (!hasNecessaryPermissions(permissionRequestState)) {
      postEvent("Missing required permissions")
      return
    }

    if (cameraConfigs.isEmpty()) {
      logv("No Camera Configs found. Unable to open camera. Did you grant permissions?")
      return
    }

    val targetConfig = cameraConfigs.find { it.position == position }
    if (targetConfig == null) {
      loge("Invalid Camera Position. Unable to open camera.")
      return
    }

    activeConfig = targetConfig

    imageReader =
        ImageReader.newInstance(
            targetConfig.width,
            targetConfig.height,
            ImageFormat.YUV_420_888,
            IMAGE_BUFFER_SIZE,
        )
    imageReader.onLatestImage(imageReaderHandler) { image -> processImage(image) }

    cameraManager.openCamera(
        targetConfig.id,
        object : CameraDevice.StateCallback() {
          override fun onOpened(camera: CameraDevice) {
            this@XrCameraDemoViewModel.camera = camera

            try {
              camera.createCaptureSession(
                  SessionConfiguration(
                      SessionConfiguration.SESSION_REGULAR,
                      mutableListOf(
                          OutputConfiguration(imageReader.surface),
                          OutputConfiguration(previewSurface),
                      ),
                      cameraSessionExecutor,
                      object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                          onSessionStarted(session, previewSurface)
                          postEvent("Camera session started!")
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                          loge("Failed to start camera session for camera ${targetConfig.id}")
                        }
                      },
                  ))
            } catch (err: Exception) {
              loge(err.message)
            }
          }

          override fun onDisconnected(camera: CameraDevice) {
            logv("Camera ${camera.id} has been disconnected")
            resetState()
          }

          override fun onClosed(camera: CameraDevice) {
            postEvent("Camera stopped")
            super.onClosed(camera)
          }

          override fun onError(camera: CameraDevice, error: Int) {
            val msg =
                when (error) {
                  ERROR_CAMERA_DEVICE -> "Fatal (device)"
                  ERROR_CAMERA_DISABLED -> "Device policy"
                  ERROR_CAMERA_IN_USE -> "Camera in use"
                  ERROR_CAMERA_SERVICE -> "Fatal (service)"
                  ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                  else -> "Unknown"
                }
            val err = RuntimeException("Camera ${camera.id} error: $msg")
            loge(err.message!!)
            throw err
          }
        },
        cameraHandler,
    )
  }

  // TODO replace with coroutines
  private fun onSessionStarted(session: CameraCaptureSession, previewSurface: Surface) {
    val activeCamera = camera ?: return

    this.cameraSession = session
    val captureRequest =
        activeCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
          addTarget(imageReader.surface)
          addTarget(previewSurface)
        }

    session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
  }

  private fun processImage(image: Image) {
    val config = activeConfig ?: return

    val brightness = getBrigthness(image.planes[0].buffer, config.width, config.height)

    mainHandler.post {
      _uiState.value = _uiState.value?.copy(cameraBrightness = brightness.toFloat())
    }
  }

  private fun hasNecessaryPermissions(state: PermissionRequestState) =
      state.nativeCameraPermissionGranted && state.vendorCameraPermissionGranted

  private fun arrayToString(arr: FloatArray?): String = arr?.joinToString(",") ?: "[]"

  private fun logConfigs(configs: List<CameraConfig>) {
    for (config in configs) {
      logv("***** Camera ID ****** ${config.id}")
      logv(" ***** Width ****** ${config.width}")
      logv(" ***** Height ****** ${config.height}")
      logv(" ***** Lens Translation ****** ${arrayToString(config.lensTranslation)}")
      logv(" ***** Lens Rotation ****** ${arrayToString(config.lensRotation)}")
      logv(" ***** Handedness ****** ${config.position}")
      logv(" ***** is passthrough camera ****** ${config.isPassthrough}")
    }
  }

  private fun postEvent(message: String) {
    mainHandler.post { _eventLiveData.value = CameraEvent.NotificationEvent(message) }
  }
}

private fun ImageReader.onLatestImage(handler: Handler? = null, listener: (Image) -> Unit) {
  setOnImageAvailableListener(
      { reader ->
        // TODO or should it be acquireNextImage()?
        val image = reader.acquireLatestImage()
        if (image != null) {
          listener(image)
          image.close()
        }
      },
      handler,
  )
}
