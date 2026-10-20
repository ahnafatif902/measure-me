package com.measureme.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmElevation
import com.measureme.app.ui.theme.MmGlass
import com.measureme.app.ui.theme.MmShapes
import com.measureme.app.ui.theme.MmSpace

/**
 * Floating glass sheet for the camera screen bottom.
 *
 * Alpha compositing over the live preview — no Haze, works on API 24+.
 * The sheet sits above the navigation bar with inset padding.
 *
 * @param content the sheet body
 */
@Composable
fun MmGlassSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                MmElevation.Sheet, MmShapes.Sheet,
                ambientColor = Mm.Shadow.copy(alpha = 0.12f),
                spotColor = Mm.Shadow.copy(alpha = 0.12f),
            )
            .clip(MmShapes.Sheet)
            .background(MmGlass.Fill)
            .border(1.dp, MmGlass.Border, MmShapes.Sheet)
            .padding(MmSpace.MD),
    ) {
        // Inner top highlight that makes the surface read as physical glass.
        Canvas(modifier = Modifier.fillMaxWidth()) {
            val r = 24.dp.toPx()
            val w = size.width
            drawLine(
                color = MmGlass.Highlight,
                start = Offset(r + 6.dp.toPx(), 1.dp.toPx()),
                end = Offset(w - r - 6.dp.toPx(), 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
        content()
    }
}
