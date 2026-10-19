package com.measureme.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmType

/*
 * The reference's component kit, translated to Compose:
 *   - mm-button   → MmButton (primary / secondary / ghost, md / lg)
 *   - .card       → MmCard   (surface, 24dp radius, 1dp ink/10 border, shadow-sm)
 *   - .tile/.well → MmWell   (bg-tinted 16dp-radius inset)
 *   - .meter      → MmMeter
 *   - ul.disc     → MmBullets
 *   - the header's dimension glyph → DimensionGlyph
 */

enum class MmButtonVariant { Primary, Secondary, Ghost }
enum class MmButtonSize { Md, Lg }

@Composable
fun MmButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    variant: MmButtonVariant = MmButtonVariant.Primary,
    size: MmButtonSize = MmButtonSize.Md,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    horizontalPadding: Dp? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = CircleShape

    val background = when (variant) {
        MmButtonVariant.Primary -> if (pressed && enabled) Mm.Primary90 else Mm.Primary
        MmButtonVariant.Secondary -> if (pressed && enabled) Mm.Bg else Mm.Surface
        MmButtonVariant.Ghost -> if (pressed && enabled) Mm.Ink5 else Color.Transparent
    }
    val contentColor = if (variant == MmButtonVariant.Primary) Mm.Surface else Mm.Ink
    val textStyle = if (size == MmButtonSize.Lg) MmType.btnLg else MmType.btnMd

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else Mm.DisabledAlpha)
            .heightIn(min = if (size == MmButtonSize.Lg) 48.dp else 44.dp)
            .clip(shape)
            .background(background)
            .then(
                if (variant == MmButtonVariant.Secondary) {
                    Modifier.border(1.dp, Mm.Ink15, shape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding ?: if (size == MmButtonSize.Lg) 20.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        }
        Text(label, style = textStyle, color = contentColor)
    }
}

/** The card surface: rounded-3xl, surface bg, 1px ink/10 border, shadow-sm. */
@Composable
fun MmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .shadow(1.dp, shape)
            .clip(shape)
            .background(Mm.Surface)
            .border(1.dp, Mm.Ink10, shape)
            .padding(20.dp),
        content = content,
    )
}

/** The inset well: rounded-2xl, bg tint, 12dp padding (formula + debug boxes). */
@Composable
fun MmWell(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Mm.Bg)
            .padding(12.dp),
        content = content,
    )
}

/** The confidence meter: 8dp pill, ink/10 track, primary fill. */
@Composable
fun MmMeter(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Mm.Ink10)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Mm.Primary)
        )
    }
}

/** A disc list, pl-5-ish hanging indent, same metrics as the reference's ul. */
@Composable
fun MmBullets(
    items: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = MmType.note80,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(Modifier.padding(start = 4.dp)) {
                Text("\u2022", style = style)
                Spacer(Modifier.width(10.dp))
                Text(item, style = style, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The header's dimension glyph: top hairline, centre vertical, bottom hairline. */
@Composable
fun DimensionGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.width(12.dp).height(36.dp)) {
        val stroke = 1.dp.toPx()
        drawLine(Mm.Primary, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
        drawLine(Mm.Primary, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke)
        drawLine(Mm.Primary, Offset(0f, size.height - stroke / 2f), Offset(size.width, size.height - stroke / 2f), stroke)
    }
}

/** Small helper used by screens: a row with a space-between label row. */
@Composable
fun MmLabelRow(left: @Composable RowScope.() -> Unit, right: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Row(verticalAlignment = Alignment.CenterVertically, content = left)
        Row(verticalAlignment = Alignment.CenterVertically, content = right)
    }
}
