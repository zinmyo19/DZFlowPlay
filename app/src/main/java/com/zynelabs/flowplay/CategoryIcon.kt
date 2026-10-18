package com.zynelabs.flowplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Consistent thin-line category artwork used by the dark/crimson filter chips. */
@Composable
private fun RawCategoryIcon(category: String, color: Color, iconSize: Dp = 17.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(w * .085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2),
                strokeWidth = w * .085f, cap = StrokeCap.Round)
        fun circle(x: Float, y: Float, r: Float) =
            drawCircle(color, w * r, Offset(w * x, h * y), style = stroke)
        val key = category.substringBefore(" · ").trim()
        when (key.lowercase().replace("[^a-z0-9]".toRegex(), "")) {
            "sports" -> {
                circle(.5f, .5f, .34f)
                line(.3f, .3f, .7f, .7f); line(.7f, .3f, .3f, .7f)
                line(.5f, .16f, .5f, .84f)
            }
            "movies", "movie", "film" -> {
                drawRoundRect(color, Offset(w * .13f, h * .2f), Size(w * .74f, h * .6f),
                    CornerRadius(w * .06f), style = stroke)
                line(.13f, .36f, .87f, .36f); line(.13f, .64f, .87f, .64f)
                line(.32f, .2f, .32f, .36f); line(.68f, .64f, .68f, .8f)
            }
            "news" -> {
                drawRoundRect(color, Offset(w * .13f, h * .2f), Size(w * .74f, h * .6f),
                    CornerRadius(w * .06f), style = stroke)
                line(.28f, .34f, .72f, .34f); line(.28f, .5f, .72f, .5f)
                line(.28f, .66f, .56f, .66f)
            }
            "kids" -> {
                circle(.5f, .5f, .33f)
                drawCircle(color, w * .035f, Offset(w * .39f, h * .45f))
                drawCircle(color, w * .035f, Offset(w * .61f, h * .45f))
                val smile = Path().apply { moveTo(w * .36f, h * .6f); quadraticBezierTo(w * .5f, h * .72f, w * .64f, h * .6f) }
                drawPath(smile, color, style = stroke)
            }
            "music" -> {
                line(.62f, .2f, .62f, .67f); line(.62f, .2f, .82f, .27f); line(.82f, .27f, .82f, .4f)
                drawCircle(color, w * .12f, Offset(w * .43f, h * .72f))
                drawCircle(color, w * .12f, Offset(w * .73f, h * .5f))
            }
            "documentary", "documentaries" -> {
                drawRoundRect(color, Offset(w * .18f, h * .14f), Size(w * .64f, h * .72f), CornerRadius(w * .05f), style = stroke)
                line(.32f, .35f, .68f, .35f); line(.32f, .51f, .68f, .51f); line(.32f, .67f, .58f, .67f)
            }
            "entertainment" -> {
                val star = Path()
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val r = if (i % 2 == 0) .38 else .17
                    val x = w * (.5 + r * kotlin.math.cos(a)).toFloat()
                    val y = h * (.5 + r * kotlin.math.sin(a)).toFloat()
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close(); drawPath(star, color, style = stroke)
            }
            "radio" -> {
                drawRoundRect(color, Offset(w * .16f, h * .3f), Size(w * .68f, h * .5f), CornerRadius(w * .08f), style = stroke)
                line(.5f, .3f, .63f, .14f); line(.35f, .52f, .65f, .52f)
                drawCircle(color, w * .07f, Offset(w * .36f, h * .68f)); drawCircle(color, w * .07f, Offset(w * .64f, h * .68f))
            }
            "adult", "18", "adult18" -> {
                val shield = Path().apply { moveTo(w * .5f, h * .12f); lineTo(w * .78f, h * .23f); lineTo(w * .73f, h * .65f); quadraticBezierTo(w * .5f, h * .88f, w * .27f, h * .65f); lineTo(w * .22f, h * .23f); close() }
                drawPath(shield, color, style = stroke)
                line(.37f, .43f, .63f, .43f); line(.37f, .58f, .63f, .58f)
            }
            else -> {
                drawRoundRect(color, Offset(w * .16f, h * .16f), Size(w * .68f, h * .68f), CornerRadius(w * .1f), style = stroke)
                drawCircle(color, w * .045f, Offset(w * .35f, h * .5f)); drawCircle(color, w * .045f, Offset(w * .5f, h * .5f)); drawCircle(color, w * .045f, Offset(w * .65f, h * .5f))
            }
        }
    }
}

/** Removes legacy emoji decoration without changing persistent category keys. */
fun categoryDisplayName(raw: String): String {
    val stripped = raw.replaceFirst(
        Regex("^\\s*(?:[\\uD83C\\uDDE6-\\uD83C\\uDDFF]{2}|[\\u2600-\\u27BF\\uFE0F\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+)\\s*"), ""
    ).trim()
    val category = stripped.substringBefore(" · ").trim().lowercase()
    val known = setOf("sports", "movies", "news", "kids", "music", "documentary", "entertainment", "radio", "adult", "other")
    return if (category in known) stripped else raw
}

@Composable
fun CategoryIcon(category: String, color: Color, iconSize: Dp = 17.dp) {
    GlassIconSurface { RawCategoryIcon(category, color, iconSize) }
}
