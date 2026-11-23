package com.zirakusa.camara

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.Lens
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Quality
import com.zirakusa.camara.data.Ratio
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.ui.CamaraState
import java.time.LocalDateTime
import androidx.camera.video.Quality as VideoQuality

/**
 * The real camera, with CameraX's controller (it also gives pinch to zoom and tap to focus on the preview). Photos and
 * videos go to the gallery's DCIM/Camera, like the phone's own camera; nothing goes anywhere else.
 */
class Camera(private val activity: ComponentActivity, private val state: CamaraState) {
    val controller = LifecycleCameraController(activity)
    private val main = ContextCompat.getMainExecutor(activity)
    private val sound = MediaActionSound()
    private var recording: Recording? = null

    init {
        controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
        controller.zoomState.observe(activity) { z -> state.zoomMin = z.minZoomRatio; state.zoomMax = z.maxZoomRatio; state.zoom = z.zoomRatio }
        controller.bindToLifecycle(activity)
        state.shoot = { if (it == Mode.PHOTO) photo() else startVideo() }
        state.stop = { recording?.stop(); recording = null }
    }

    fun lens(front: Boolean) { controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA }
    fun zoom(ratio: Float) { if (kotlin.math.abs((controller.zoomState.value?.zoomRatio ?: 1f) - ratio) > 0.01f) controller.setZoomRatio(ratio) }
    fun flash(f: Flash) { controller.imageCaptureFlashMode = when (f) { Flash.OFF -> ImageCapture.FLASH_MODE_OFF; Flash.AUTO -> ImageCapture.FLASH_MODE_AUTO; Flash.ON -> ImageCapture.FLASH_MODE_ON } }
    fun ratio(r: Ratio) {
        val sel = ResolutionSelector.Builder().setAspectRatioStrategy(if (r == Ratio.R16_9) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
        controller.imageCaptureResolutionSelector = sel; controller.previewResolutionSelector = sel
    }
    fun quality(q: Quality) {
        val want = when (q) { Quality.HD -> VideoQuality.HD; Quality.FHD -> VideoQuality.FHD; Quality.UHD -> VideoQuality.UHD }
        controller.videoCaptureQualitySelector = QualitySelector.from(want, FallbackStrategy.lowerQualityOrHigherThan(VideoQuality.SD))
    }

    private fun values(mode: Mode, name: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (mode == Mode.PHOTO) "image/jpeg" else "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
    }

    private fun photo() {
        val name = Lens.fileName(Mode.PHOTO, LocalDateTime.now())
        val out = ImageCapture.OutputFileOptions.Builder(activity.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values(Mode.PHOTO, name)).build()
        if (state.sound) sound.play(MediaActionSound.SHUTTER_CLICK)
        state.flashBlink = true
        controller.takePicture(out, main, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                state.flashBlink = false
                r.savedUri?.let { state.saved(Shot(0, it.toString(), name, Mode.PHOTO, System.currentTimeMillis())) }
            }
            override fun onError(e: ImageCaptureException) { state.flashBlink = false; state.failed() }
        })
    }

    @SuppressLint("MissingPermission") // sound is recorded only when the microphone permission was given (checked here)
    private fun startVideo() {
        val name = Lens.fileName(Mode.VIDEO, LocalDateTime.now())
        val out = MediaStoreOutputOptions.Builder(activity.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values(Mode.VIDEO, name)).build()
        val mic = state.audio && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (state.sound) sound.play(MediaActionSound.START_VIDEO_RECORDING)
        recording = controller.startRecording(out, AudioConfig.create(mic), main) { e ->
            when (e) {
                is VideoRecordEvent.Start -> state.started()
                is VideoRecordEvent.Status -> state.recorded = e.recordingStats.recordedDurationNanos / 1_000_000_000
                is VideoRecordEvent.Finalize -> {
                    if (state.sound) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    if (!e.hasError() || e.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE)
                        state.saved(Shot(0, e.outputResults.outputUri.toString(), name, Mode.VIDEO, System.currentTimeMillis(), e.recordingStats.recordedDurationNanos / 1_000_000_000))
                    else state.failed()
                }
            }
        }
    }

    fun release() { recording?.stop(); sound.release() }
}
