package pl.pam.startproject.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Wykres liniowy prędkości w czasie (t z prób w ms od startu pomiaru, v w km/h).
 */
@Composable
fun SpeedOverTimeChart(
    samples: List<Pair<Long, Float>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
) {
    if (samples.size < 2) {
        return
    }
    val t0 = samples.first().first
    val t1 = samples.last().first
    val dt = max((t1 - t0).toFloat(), 1f)
    val vmax = samples.maxOf { it.second }.coerceAtLeast(1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val w = size.width
        val h = size.height
        val padL = 36.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 8.dp.toPx()
        val padB = 28.dp.toPx()
        val chartW = w - padL - padR
        val chartH = h - padT - padB

        fun xOf(t: Long): Float = padL + (t - t0) / dt * chartW
        fun yOf(v: Float): Float = padT + chartH - (v / vmax) * chartH

        // Siatka pozioma
        for (i in 0..4) {
            val frac = i / 4f
            val y = padT + chartH * (1f - frac)
            drawLine(gridColor, Offset(padL, y), Offset(padL + chartW, y), strokeWidth = 1.dp.toPx())
        }

        val path = Path()
        samples.forEachIndexed { i, (t, v) ->
            val x = xOf(t)
            val y = yOf(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))

        // Oś X — etykiety czasu (sekundy)
        val steps = 4
        for (i in 0..steps) {
            val frac = i / steps.toFloat()
            val x = padL + chartW * frac
            drawLine(
                gridColor,
                Offset(x, padT + chartH),
                Offset(x, padT + chartH + 4.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
