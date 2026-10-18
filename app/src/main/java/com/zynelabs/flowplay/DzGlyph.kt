package com.zynelabs.flowplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import com.zynelabs.flowplay.GlassText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Thin-line vector replacements for the text glyphs that used to act as
 * button icons. Text glyphs such as the gear, star, pencil or wastebasket are
 * drawn by the system emoji font on many phones (orange / coloured), which
 * clashes with the near-black + accent theme. These icons use the same
 * round-capped thin-line style as DzIcon and always take the given colour.
 */
private val DZ_GLYPHS = setOf(
    "\u2713", // check
    "\u2715", // close
    "\u26F6", // expand / fullscreen
    "\u2699", // gear
    "\u2605", // star filled
    "\u2606", // star outline
    "\u270E", // pencil
    "\u270F", // pencil + underline (rename)
    "\u2139", // info
    "\u25B6", // play
    "\u27F3", // refresh
    "\u2298", // disable
    "\uD83D\uDDD1", // trash
    "\u25F7", // clock
    "\u266A", // audio / music note
    "\u2197", // open external
    "\u2190", // back
    "\u2630", // list
    "\u25A6", // grid
    "\u25CE" // locate / now
)

fun isDzGlyph(s: String): Boolean = s in DZ_GLYPHS

@Composable
private fun RawDzGlyph(glyph: String, color: Color, iconSize: Dp = 20.dp) {
    if (!isDzGlyph(glyph)) {
        Text(glyph, color = color, fontSize = 14.sp)
        return
    }
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.085f
        val line = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2),
                strokeWidth = sw, cap = StrokeCap.Round)
        when (glyph) {
            "\u2713" -> {
                val p = Path().apply {
                    moveTo(w * 0.2f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.72f)
                    lineTo(w * 0.8f, h * 0.3f)
                }
                drawPath(p, color, style = line)
            }
            "\u2715" -> {
                ln(0.28f, 0.28f, 0.72f, 0.72f)
                ln(0.72f, 0.28f, 0.28f, 0.72f)
            }
            "\u26F6" -> {
                val p = Path().apply {
                    moveTo(w * 0.18f, h * 0.38f); lineTo(w * 0.18f, h * 0.18f); lineTo(w * 0.38f, h * 0.18f)
                    moveTo(w * 0.62f, h * 0.18f); lineTo(w * 0.82f, h * 0.18f); lineTo(w * 0.82f, h * 0.38f)
                    moveTo(w * 0.82f, h * 0.62f); lineTo(w * 0.82f, h * 0.82f); lineTo(w * 0.62f, h * 0.82f)
                    moveTo(w * 0.38f, h * 0.82f); lineTo(w * 0.18f, h * 0.82f); lineTo(w * 0.18f, h * 0.62f)
                }
                drawPath(p, color, style = line)
            }
            "\u2699" -> {
                drawCircle(color, w * 0.14f, Offset(w * 0.5f, h * 0.5f), style = line)
                drawCircle(color, w * 0.29f, Offset(w * 0.5f, h * 0.5f), style = line)
                for (i in 0 until 8) {
                    val a = i * 0.7853982f
                    val c = cos(a)
                    val s = sin(a)
                    ln(0.5f + 0.29f * c, 0.5f + 0.29f * s, 0.5f + 0.41f * c, 0.5f + 0.41f * s)
                }
            }
            "\u2605", "\u2606" -> {
                val sp = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.40f else 0.17f
                    val a = -1.5707964f + i * 0.31415927f
                    val x = w * (0.5f + r * cos(a))
                    val y = h * (0.54f + r * sin(a))
                    if (i == 0) sp.moveTo(x, y) else sp.lineTo(x, y)
                }
                sp.close()
                if (glyph == "\u2605") drawPath(sp, color)
                else drawPath(sp, color, style = line)
            }
            "\u270E", "\u270F" -> {
                val p = Path().apply {
                    moveTo(w * 0.30f, h * 0.60f)
                    lineTo(w * 0.60f, h * 0.30f)
                    lineTo(w * 0.74f, h * 0.44f)
                    lineTo(w * 0.44f, h * 0.74f)
                    lineTo(w * 0.20f, h * 0.80f)
                    close()
                }
                drawPath(p, color, style = line)
                if (glyph == "\u270F") ln(0.52f, 0.9f, 0.88f, 0.9f)
            }
            "\u2139" -> {
                drawCircle(color, w * 0.38f, Offset(w * 0.5f, h * 0.5f), style = line)
                ln(0.5f, 0.46f, 0.5f, 0.68f)
                drawCircle(color, sw * 0.7f, Offset(w * 0.5f, h * 0.33f))
            }
            "\u25B6" -> {
                val p = Path().apply {
                    moveTo(w * 0.32f, h * 0.22f)
                    lineTo(w * 0.78f, h * 0.5f)
                    lineTo(w * 0.32f, h * 0.78f)
                    close()
                }
                drawPath(p, color)
            }
            "\u27F3" -> {
                drawArc(
                    color = color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(w * 0.2f, h * 0.2f), size = Size(w * 0.6f, h * 0.6f),
                    style = line
                )
                ln(0.65f, 0.24f, 0.84f, 0.22f)
                ln(0.65f, 0.24f, 0.69f, 0.43f)
            }
            "\u2298" -> {
                drawCircle(color, w * 0.36f, Offset(w * 0.5f, h * 0.5f), style = line)
                ln(0.26f, 0.74f, 0.74f, 0.26f)
            }
            "\uD83D\uDDD1" -> {
                ln(0.2f, 0.28f, 0.8f, 0.28f)
                val handle = Path().apply {
                    moveTo(w * 0.4f, h * 0.28f); lineTo(w * 0.4f, h * 0.17f)
                    lineTo(w * 0.6f, h * 0.17f); lineTo(w * 0.6f, h * 0.28f)
                }
                drawPath(handle, color, style = line)
                val body = Path().apply {
                    moveTo(w * 0.28f, h * 0.28f); lineTo(w * 0.32f, h * 0.84f)
                    lineTo(w * 0.68f, h * 0.84f); lineTo(w * 0.72f, h * 0.28f)
                }
                drawPath(body, color, style = line)
                ln(0.43f, 0.42f, 0.43f, 0.7f)
                ln(0.57f, 0.42f, 0.57f, 0.7f)
            }
            "\u25F7" -> {
                drawCircle(color, w * 0.38f, Offset(w * 0.5f, h * 0.5f), style = line)
                ln(0.5f, 0.5f, 0.5f, 0.28f)
                ln(0.5f, 0.5f, 0.66f, 0.58f)
            }
            "\u266A" -> {
                drawCircle(color, w * 0.13f, Offset(w * 0.36f, h * 0.72f))
                ln(0.48f, 0.72f, 0.48f, 0.2f)
                ln(0.48f, 0.2f, 0.76f, 0.32f)
                ln(0.76f, 0.32f, 0.76f, 0.42f)
            }
            "\u2197" -> {
                ln(0.28f, 0.72f, 0.72f, 0.28f)
                ln(0.4f, 0.28f, 0.72f, 0.28f)
                ln(0.72f, 0.28f, 0.72f, 0.6f)
            }
            "\u2190" -> {
                ln(0.8f, 0.5f, 0.22f, 0.5f)
                val head = Path().apply {
                    moveTo(w * 0.48f, h * 0.24f)
                    lineTo(w * 0.22f, h * 0.5f)
                    lineTo(w * 0.48f, h * 0.76f)
                }
                drawPath(head, color, style = line)
            }
            "\u2630" -> {
                ln(0.2f, 0.3f, 0.8f, 0.3f)
                ln(0.2f, 0.5f, 0.8f, 0.5f)
                ln(0.2f, 0.7f, 0.8f, 0.7f)
            }
            "\u25A6" -> {
                val s = Size(w * 0.28f, h * 0.28f)
                val r = androidx.compose.ui.geometry.CornerRadius(w * 0.05f)
                drawRoundRect(color, Offset(w * 0.17f, h * 0.17f), s, r, style = line)
                drawRoundRect(color, Offset(w * 0.55f, h * 0.17f), s, r, style = line)
                drawRoundRect(color, Offset(w * 0.17f, h * 0.55f), s, r, style = line)
                drawRoundRect(color, Offset(w * 0.55f, h * 0.55f), s, r, style = line)
            }
            "\u25CE" -> {
                drawCircle(color, w * 0.36f, Offset(w * 0.5f, h * 0.5f), style = line)
                drawCircle(color, w * 0.12f, Offset(w * 0.5f, h * 0.5f))
            }
            else -> {}
        }
    }
}

@Composable
fun DzGlyph(glyph: String, color: Color, iconSize: Dp = 20.dp) {
    GlassIconSurface { RawDzGlyph(glyph, color, iconSize) }
}
