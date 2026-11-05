package com.godly.moonzoom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/** Vòng tiến trình Liquid Glass: giá trị [progress] luôn là số liệu thật do pipeline báo về. */
@Composable
fun GlassProgressRing(progress: Float, centerText: String, subText: String, size: Dp = 210.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "ring")
    val inf = rememberInfiniteTransition(label = "spin")
    val phase by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase"
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(0.20f), Color.White.copy(0.04f)), center, r),
                radius = r
            )
            // Mực dâng lên theo tiến độ (hiệu ứng "sạc")
            val inner = r * 0.84f
            val clip = Path().apply { addOval(Rect(center, inner)) }
            clipPath(clip) {
                val top = center.y + inner - 2f * inner * p
                drawRect(MoonBlue.copy(0.22f), Offset(0f, top), Size(this.size.width, this.size.height - top))
                drawRect(MoonBlue.copy(0.40f), Offset(0f, top), Size(this.size.width, 3.dp.toPx()))
            }
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            drawArc(Color.White.copy(0.15f), -90f, 360f, false, tl, sz, style = Stroke(stroke))
            drawArc(MoonBlue.copy(0.30f), -90f, 360f * p, false, tl, sz, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(MoonBlue, -90f, 360f * p, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White.copy(0.55f), -90f + phase * 360f, 18f, false, tl, sz, style = Stroke(stroke * 0.5f, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(subText, color = Color.White.copy(0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
fun StackOverlay(session: Session, onCancel: () -> Unit) {
    if (session is Session.Idle) return
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(0.65f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (session) {
                is Session.Capturing -> {
                    val plan = session.plan
                    Text(
                        if (plan.milky) "MILKY WAY" else "NIGHT",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    if (plan.fixedFrames == 0) {
                        val prog = 1f - session.remainingMs.toFloat() / session.totalMs.toFloat()
                        val secs = ceil(session.remainingMs / 1000.0).toInt()
                        GlassProgressRing(prog, "$secs", if (secs > 0) "giây" else "khung cuối...")
                    } else {
                        GlassProgressRing(
                            session.frames.toFloat() / plan.fixedFrames,
                            "${session.frames}/${plan.fixedFrames}", "khung"
                        )
                    }
                    Text(
                        "Đã thu ${session.frames} khung",
                        color = Color.White, fontSize = 13.sp
                    )
                    Text(
                        plan.info, color = Color.White.copy(0.75f), fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Đặt máy lên chân máy hoặc tựa vào vật cố định",
                        color = Color.White.copy(0.6f), fontSize = 11.sp, textAlign = TextAlign.Center
                    )
                }
                is Session.Processing -> {
                    GlassProgressRing(session.progress, "${(session.progress * 100).toInt()}%", "xử lý ảnh")
                    Text(session.status, color = Color.White, fontSize = 13.sp)
                }
                Session.Idle -> Unit
            }
            GlassChip("HỦY", onClick = onCancel)
        }
    }
}
