package com.example.obdscan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun BackgroundSwitcher(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CircuitBoardBackground()
        content()
        CloudVoiceAssistant()
    }
}

@Composable
private fun CircuitBoardBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val board = Color(0xFF061911)
        val trace = Color(0xFF16764B)
        val traceGlow = Color(0xFF20B878)
        val orange = Color(0xFFFF8A00)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B2A20), board, Color(0xFF03120F))))

        val step = (w / 9f).coerceAtLeast(36f)
        var x = 0f
        while (x <= w + step) {
            drawLine(trace.copy(alpha = 0.30f), Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
            x += step
        }
        var y = 0f
        while (y <= h + step) {
            drawLine(trace.copy(alpha = 0.27f), Offset(0f, y), Offset(w, y), strokeWidth = 2f)
            y += step
        }

        // Angled circuit paths and solder-pad nodes add detail without competing with app content.
        for (i in 0..7) {
            val startY = h * (0.12f + i * 0.105f)
            val direction = if (i % 2 == 0) 1f else -1f
            val startX = if (direction > 0) 0f else w
            val endX = if (direction > 0) w * 0.28f else w * 0.72f
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(startX + direction * w * 0.12f, startY)
                lineTo(startX + direction * w * 0.18f, startY + h * 0.05f)
                lineTo(endX, startY + h * 0.05f)
            }
            drawPath(path, traceGlow.copy(alpha = 0.5f), style = Stroke(width = 3f))
            drawCircle(traceGlow.copy(alpha = 0.7f), radius = 4f, center = Offset(endX, startY + h * 0.05f))
        }

        // Large translucent check-engine symbol behind page content.
        val scale = minOf(w / 720f, h / 740f)
        val iconW = 620f * scale
        val iconH = 310f * scale
        val left = (w - iconW) / 2f
        val top = (h - iconH) / 2f
        val engine = Path().apply {
            moveTo(left + 90f * scale, top + 90f * scale)
            lineTo(left + 170f * scale, top + 90f * scale)
            lineTo(left + 210f * scale, top + 35f * scale)
            lineTo(left + 420f * scale, top + 35f * scale)
            lineTo(left + 470f * scale, top + 90f * scale)
            lineTo(left + 550f * scale, top + 90f * scale)
            lineTo(left + 550f * scale, top + 145f * scale)
            lineTo(left + 615f * scale, top + 145f * scale)
            lineTo(left + 615f * scale, top + 250f * scale)
            lineTo(left + 520f * scale, top + 250f * scale)
            lineTo(left + 475f * scale, top + 300f * scale)
            lineTo(left + 205f * scale, top + 300f * scale)
            lineTo(left + 165f * scale, top + 250f * scale)
            lineTo(left + 90f * scale, top + 250f * scale)
            close()
        }
        drawPath(engine, orange.copy(alpha = 0.13f))
        drawPath(engine, orange.copy(alpha = 0.58f), style = Stroke(width = 11f * scale))
        drawLine(orange.copy(alpha = 0.62f), Offset(left + 90f * scale, top + 145f * scale), Offset(left + 18f * scale, top + 145f * scale), 10f * scale)
        drawLine(orange.copy(alpha = 0.62f), Offset(left + 18f * scale, top + 145f * scale), Offset(left + 18f * scale, top + 205f * scale), 10f * scale)
        drawLine(orange.copy(alpha = 0.65f), Offset(left + 295f * scale, top + 110f * scale), Offset(left + 295f * scale, top + 215f * scale), 10f * scale)
        drawLine(orange.copy(alpha = 0.65f), Offset(left + 375f * scale, top + 110f * scale), Offset(left + 375f * scale, top + 215f * scale), 10f * scale)
        drawCircle(orange.copy(alpha = 0.7f), 8f * scale, Offset(left + 295f * scale, top + 265f * scale))
        drawCircle(orange.copy(alpha = 0.7f), 8f * scale, Offset(left + 375f * scale, top + 265f * scale))
    }
}
