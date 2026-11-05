package com.godly.moonzoom

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ShutterOrange = Color(0xFFFF7A1A)

@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(Color.White.copy(0.14f), RoundedCornerShape(50))
            .border(BorderStroke(1.dp, Color.White.copy(0.30f)), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Nút chụp: lõi cam, vòng trắng, vòng ngoài trong suốt, hiệu ứng nhấn lò xo. */
@Composable
fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shutter"
    )
    Box(
        Modifier
            .size(84.dp)
            .scale(scale)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(84.dp)) {
            val r = size.minDimension / 2f
            drawCircle(Color.White.copy(0.12f), r)
            drawCircle(Color.White.copy(0.35f), r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            drawCircle(Color.White, r * 0.80f, style = Stroke(4.dp.toPx()))
            drawCircle(
                Brush.radialGradient(listOf(ShutterOrange.copy(0.55f), Color.Transparent), center, r * 0.8f),
                radius = r * 0.8f
            )
            drawCircle(if (enabled) ShutterOrange else ShutterOrange.copy(0.5f), r * 0.62f)
        }
    }
}
