package com.example.obdscan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Lets the user switch between the original and bidirectional app backgrounds. */
@Composable
fun BackgroundSwitcher(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE) }
    var useBidirectional by remember { mutableStateOf(preferences.getBoolean("bidirectional_background", false)) }

    Box(Modifier.fillMaxSize()) {
        if (useBidirectional) BidirectionalBackground() else CyberBackground()
        content()
        FloatingActionButton(
            onClick = {
                useBidirectional = !useBidirectional
                preferences.edit().putBoolean("bidirectional_background", useBidirectional).apply()
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp),
            containerColor = Color(0xFF241018),
            contentColor = Color.White
        ) {
            Text(if (useBidirectional) "BG 2" else "BG 1")
        }
    }
}

/** Decorative cyberpunk backdrop based on the submitted bidirectional SVG. */
@Composable
fun BidirectionalBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val sx = size.width / 1200f
        val sy = size.height / 400f
        fun point(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun box(x: Float, y: Float, width: Float, height: Float) =
            Size(width * sx, height * sy)
        fun strokeWidth(value: Float) = value * ((sx + sy) / 2f)
        fun roundRect(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float, stroke: Float? = null) {
            drawRoundRect(
                color = color,
                topLeft = point(x, y),
                size = box(x, y, width, height),
                cornerRadius = CornerRadius(radius * sx, radius * sy),
                style = stroke?.let { Stroke(strokeWidth(it)) } ?: androidx.compose.ui.graphics.drawscope.Fill
            )
        }

        drawRect(Color(0xFF070103))
        val grid = Color(0xFF24070D)
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth(0.8f))
            x += (40f * sx).coerceAtLeast(1f)
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth(0.8f))
            y += (40f * sy).coerceAtLeast(1f)
        }

        val red = Color(0xFFFF003C)
        val lime = Color(0xFF39FF14)
        roundRect(Color(0xFF140206), 60f, 45f, 440f, 170f, 6f)
        roundRect(red, 60f, 45f, 440f, 170f, 6f, 1.5f)
        roundRect(Color(0xFF031202), 700f, 45f, 440f, 170f, 6f)
        roundRect(lime, 700f, 45f, 440f, 170f, 6f, 1.5f)

        // Function toggles, shown as decorative on/off switches.
        roundRect(Color(0xFF0A1F05), 390f, 105f, 80f, 24f, 12f)
        roundRect(lime, 432f, 107f, 36f, 20f, 10f)
        roundRect(Color(0xFF240207), 390f, 155f, 80f, 24f, 12f)
        roundRect(red, 392f, 157f, 36f, 20f, 10f)

        // Command interface rows.
        listOf(100f, 150f).forEach { row ->
            roundRect(Color(0xFF010801), 720f, row, 400f, 32f, 4f)
            roundRect(Color(0xFF1B4D10), 720f, row, 400f, 32f, 4f, 1f)
        }

        drawLine(red, point(500f, 130f), point(700f, 130f), strokeWidth(2f))
        drawCircle(red, strokeWidth(3f), point(500f, 130f))
        drawCircle(lime, strokeWidth(3f), point(700f, 130f))

        // Raw hex terminal panel and colored data-stream rows.
        roundRect(Color(0xFF040001), 60f, 245f, 1080f, 120f, 4f)
        roundRect(Color(0xFF2B080F), 60f, 245f, 1080f, 120f, 4f, 1f)
        listOf(290f to red, 322f to lime, 350f to red).forEach { (row, color) ->
            drawLine(color.copy(alpha = 0.8f), point(80f, row), point(1120f, row), strokeWidth(1.5f))
        }
    }
}
