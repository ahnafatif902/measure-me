package com.measureme.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmElevation
import com.measureme.app.ui.theme.MmGlass
import com.measureme.app.ui.theme.MmShapes
import com.measureme.app.ui.theme.MmSpace

/**
 * Floating sheet for the camera bottom.
 *
 * Background and shape are applied BEFORE the inset padding, so the fill
 * extends under the navigation bar while the content stays above it. The
 * top-corner highlight is a rounded inset border (see [mmTopHighlight]), not
 * a straight line, so it never cuts across the radius.
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
            .background(Mm.Sheet)
            .border(1.dp, MmGlass.Border, MmShapes.Sheet)
            .mmTopHighlight(28.dp, alpha = 0.55f)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                ),
            )
            .padding(MmSpace.Screen),
        content = content,
    )
}
