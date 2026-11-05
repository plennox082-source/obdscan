package com.example.obdscan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
    val transition = rememberInfiniteTransition(label = "circuitLights")
    val lightProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lightProgress"
    )

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

        // Fixed circuit traces provide the tracks for the traveling light pulses.
        val routes = (0..7).map { i ->
            val startY = h * (0.10f + i * 0.105f)
            val direction = if (i % 2 == 0) 1f else -1f
            val startX = if (direction > 0) 0f else w
            val endX = if (direction > 0) w * 0.28f else w * 0.72f
            listOf(
                Offset(startX, startY),
                Offset(startX + direction * w * 0.12f, startY),
                Offset(startX + direction * w * 0.18f, startY + h * 0.05f),
                Offset(endX, startY + h * 0.05f)
            )
        }
        routes.forEachIndexed { index, points ->
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val accent = if (index % 3 == 0) cyan else traceGlow
            drawPath(path, accent.copy(alpha = 0.10f), style = Stroke(width = 9f))
            drawPath(path, accent.copy(alpha = 0.43f), style = Stroke(width = 2.5f))
        }

        // Additional routes carry pulses in several directions across the display.
        val multiDirectionRoutes = listOf(
            listOf(Offset(0f, h * 0.22f), Offset(w * 0.18f, h * 0.22f), Offset(w * 0.24f, h * 0.28f), Offset(w * 0.48f, h * 0.28f)),
            listOf(Offset(w, h * 0.34f), Offset(w * 0.82f, h * 0.34f), Offset(w * 0.76f, h * 0.40f), Offset(w * 0.54f, h * 0.40f)),
            listOf(Offset(0f, h * 0.76f), Offset(w * 0.15f, h * 0.76f), Offset(w * 0.21f, h * 0.70f), Offset(w * 0.46f, h * 0.70f)),
            listOf(Offset(w, h * 0.84f), Offset(w * 0.83f, h * 0.84f), Offset(w * 0.77f, h * 0.78f), Offset(w * 0.53f, h * 0.78f)),
            listOf(Offset(w * 0.16f, 0f), Offset(w * 0.16f, h * 0.16f), Offset(w * 0.22f, h * 0.22f), Offset(w * 0.22f, h * 0.34f)),
            listOf(Offset(w * 0.84f, h), Offset(w * 0.84f, h * 0.86f), Offset(w * 0.78f, h * 0.80f), Offset(w * 0.78f, h * 0.68f)),
            listOf(Offset(w * 0.34f, 0f), Offset(w * 0.34f, h * 0.12f), Offset(w * 0.40f, h * 0.18f), Offset(w * 0.40f, h * 0.29f)),
            listOf(Offset(w * 0.66f, h), Offset(w * 0.66f, h * 0.88f), Offset(w * 0.60f, h * 0.82f), Offset(w * 0.60f, h * 0.71f))
        )

        fun positionAlong(points: List<Offset>, progress: Float): Offset {
            val lengths = points.zipWithNext { a, b -> (b - a).getDistance() }
            val totalLength = lengths.sum().coerceAtLeast(1f)
            var distance = progress.coerceIn(0f, 1f) * totalLength
            for (i in lengths.indices) {
                if (distance <= lengths[i] || i == lengths.lastIndex) {
                    val fraction = (distance / lengths[i].coerceAtLeast(1f)).coerceIn(0f, 1f)
                    return Offset(
                        points[i].x + (points[i + 1].x - points[i].x) * fraction,
                        points[i].y + (points[i + 1].y - points[i].y) * fraction
                    )
                }
                distance -= lengths[i]
            }
            return points.last()
        }

        multiDirectionRoutes.forEachIndexed { index, points ->
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val accent = if (index % 2 == 0) cyan else orange
            drawPath(path, accent.copy(alpha = 0.07f), style = Stroke(width = 8f))
            drawPath(path, traceGlow.copy(alpha = 0.30f), style = Stroke(width = 2f))

            val offset = (index * 0.137f) % 1f
            val forward = index % 2 == 0
            val rawProgress = (lightProgress.value + offset) % 1f
            val progress = if (forward) rawProgress else 1f - rawProgress
            val head = positionAlong(points, progress)
            val tail = positionAlong(points, (progress - 0.10f).coerceAtLeast(0f))
            drawLine(accent.copy(alpha = 0.35f), tail, head, strokeWidth = 13f)
            drawLine(accent.copy(alpha = 0.95f), tail, head, strokeWidth = 4f)
            drawCircle(accent.copy(alpha = 0.18f), radius = 15f, center = head)
            drawCircle(accent.copy(alpha = 0.55f), radius = 8f, center = head)
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 3f, center = head)
        }

        // Fixed board-edge circuitry and solder pads.
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

        // Glass-like frame around the central diagnostic emblem.
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

        // Large translucent orange check-engine symbol.
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

        drawRect(Brush.horizontalGradient(listOf(Color(0x55000000), Color.Transparent, Color(0x55000000))))
    }
}
