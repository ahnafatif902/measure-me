package com.measureme.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmShapes
import com.measureme.app.ui.theme.MmType
import com.measureme.app.ui.theme.MmMotion

/**
 * A sleek segmented control — replaces the Home mode cards.
 *
 * Selection is signalled by border weight + text weight, never colour alone.
 * The selection pill slides with [MmMotion.Fluid]; reduced motion snaps it.
 *
 * @param options the selectable values
 * @param selected the currently selected value
 * @param onSelect called when the user taps a different segment
 * @param labelFor display label for each option
 */
@Composable
fun <T : Any> MmSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
) {
    require(options.isNotEmpty()) { "Segmented control needs at least one option" }
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()
    val selectedIndex = options.indexOf(selected)
    require(selectedIndex >= 0) { "Selected value not found in options" }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val segmentWidth = if (maxWidth != Dp.Unspecified) maxWidth / options.size else Dp.Unspecified
        require(segmentWidth != Dp.Unspecified) { "Segmented control needs a finite width" }

        val pillOffsetPx by animateFloatAsState(
            targetValue = with(density) { (segmentWidth * selectedIndex.toFloat()).toPx().toFloat() },
            animationSpec = if (reduced) snap() else MmMotion.Fluid,
            label = "segmentPill",
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            // Selection pill behind the row — surface fill + accent border.
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .height(44.dp)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationX = pillOffsetPx }
                    .background(Mm.Surface)
                    .border(1.dp, Mm.Accent, RoundedCornerShape(16.dp))
                    .shadow(
                        4.dp, RoundedCornerShape(16.dp),
                        ambientColor = Mm.Shadow.copy(alpha = 0.08f),
                        spotColor = Mm.Shadow.copy(alpha = 0.08f),
                    ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    val label = labelFor(option)
                    SegmentItem(
                        label = label,
                        selected = isSelected,
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .semantics {
                                contentDescription = label
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (rememberReducedMotion()) snap() else MmMotion.Press,
        label = "segmentPress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Mm.Surface else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                if (selected) 1.dp else 1.dp,
                if (selected) Mm.Accent else Mm.Line,
                RoundedCornerShape(16.dp),
            ),
    ) {
        androidx.compose.material3.Text(
            text = label,
            style = if (selected) MmType.segmentSelected else MmType.segment,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
