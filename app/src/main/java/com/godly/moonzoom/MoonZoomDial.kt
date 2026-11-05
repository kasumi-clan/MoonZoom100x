package com.godly.moonzoom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

val MoonBlue = Color(0xFF8CC8FF)

fun formatZoom(z: Float): String = when {
    z >= 99.95f -> "100X"
    z < 10f -> String.format(Locale.US, "%.1fX", z)
    else -> String.format(Locale.US, "%.0fX", z)
}

private fun angleOf(p: Offset, size: IntSize): Float =
    Math.toDegrees(
        atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())
    ).toFloat()

/**
 * Núm xoay zoom kiểu Liquid Glass. Xoay thuận chiều kim đồng hồ = zoom tới.
 * Dial KHÔNG quấn vòng: chạm 100X là dừng hẳn.
 * [onFractionDelta] nhận độ thay đổi theo thang log (0..1 = 1X..100X).
 */
@Composable
fun MoonZoomDial(
    zoom: Float,
    onFractionDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
    sweepDegrees: Float = 300f,
) {
    val haptic = LocalHapticFeedback.current
    val logMax = ln(SmoothZoomController.MAX)
    val fraction = (ln(zoom.coerceIn(1f, SmoothZoomController.MAX)) / logMax).coerceIn(0f, 1f)
    val onDelta by rememberUpdatedState(onFractionDelta)

    val nearestPreset = SmoothZoomController.PRESETS.firstOrNull { abs(ln(zoom / it)) < 0.02f }
    LaunchedEffect(nearestPreset) {
        if (nearestPreset != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                var last = 0f
                detectDragGestures(
                    onDragStart = { last = angleOf(it, size) },
                    onDrag = { change, _ ->
                        change.consume()
                        val a = angleOf(change.position, size)
                        var d = a - last
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        last = a
                        onDelta(d / sweepDegrees)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = center
            // Thân kính
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(0.24f), Color.White.copy(0.05f)),
                    center = c, radius = r
                ),
                radius = r
            )
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.White.copy(0.85f), MoonBlue.copy(0.45f),
                        Color.White.copy(0.10f), MoonBlue.copy(0.45f), Color.White.copy(0.85f)
                    ),
                    center = c
                ),
                radius = r - 1.dp.toPx(),
                style = Stroke(1.5.dp.toPx())
            )
            // Vệt phản chiếu
            val inset = r * 0.10f
            drawArc(
                Color.White.copy(0.35f), 200f, 70f, false,
                topLeft = Offset(inset, inset),
                size = Size(2 * (r - inset), 2 * (r - inset)),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
            // Cung tiến trình + glow
            val arcInset = r * 0.16f
            val arcSize = Size(2 * (r - arcInset), 2 * (r - arcInset))
            val sweep = sweepDegrees * fraction
            drawArc(
                MoonBlue.copy(0.25f), 120f, sweep, false,
                topLeft = Offset(arcInset, arcInset), size = arcSize,
                style = Stroke(9.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                MoonBlue, 120f, sweep, false,
                topLeft = Offset(arcInset, arcInset), size = arcSize,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
            // Vạch chia xoay theo zoom
            rotate(degrees = -fraction * sweepDegrees, pivot = c) {
                for (i in 0 until 60) {
                    val major = i % 5 == 0
                    val r1 = r * 0.66f
                    val r2 = r * (if (major) 0.76f else 0.71f)
                    val rad = Math.toRadians(i * 6.0 - 90.0)
                    drawLine(
                        Color.White.copy(if (major) 0.8f else 0.35f),
                        Offset(c.x + cos(rad).toFloat() * r1, c.y + sin(rad).toFloat() * r1),
                        Offset(c.x + cos(rad).toFloat() * r2, c.y + sin(rad).toFloat() * r2),
                        strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx()
                    )
                }
            }
            // Kim chỉ cố định ở đỉnh
            drawLine(
                MoonBlue, Offset(c.x, c.y - r * 0.80f), Offset(c.x, c.y - r * 0.90f),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
            )
        }
        Text(
            formatZoom(zoom),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
