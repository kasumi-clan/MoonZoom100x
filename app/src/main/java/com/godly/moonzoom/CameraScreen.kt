package com.godly.moonzoom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

enum class CamMode(val label: String, val ready: Boolean) {
    PHOTO("PHOTO", true),
    NIGHT("NIGHT", true),
    MILKY("MILKY WAY", true),
    PRO("PRO", false),
    AI("AI PHOTO", false)
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val f = ProcessCameraProvider.getInstance(this)
    f.addListener({ cont.resume(f.get()) }, ContextCompat.getMainExecutor(this))
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraContent()
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Moon Zoom cần quyền Camera để hoạt động.",
                    color = Color.White, textAlign = TextAlign.Center
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Cấp quyền") }
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text("Mở cài đặt ứng dụng") }
            }
        }
    }
}

@Composable
private fun CameraContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // TextureView: cho phép scale
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val zoomController = remember { SmoothZoomController(scope) }
    val zoom by zoomController.zoom.collectAsState()

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var nativeMax by remember { mutableFloatStateOf(1f) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    var expIndex by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var fx by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(CamMode.PHOTO) }
    var session by remember { mutableStateOf<Session>(Session.Idle) }
    var stackJob by remember { mutableStateOf<Job?>(null) }

    val cameraNow by rememberUpdatedState(camera)

    // Ảnh mới nhất trong thư viện Moon Zoom
    LaunchedEffect(Unit) {
        GalleryHelper.latest(context)?.let { u ->
            lastUri = u
            thumb = GalleryHelper.thumb(context, u)?.asImageBitmap()
        }
    }

    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); message = null }
    }

    // Gắn camera khi đổi ống kính
    LaunchedEffect(lensFacing) {
        try {
            val provider = context.awaitCameraProvider()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            if (!provider.hasCamera(selector)) {
                message = "Thiết bị không có camera này"
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                return@LaunchedEffect
            }
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            provider.unbindAll()
            val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            imageCapture = capture
            camera = cam
            flashMode = ImageCapture.FLASH_MODE_OFF
            expIndex = 0f
            focusPoint = null
            nativeMax = (cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f).coerceAtLeast(1f)
            zoomController.setTarget(1f, immediate = true)
        } catch (e: Exception) {
            message = "Không mở được camera: ${e.message}"
        }
    }

    LaunchedEffect(flashMode, imageCapture) { imageCapture?.flashMode = flashMode }

    // Áp zoom: phần gốc do camera (CameraX zoomRatio), phần vượt quá là crop số thật trên preview
    LaunchedEffect(camera, nativeMax) {
        zoomController.zoom.collect { z ->
            val native = min(z, nativeMax).coerceAtLeast(1f)
            camera?.cameraControl?.setZoomRatio(native)
            val extra = z / native
            previewView.scaleX = extra
            previewView.scaleY = extra
        }
    }

    // Tự ẩn vòng lấy nét
    LaunchedEffect(focusTick) {
        if (focusTick > 0) { delay(4500); focusPoint = null }
    }

    val fxAlpha by animateFloatAsState(if (fx) 0.8f else 0f, tween(110), label = "fx")

    fun capture() {
        val ic = imageCapture ?: return
        if (busy) return
        busy = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { fx = true; delay(90); fx = false }
        val extra = previewView.scaleX
        val tmp = File.createTempFile("cap_", ".jpg", context.cacheDir)
        ic.takePicture(
            ImageCapture.OutputFileOptions.Builder(tmp).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val uri = runCatching { CaptureSaver.save(context, tmp, extra) }.getOrNull()
                        tmp.delete()
                        busy = false
                        if (uri != null) {
                            lastUri = uri
                            thumb = GalleryHelper.thumb(context, uri)?.asImageBitmap()
                        } else message = "Lưu ảnh thất bại"
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    tmp.delete(); busy = false
                    message = "Chụp lỗi: ${exception.message}"
                }
            }
        )
    }

    fun startStack(milky: Boolean) {
        val ic = imageCapture ?: return
        val cam = camera ?: return
        if (busy) return
        busy = true
        focusPoint = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val extra = previewView.scaleX
        stackJob = scope.launch {
            val files = mutableListOf<File>()
            val temps = mutableListOf<File>()
            val plan = StackPlanner.plan(cam, milky)
            try {
                ic.flashMode = ImageCapture.FLASH_MODE_OFF
                if (plan.manual) StackPlanner.applyManual(cam, plan, mainExecutor)
                val start = SystemClock.elapsedRealtime()
                val ticker = launch {
                    while (isActive) {
                        val el = SystemClock.elapsedRealtime() - start
                        session = Session.Capturing(maxOf(0L, plan.totalMs - el), plan.totalMs, files.size, plan)
                        delay(100)
                    }
                }
                while (true) {
                    val el = SystemClock.elapsedRealtime() - start
                    val enough = if (plan.fixedFrames > 0) files.size >= plan.fixedFrames
                    else files.size >= plan.maxFrames || (el >= plan.totalMs && files.isNotEmpty())
                    if (enough) break
                    val f = File.createTempFile("stk_", ".jpg", context.cacheDir)
                    temps += f
                    ic.takeToFile(mainExecutor, f)
                    files += f
                }
                ticker.cancel()
                // Sau khi thu xong khung thì bỏ phơi sáng tay để xử lý nhẹ nhàng
                session = Session.Processing(0f, "Đang chuẩn bị...")
                val params = if (milky) NightStacker.MILKY else NightStacker.NIGHT
                val out = NightStacker.process(context, files, params) { p, s ->
                    session = Session.Processing(p, s)
                }
                temps += out
                val uri = CaptureSaver.save(context, out, extra, if (milky) "MILKY" else "NIGHT")
                lastUri = uri
                thumb = GalleryHelper.thumb(context, uri)?.asImageBitmap()
                message = if (milky && !plan.manual)
                    "Đã lưu (chồng khung exposure tự động, không phải phơi sáng dài)"
                else "Đã lưu ảnh"
            } catch (e: CancellationException) {
                message = "Đã hủy"
                throw e
            } catch (e: OutOfMemoryError) {
                message = "Hết bộ nhớ khi xử lý ảnh"
            } catch (e: Exception) {
                message = "Lỗi chụp chồng khung: ${e.message}"
            } finally {
                runCatching { StackPlanner.clearManual(cam) }
                ic.flashMode = flashMode
                temps.forEach { it.delete() }
                session = Session.Idle
                busy = false
            }
        }
    }

    val nativeApplied = min(zoom, nativeMax).coerceAtLeast(1f)
    val extraNow = zoom / nativeApplied
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true

    Box(Modifier.fillMaxSize()) {
        // ---- Preview 3:4 (khớp ảnh chụp 4:3) ----
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(3f / 4f).clipToBounds()
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Lớp cử chỉ: chạm lấy nét + đo sáng, chụm 2 ngón để zoom
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            val cam = cameraNow ?: return@detectTapGestures
                            val extra = previewView.scaleX
                            val cx = previewView.width / 2f
                            val cy = previewView.height / 2f
                            val x = cx + (off.x - cx) / extra
                            val y = cy + (off.y - cy) / extra
                            val point = previewView.meteringPointFactory.createPoint(x, y)
                            val action = FocusMeteringAction.Builder(
                                point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                            cam.cameraControl.startFocusAndMetering(action)
                            focusPoint = off
                            focusTick++
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (zoomChange != 1f) {
                                zoomController.setTarget(zoomController.target * zoomChange)
                            }
                        }
                    }
            )

            // Vòng lấy nét + thanh exposure
            focusPoint?.let { p ->
                val ring = remember { Animatable(1.5f) }
                LaunchedEffect(focusTick) { ring.snapTo(1.5f); ring.animateTo(1f, tween(260)) }
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White, 34.dp.toPx() * ring.value, p, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.White.copy(0.9f), 3.dp.toPx(), p)
                }
                val expState = camera?.cameraInfo?.exposureState
                val range = expState?.exposureCompensationRange
                if (expState != null && range != null &&
                    expState.isExposureCompensationSupported && range.upper > range.lower
                ) {
                    val sliderW = 160.dp
                    Column(
                        Modifier.offset {
                            val maxX = (previewView.width - sliderW.toPx()).toInt().coerceAtLeast(0)
                            val x = (p.x - sliderW.toPx() / 2f).roundToInt().coerceIn(0, maxX)
                            val y = if (p.y > previewView.height * 0.8f) p.y - 96.dp.toPx() else p.y + 44.dp.toPx()
                            IntOffset(x, y.roundToInt())
                        }.width(sliderW),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Slider(
                            value = expIndex,
                            onValueChange = {
                                expIndex = it
                                cameraNow?.cameraControl?.setExposureCompensationIndex(it.roundToInt())
                                focusTick++
                            },
                            valueRange = range.lower.toFloat()..range.upper.toFloat(),
                            steps = (range.upper - range.lower - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MoonBlue,
                                inactiveTrackColor = Color.White.copy(0.3f)
                            )
                        )
                        Text(
                            "EV " + String.format(
                                java.util.Locale.US, "%+.1f",
                                expIndex.roundToInt() * expState.exposureCompensationStep.toFloat()
                            ),
                            color = Color.White, fontSize = 11.sp
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = fxAlpha)))
        }

        // ---- Thương hiệu + flash (trên cùng) ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("Moon Zoom", color = Color.White.copy(0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("By GODLY", color = Color.White.copy(0.6f), fontSize = 10.sp)
            }
            if (hasFlash) {
                GlassChip(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> "FLASH ON"
                        ImageCapture.FLASH_MODE_AUTO -> "FLASH AUTO"
                        else -> "FLASH OFF"
                    }
                ) {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }
            }
        }

        // ---- Điều khiển dưới ----
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (extraNow > 1.01f)
                    "${formatZoom(zoom)} · camera ${formatZoom(nativeApplied)} + crop số ${formatZoom(extraNow)}"
                else "${formatZoom(zoom)} · zoom của camera",
                color = Color.White.copy(0.75f), fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmoothZoomController.PRESETS.forEach { p ->
                    GlassChip(formatZoom(p).replace(".0", "")) { zoomController.setTarget(p) }
                }
            }
            MoonZoomDial(
                zoom = zoom,
                modifier = Modifier.height(110.dp),
                onFractionDelta = { df ->
                    val logMax = ln(SmoothZoomController.MAX)
                    val f = (ln(zoomController.target) / logMax + df).coerceIn(0f, 1f)
                    zoomController.setTarget(if (f >= 0.9999f) SmoothZoomController.MAX else exp(f * logMax))
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CamMode.values().forEach { m ->
                    Text(
                        m.label,
                        color = when {
                            m == mode -> MoonBlue
                            m.ready -> Color.White
                            else -> Color.White.copy(0.35f)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            if (m.ready) mode = m
                            else message = "${m.label}: chưa làm ở bản nền này (giai đoạn sau)"
                        }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(54.dp)
                        .background(Color.White.copy(0.12f), RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color.White.copy(0.7f), RoundedCornerShape(14.dp))
                        .clickable(enabled = lastUri != null) {
                            lastUri?.let {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).setDataAndType(it, "image/jpeg")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val t = thumb
                    if (t != null) {
                        Image(
                            t, contentDescription = "Ảnh mới nhất",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().padding(2.dp)
                        )
                    }
                }
                ShutterButton(
                    enabled = !busy && camera != null,
                    onClick = {
                        when (mode) {
                            CamMode.PHOTO -> capture()
                            CamMode.NIGHT -> startStack(false)
                            CamMode.MILKY -> startStack(true)
                            else -> Unit
                        }
                    }
                )
                GlassChip("ĐỔI CAM") {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }
            }
        }
        StackOverlay(session) { stackJob?.cancel() }
    }
}
