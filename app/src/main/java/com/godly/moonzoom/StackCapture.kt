package com.godly.moonzoom

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/** Kế hoạch chụp chồng khung. fixedFrames > 0: chụp đủ số khung; ngược lại chụp theo thời gian totalMs. */
data class StackPlan(
    val milky: Boolean,
    val manual: Boolean,
    val exposureNs: Long,
    val iso: Int,
    val focusInfinity: Boolean,
    val totalMs: Long,
    val maxFrames: Int,
    val fixedFrames: Int,
    val info: String
)

sealed interface Session {
    object Idle : Session
    data class Capturing(val remainingMs: Long, val totalMs: Long, val frames: Int, val plan: StackPlan) : Session
    data class Processing(val progress: Float, val status: String) : Session
}

suspend fun <T> ListenableFuture<T>.awaitFuture(executor: Executor): T =
    suspendCancellableCoroutine { c ->
        addListener({
            try { c.resume(get()) } catch (e: Exception) { c.resumeWithException(e) }
        }, executor)
    }

suspend fun ImageCapture.takeToFile(executor: Executor, file: File) =
    suspendCancellableCoroutine<Unit> { c ->
        takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { c.resume(Unit) }
                override fun onError(exception: ImageCaptureException) { c.resumeWithException(exception) }
            }
        )
    }

@OptIn(ExperimentalCamera2Interop::class)
object StackPlanner {
    private const val MILKY_EXPOSURE_NS = 4_000_000_000L
    private const val MILKY_TOTAL_MS = 15_000L
    private const val MILKY_MAX_FRAMES = 12
    private const val NIGHT_FRAMES = 6

    fun plan(cam: Camera, milky: Boolean): StackPlan {
        if (!milky) {
            return StackPlan(
                false, false, 0L, 0, false, 0L, NIGHT_FRAMES, NIGHT_FRAMES,
                "Giữ yên máy · $NIGHT_FRAMES khung exposure tự động, căn chỉnh rồi chồng để giảm nhiễu"
            )
        }
        val info = Camera2CameraInfo.from(cam.cameraInfo)
        val caps = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val manual = caps?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
        val expR = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val isoR = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val minFocus = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        if (manual && expR != null && isoR != null) {
            val exp = min(expR.upper, MILKY_EXPOSURE_NS).coerceAtLeast(expR.lower)
            val iso = min(isoR.upper, 3200).coerceAtLeast(isoR.lower)
            val text = String.format(
                Locale.US,
                "Mỗi khung phơi sáng %.1fs · ISO %d · tối đa %d khung trong 15 giây.\nĐếm ngược 15s KHÔNG phải một lần phơi sáng 15s.",
                exp / 1e9, iso, MILKY_MAX_FRAMES
            )
            return StackPlan(true, true, exp, iso, minFocus > 0f, MILKY_TOTAL_MS, MILKY_MAX_FRAMES, 0, text)
        }
        return StackPlan(
            true, false, 0L, 0, false, MILKY_TOTAL_MS, MILKY_MAX_FRAMES, 0,
            "Máy không hỗ trợ phơi sáng tay: chồng nhiều khung exposure tự động trong 15 giây.\nĐây KHÔNG phải phơi sáng dài thật."
        )
    }

    suspend fun applyManual(cam: Camera, plan: StackPlan, executor: Executor) {
        val b = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, plan.exposureNs)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, plan.iso)
            .setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, plan.exposureNs)
        if (plan.focusInfinity) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
        }
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build()).awaitFuture(executor)
    }

    fun clearManual(cam: Camera) {
        Camera2CameraControl.from(cam.cameraControl).clearCaptureRequestOptions()
    }
}
