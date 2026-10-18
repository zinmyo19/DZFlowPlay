package com.zynelabs.flowplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text as PlainText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared drawer rendering only: raw category keys and filtering stay untouched. */
@Composable
internal fun DrawerCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassContent {
        Box(
            Modifier.heightIn(min = 48.dp)
                .glassCapsule(selected = selected, corner = 20.dp)
                .tvFocus(corner = 20.dp)
                .focusable()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val display = categoryDisplayName(label)
            val color = if (selected) Accent else Dim
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(display.substringBefore(" · "), color, 14.dp)
                Spacer(Modifier.width(4.dp))
                PlainText(
                    display,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = Shadow(
                        color = Color(0xCC000000), offset = Offset(1f, 1f), blurRadius = 4f
                    ))
                )
            }
        }
    }
}
