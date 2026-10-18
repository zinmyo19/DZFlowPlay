package com.zynelabs.flowplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private object GlassCapsuleMarker : Modifier.Element

/** Shared translucent capsule decoration. Existing click/focus handlers remain authoritative. */
internal fun Modifier.glassCapsule(
    selected: Boolean = false,
    enabled: Boolean = true,
    corner: Dp = 24.dp
): Modifier = this.then(GlassCapsuleMarker).composed {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val active = enabled && (focused || pressed)
    val shape = RoundedCornerShape(corner)
    val gold = Color(0xFFFFCF66)
    val edge = when {
        !enabled -> Color.White.copy(alpha = 0.12f)
        active -> gold
        selected -> Accent.copy(alpha = 0.92f)
        else -> Color.White.copy(alpha = 0.40f)
    }
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .pointerInput(enabled) {
            if (enabled) awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                try {
                    // Observe without consuming, so clicks, long presses and scroll still work.
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                } finally {
                    pressed = false
                }
            }
        }
        .clip(shape)
        .background(Color(0xFF101827).copy(alpha = when {
            !enabled -> 0.22f
            active -> 0.66f
            else -> 0.46f
        }))
        .background(Brush.verticalGradient(listOf(
            if (selected) Accent.copy(alpha = 0.20f) else Color.White.copy(alpha = if (active) 0.20f else 0.14f),
            Color.White.copy(alpha = 0.025f)
        )))
        .border(if (active) 1.2.dp else 0.6.dp,
            Brush.verticalGradient(listOf(edge, edge.copy(alpha = edge.alpha * 0.42f))), shape)
}

/** Dialog actions use the same glass finish as the rest of the app. */
@Composable
internal fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).padding(vertical = 6.dp)
            .height(36.dp).glassCapsule(enabled = enabled),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Ink, disabledContentColor = Dim),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        content = { GlassContent { content() } }
    )
}

/** Reuse a parent capsule for its label/icon instead of nesting another capsule. */
internal val LocalGlassContent = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
internal fun GlassContent(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalGlassContent provides true, content = content)
}

/** Material-compatible text entry point used throughout app screens. */
@Composable
internal fun GlassText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    val alreadyFramed = modifier.foldIn(false) { found, element -> found || element === GlassCapsuleMarker }
    val textModifier = if (LocalGlassContent.current || alreadyFramed) modifier else
            modifier.glassCapsule(corner = 14.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    androidx.compose.material3.Text(
        text = text, modifier = textModifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
        maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}

/** Decorative icon frames do not become focusable or intercept actions. */
@Composable
internal fun GlassIconSurface(content: @Composable () -> Unit) {
    if (LocalGlassContent.current) content()
    else androidx.compose.foundation.layout.Box(
        Modifier.glassCapsule(corner = 14.dp).padding(2.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) { GlassContent(content) }
}
