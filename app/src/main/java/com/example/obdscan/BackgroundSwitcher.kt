package com.example.obdscan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
        val cyan = Color(0xFF00D9FF)
        val orange = Color(0xFFFF8A00)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B2A20), board, Color(0xFF03120F))))

        val step = (w / 9f).coerceAtLeast(36f)
        var x = 0f
        while (x <= w + step) {
            drawLine(trace.copy(alpha = 0.24f), Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
            x += step
        }
        var y = 0f
        while (y <= h + step) {
            drawLine(trace.copy(alpha = 0.22f), Offset(0f, y), Offset(w, y), strokeWidth = 2f)
            y += step
        }

        // Circuit traces run around the central display area, keeping the foreground readable.
        for (i in 0..7) {
            val startY = h * (0.10f + i * 0.105f)
            val direction = if (i % 2 == 0) 1f else -1f
            val startX = if (direction > 0) 0f else w
            val endX = if (direction > 0) w * 0.28f else w * 0.72f
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(startX + direction * w * 0.12f, startY)
                lineTo(startX + direction * w * 0.18f, startY + h * 0.05f)
                lineTo(endX, startY + h * 0.05f)
            }
            val accent = if (i % 3 == 0) cyan else traceGlow
            drawPath(path, accent.copy(alpha = 0.13f), style = Stroke(width = 9f))
            drawPath(path, accent.copy(alpha = 0.52f), style = Stroke(width = 2.5f))
            drawCircle(accent.copy(alpha = 0.18f), radius = 9f, center = Offset(endX, startY + h * 0.05f))
            drawCircle(accent.copy(alpha = 0.8f), radius = 3f, center = Offset(endX, startY + h * 0.05f))
        }

        // Fine angular traces, pads, and board-edge accents.
        val sidePath = Path().apply {
            moveTo(0f, h * 0.31f); lineTo(w * 0.08f, h * 0.31f); lineTo(w * 0.12f, h * 0.36f)
            lineTo(w * 0.12f, h * 0.64f); lineTo(w * 0.07f, h * 0.69f); lineTo(0f, h * 0.69f)
            moveTo(w, h * 0.28f); lineTo(w * 0.92f, h * 0.28f); lineTo(w * 0.88f, h * 0.33f)
            lineTo(w * 0.88f, h * 0.67f); lineTo(w * 0.93f, h * 0.72f); lineTo(w, h * 0.72f)
        }
        drawPath(sidePath, cyan.copy(alpha = 0.42f), style = Stroke(width = 3f))
        for (i in 0..5) {
            val padY = h * (0.20f + i * 0.12f)
            drawCircle(traceGlow.copy(alpha = 0.55f), 5f, Offset(w * 0.055f, padY), style = Stroke(width = 2f))
            drawCircle(cyan.copy(alpha = 0.48f), 5f, Offset(w * 0.945f, padY), style = Stroke(width = 2f))
        }

        // Restrained glass-like frame around the central diagnostic emblem.
        val frameW = w * 0.78f
        val frameH = h * 0.42f
        val frameLeft = (w - frameW) / 2f
        val frameTop = (h - frameH) / 2f
        drawRoundRect(
            color = cyan.copy(alpha = 0.12f),
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameW, frameH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
            style = Stroke(width = 2f)
        )
        drawRoundRect(
            color = orange.copy(alpha = 0.07f),
            topLeft = Offset(frameLeft + 6f, frameTop + 6f),
            size = Size(frameW - 12f, frameH - 12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
            style = Stroke(width = 1f)
        )

        // Large translucent orange check-engine symbol, layered for a soft neon glow.
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
        drawPath(engine, orange.copy(alpha = 0.07f), style = Stroke(width = 34f * scale))
        drawPath(engine, orange.copy(alpha = 0.14f), style = Stroke(width = 20f * scale))
        drawPath(engine, orange.copy(alpha = 0.11f))
        drawPath(engine, orange.copy(alpha = 0.72f), style = Stroke(width = 9f * scale))
        drawLine(orange.copy(alpha = 0.72f), Offset(left + 90f * scale, top + 145f * scale), Offset(left + 18f * scale, top + 145f * scale), 9f * scale)
        drawLine(orange.copy(alpha = 0.72f), Offset(left + 18f * scale, top + 145f * scale), Offset(left + 18f * scale, top + 205f * scale), 9f * scale)
        drawLine(orange.copy(alpha = 0.75f), Offset(left + 295f * scale, top + 110f * scale), Offset(left + 295f * scale, top + 215f * scale), 9f * scale)
        drawLine(orange.copy(alpha = 0.75f), Offset(left + 375f * scale, top + 110f * scale), Offset(left + 375f * scale, top + 215f * scale), 9f * scale)
        drawCircle(orange.copy(alpha = 0.78f), 8f * scale, Offset(left + 295f * scale, top + 265f * scale))
        drawCircle(orange.copy(alpha = 0.78f), 8f * scale, Offset(left + 375f * scale, top + 265f * scale))

        // Subtle edge vignette gives the app a dashboard-screen finish.
        drawRect(
            Brush.horizontalGradient(listOf(Color(0x55000000), Color.Transparent, Color(0x55000000)))
        )
    }
}
