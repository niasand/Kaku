package com.timestampcam.app.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executor

/**
 * CameraX preview + video, with the watermark composited into both outputs.
 */
class TimestampCamera(
    private val context: Context,
    val overlayState: OverlayState,
) {
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val overlayThread = HandlerThread("timestamp-overlay").apply { start() }
    private val overlayHandler = Handler(overlayThread.looper)
    private var cameraProvider: ProcessCameraProvider? = null
    private var overlayEffect: OverlayEffect? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    var pendingDisplayName: String = ""
        private set

    fun bind(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int,
        onResult: (ready: Boolean, message: String?) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindUseCases(previewView, lifecycleOwner, lensFacing)
                onResult(true, null)
            } catch (error: Exception) {
                Log.e(TAG, "bind failed", error)
                onResult(false, error.message ?: "相机打开失败")
            }
        }, mainExecutor)
    }

    fun startRecording(withAudio: Boolean, listener: Consumer<VideoRecordEvent>): StartResult {
        val capture = videoCapture ?: return StartResult(started = false, message = "相机还没准备好")
        if (recording != null) return StartResult(started = true, message = null)
        return try {
            recording = begin(capture, withAudio, listener)
            StartResult(started = true, message = null)
        } catch (security: SecurityException) {
            Log.e(TAG, "audio permission missing", security)
            if (!withAudio) {
                StartResult(started = false, message = security.message ?: "无法开始录像")
            } else {
                try {
                    recording = begin(capture, false, listener)
                    StartResult(started = true, message = "没有麦克风权限，已改为无声录像")
                } catch (error: Exception) {
                    Log.e(TAG, "start recording failed", error)
                    StartResult(started = false, message = error.message ?: "无法开始录像")
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "start recording failed", error)
            StartResult(started = false, message = error.message ?: "无法开始录像")
        }
    }

    fun stopRecording() {
        val current = recording ?: return
        recording = null
        current.stop()
    }

    fun release() {
        runCatching { recording?.stop() }
        recording = null
        runCatching { cameraProvider?.unbindAll() }
        val effect = overlayEffect
        overlayEffect = null
        overlayHandler.post {
            runCatching { effect?.close() }
            overlayThread.quitSafely()
        }
    }

    private fun bindUseCases(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int,
    ) {
        val provider = cameraProvider ?: return
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

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

        val drawer = WatermarkDrawer()
        val effect = OverlayEffect(
            androidx.camera.core.CameraEffect.PREVIEW or androidx.camera.core.CameraEffect.VIDEO_CAPTURE,
            0,
            overlayHandler,
        ) { error ->
            Log.e(TAG, "overlay effect", error)
        }
        effect.setOnDrawListener { frame ->
            try {
                drawer.draw(frame, overlayState.subtitle)
            } catch (error: Throwable) {
                Log.e(TAG, "draw watermark", error)
            }
            true
        }
        runCatching { overlayEffect?.close() }
        overlayEffect = effect

        val builder = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .addEffect(effect)
        previewView.viewPort?.let { builder.setViewPort(it) }
            ?: builder.setViewPort(
                ViewPort.Builder(Rational(previewView.width.coerceAtLeast(1), previewView.height.coerceAtLeast(1)), rotation)
                    .setScaleType(ViewPort.FILL_CENTER)
                    .build(),
            )

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, builder.build())
    }

    private fun begin(
        capture: VideoCapture<Recorder>,
        withAudio: Boolean,
        listener: Consumer<VideoRecordEvent>,
    ): Recording {
        pendingDisplayName = createDisplayName()
        val mediaStore = try {
            mediaStoreOptions(pendingDisplayName)
        } catch (error: Exception) {
            Log.w(TAG, "MediaStore options failed", error)
            null
        }
        val pending = if (mediaStore != null) {
            capture.output.prepareRecording(context, mediaStore)
        } else {
            capture.output.prepareRecording(context, fileOptions(pendingDisplayName))
        }
        val configured = if (withAudio) pending.withAudioEnabled() else pending
        return configured.start(mainExecutor, listener)
    }

    private fun mediaStoreOptions(name: String): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/TimestampCam")
            }
        }
        return MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()
    }

    private fun fileOptions(name: String): FileOutputOptions {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        dir.mkdirs()
        return FileOutputOptions.Builder(File(dir, name)).build()
    }

    private fun createDisplayName(): String {
        val date = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
        return "时间记录_%04d%02d%02d_%02d%02d%02d.mp4".format(
            date.year,
            date.monthValue,
            date.dayOfMonth,
            date.hour,
            date.minute,
            date.second,
        )
    }

    private companion object {
        const val TAG = "TimestampCamera"
    }
}

class OverlayState {
    @Volatile
    var subtitle: String = ""
}

data class StartResult(val started: Boolean, val message: String?)
