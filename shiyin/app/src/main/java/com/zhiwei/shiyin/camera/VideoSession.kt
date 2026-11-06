package com.zhiwei.shiyin.camera

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.zhiwei.shiyin.core.FrameClock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class VideoSession(
    context: Context,
    private val onOverlayError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val overlayThread = HandlerThread("shiyin-overlay").apply { start() }
    private val overlayHandler = Handler(overlayThread.looper)
    private val drawer = TimestampDrawer(FrameClock())
    private val closed = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private var bindGeneration = 0
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var releaseAfterStop = false
    var onBound: (() -> Unit)? = null

    private val overlayEffect = OverlayEffect(
        CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
        0,
        overlayHandler,
    ) { error ->
        Log.e(TAG, "overlay effect failed", error)
        mainExecutor.execute {
            onOverlayError(error.message ?: "时间水印处理失败")
        }
    }.also { effect ->
        effect.setOnDrawListener { frame ->
            try {
                drawer.draw(frame)
            } catch (error: RuntimeException) {
                Log.e(TAG, "watermark draw failed", error)
            }
            true
        }
    }

    val hasFlash: Boolean
        get() = camera?.cameraInfo?.hasFlashUnit() == true

    val isReady: Boolean
        get() = videoCapture != null && !closed.get()

    fun bind(owner: LifecycleOwner, preview: PreviewView, facing: Int) {
        if (closed.get()) return
        val generation = ++bindGeneration
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (generation != bindGeneration || closed.get()) return@addListener
            val provider = try {
                future.get()
            } catch (error: Exception) {
                Log.e(TAG, "camera provider failed", error)
                onOverlayError(error.message ?: "相机打开失败")
                return@addListener
            }
            cameraProvider = provider
            try {
                bindUseCases(provider, owner, preview, facing)
                onBound?.invoke()
            } catch (error: Exception) {
                Log.e(TAG, "bind failed", error)
                onOverlayError(error.message ?: "这台设备没法同时预览、录像和水印")
            }
        }, mainExecutor)
    }

    fun start(file: File, withAudio: Boolean, listener: (VideoRecordEvent) -> Unit) {
        val capture = videoCapture ?: error("相机还没准备好")
        if (closed.get()) error("相机已经关闭")
        stopRequested.set(false)
        val options = FileOutputOptions.Builder(file).build()
        var pending = capture.output.prepareRecording(appContext, options)
        if (withAudio) pending = pending.withAudioEnabled()
        recording = pending.start(mainExecutor) { event ->
            listener(event)
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                if (releaseAfterStop) releaseNow()
            }
        }
    }

    fun stop() {
        val current = recording ?: return
        if (!stopRequested.compareAndSet(false, true)) return
        current.stop()
    }

    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun close() {
        releaseAfterStop = true
        if (recording == null) {
            releaseNow()
            return
        }
        stop()
        overlayHandler.postDelayed({ releaseNow() }, RELEASE_TIMEOUT_MS)
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        owner: LifecycleOwner,
        preview: PreviewView,
        facing: Int,
    ) {
        val rotation = preview.display?.rotation ?: Surface.ROTATION_0
        val previewUseCase = Preview.Builder().build().also {
            it.targetRotation = rotation
            it.surfaceProvider = preview.surfaceProvider
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()
        val capture = VideoCapture.withOutput(recorder).also { it.targetRotation = rotation }
        videoCapture = capture
        val selector = CameraSelector.Builder().requireLensFacing(facing).build()
        val group = UseCaseGroup.Builder()
            .addUseCase(previewUseCase)
            .addUseCase(capture)
            .addEffect(overlayEffect)
            .build()
        provider.unbindAll()
        camera = provider.bindToLifecycle(owner, selector, group)
    }

    private fun releaseNow() {
        if (!closed.compareAndSet(false, true)) return
        mainExecutor.execute {
            bindGeneration += 1
            runCatching { cameraProvider?.unbindAll() }
            runCatching { overlayEffect.close() }
            videoCapture = null
            camera = null
            overlayThread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "Shiyin"
        const val RELEASE_TIMEOUT_MS = 5_000L
    }
}
