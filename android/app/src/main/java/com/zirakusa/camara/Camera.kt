package com.zirakusa.camara

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaActionSound
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import com.zirakusa.camara.data.Balance
import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.Lens
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Pro
import com.zirakusa.camara.data.Quality
import com.zirakusa.camara.data.Ratio
import com.zirakusa.camara.data.Scene
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.ui.CamaraState
import java.time.LocalDateTime
import java.util.concurrent.Executors
import androidx.camera.video.Quality as VideoQuality

/**
 * The real camera, with CameraX's controller (it also gives pinch to zoom and tap to focus on the preview). Retrato, Noche
 * and HDR use the phone maker's processing (CameraX Extensions) where the phone offers it; Pro and the video stabilizer go
 * through Camera2. Photos and videos go to the gallery's DCIM/Camera, like the phone's own camera; nothing goes anywhere else.
 */
class Camera(private val activity: ComponentActivity, private val state: CamaraState) {
    val controller = LifecycleCameraController(activity)
    private val main = ContextCompat.getMainExecutor(activity)
    private val work = Executors.newSingleThreadExecutor()
    private val sound = MediaActionSound()
    private var recording: Recording? = null
    private var extensions: ExtensionsManager? = null
    private var boundControl: CameraControl? = null

    init {
        controller.zoomState.observe(activity) { z ->
            state.zoomMin = z.minZoomRatio; state.zoomMax = z.maxZoomRatio; state.zoom = z.zoomRatio
            // A new camera was bound (lens, mode): its exposure range, and Pro and the stabilizer again.
            val control = controller.cameraControl
            if (control != null && control !== boundControl) { boundControl = control; readExposure(); tune() }
        }
        controller.bindToLifecycle(activity)
        state.shoot = { if (it == Mode.PHOTO) photo() else startVideo() }
        state.stop = { recording?.stop(); recording = null }
        val provider = ProcessCameraProvider.getInstance(activity)
        provider.addListener({
            val manager = runCatching { ExtensionsManager.getInstanceAsync(activity, provider.get()) }.getOrNull() ?: return@addListener
            manager.addListener({ extensions = runCatching { manager.get() }.getOrNull(); available(); bind() }, main)
        }, main)
        bind()
    }

    private fun base() = if (state.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    /** Which of Retrato, Noche and HDR this lens does with the maker's processing. */
    private fun available() {
        val m = extensions ?: return
        fun has(mode: Int) = runCatching { m.isExtensionAvailable(base(), mode) }.getOrDefault(false)
        state.extensions = buildSet { if (has(ExtensionMode.BOKEH)) add(Scene.PORTRAIT); if (has(ExtensionMode.NIGHT)) add(Scene.NIGHT) }
        state.hdrAvailable = has(ExtensionMode.HDR)
    }

    private fun extensionFor(): Int? = when {
        state.scene == Scene.PORTRAIT && Scene.PORTRAIT in state.extensions -> ExtensionMode.BOKEH
        state.scene == Scene.NIGHT && Scene.NIGHT in state.extensions -> ExtensionMode.NIGHT
        state.scene == Scene.PHOTO && state.hdr && state.hdrAvailable -> ExtensionMode.HDR
        else -> null
    }

    /** Binds the right camera for the mode: video or photo, with or without the maker's processing. */
    fun bind() {
        if (recording != null) return
        available()
        val ext = extensionFor()
        val video = state.mode == Mode.VIDEO
        controller.setEnabledUseCases(if (video) CameraController.VIDEO_CAPTURE else CameraController.IMAGE_CAPTURE)
        controller.imageCaptureMode = if (state.scene == Scene.PHOTO) ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        val selector = ext?.let { e -> extensions?.let { m -> runCatching { m.getExtensionEnabledCameraSelector(base(), e) }.getOrNull() } } ?: base()
        if (controller.cameraSelector != selector) controller.cameraSelector = selector
        resolution()
        flash(); tune()
    }

    fun zoom(ratio: Float) { if (kotlin.math.abs((controller.zoomState.value?.zoomRatio ?: 1f) - ratio) > 0.01f) controller.setZoomRatio(ratio) }

    fun flash() {
        val f = state.flashNow
        controller.imageCaptureFlashMode = when (f) { Flash.AUTO -> ImageCapture.FLASH_MODE_AUTO; Flash.ON -> ImageCapture.FLASH_MODE_ON; else -> ImageCapture.FLASH_MODE_OFF }
        controller.enableTorch(f == Flash.TORCH)
    }

    /** The photo's shape; a photo that is changed afterwards is kept near 12 MP so the phone has memory for it. */
    fun resolution() {
        val aspect = if (state.ratio == Ratio.R16_9) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val sel = ResolutionSelector.Builder().setAspectRatioStrategy(aspect).apply {
            if (state.processed) setResolutionStrategy(ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
        }.build()
        controller.imageCaptureResolutionSelector = sel
        controller.previewResolutionSelector = ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build()
    }

    fun quality(q: Quality) {
        val want = when (q) { Quality.HD -> VideoQuality.HD; Quality.FHD -> VideoQuality.FHD; Quality.UHD -> VideoQuality.UHD }
        controller.videoCaptureQualitySelector = QualitySelector.from(want, FallbackStrategy.lowerQualityOrHigherThan(VideoQuality.SD))
    }

    private fun readExposure() {
        val exposure = controller.cameraInfo?.exposureState ?: return
        if (!exposure.isExposureCompensationSupported) { state.evMax = 0; return }
        state.evStep = exposure.exposureCompensationStep.toFloat()
        state.evMax = minOf(exposure.exposureCompensationRange.upper, -exposure.exposureCompensationRange.lower, Pro.evs.last())
    }

    /** Pro (exposure, ISO, shutter, white balance) and the video stabilizer, on the camera that is bound now. */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun tune() {
        val control = controller.cameraControl ?: return
        val pro = state.scene == Scene.PRO
        runCatching { control.setExposureCompensationIndex(if (pro) state.ev.coerceIn(-state.evMax, state.evMax) else 0) }
        if (extensionFor() != null) return // the maker's processing does not take Camera2 settings
        val o = CaptureRequestOptions.Builder()
        if (state.mode == Mode.VIDEO && state.stabilization)
            o.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        if (pro) {
            o.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, when (state.balance) {
                Balance.AUTO -> CaptureRequest.CONTROL_AWB_MODE_AUTO; Balance.DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                Balance.CLOUDY -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT; Balance.INCANDESCENT -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                Balance.FLUORESCENT -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
            })
            Pro.manual(state.iso, state.shutter)?.let { (iso, ns) ->
                o.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                o.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                o.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, ns)
            }
        }
        runCatching { Camera2CameraControl.from(control).setCaptureRequestOptions(o.build()) }
    }

    private fun values(mode: Mode, name: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (mode == Mode.PHOTO) "image/jpeg" else "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
    }

    private fun photo() {
        val name = Lens.fileName(Mode.PHOTO, LocalDateTime.now())
        if (state.sound) sound.play(MediaActionSound.SHUTTER_CLICK)
        state.flashBlink = true
        if (state.processed) return processedPhoto(name)
        val out = ImageCapture.OutputFileOptions.Builder(activity.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values(Mode.PHOTO, name)).build()
        controller.takePicture(out, main, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                state.flashBlink = false
                r.savedUri?.let { state.saved(Shot(0, it.toString(), name, Mode.PHOTO, System.currentTimeMillis())) }
            }
            override fun onError(e: ImageCaptureException) { state.flashBlink = false; state.failed() }
        })
    }

    /** Colour style, watermark, square or document: the photo is taken into memory, changed, and then saved. */
    private fun processedPhoto(name: String) {
        val look = PhotoLook(state.color, state.watermark, state.ratio == Ratio.R1_1, state.scene == Scene.DOCUMENT, state.front)
        controller.takePicture(work, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val uri = runCatching {
                    val bitmap = image.use { look.render(it.toBitmap(), it.imageInfo.rotationDegrees) }
                    val target = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values(Mode.PHOTO, name)) ?: error("no uri")
                    activity.contentResolver.openOutputStream(target)?.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                    bitmap.recycle(); target
                }.getOrNull()
                main.execute {
                    state.flashBlink = false
                    if (uri != null) state.saved(Shot(0, uri.toString(), name, Mode.PHOTO, System.currentTimeMillis())) else state.failed()
                }
            }
            override fun onError(e: ImageCaptureException) { main.execute { state.flashBlink = false; state.failed() } }
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

    fun release() { recording?.stop(); sound.release(); work.shutdown() }
}
