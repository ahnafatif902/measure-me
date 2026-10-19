package com.measureme.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.measureme.app.R

/*
 * MeasureMe design tokens — UI Design Specification v1.0, sections 2–4.
 * One fixed light theme: paper, surface, ink, accent. No dark variant by design.
 * Do not introduce lighter greys for text (spec §2: they fail AA).
 */
object Mm {
    val Paper = Color(0xFFF4F1EA)   // page background
    val Surface = Color(0xFFFBF8F2) // cards, mode cards, secondary buttons
    val Ink = Color(0xFF231F1B)     // primary text
    val Ink2 = Color(0xFF5C544B)    // secondary text
    val Accent = Color(0xFFB4451C)  // primary button, check marks, progress
    val OnAccent = Color(0xFFFFFFFF)
    val AccentPress = Color(0xFF983A17)

    /** Hairline borders (decorative) — rgba(35,31,27,0.10). */
    val Line = Color(0x1A231F1B)

    /** Unselected radio outline — rgba(35,31,27,0.18). */
    val LineStrong = Color(0x2E231F1B)

    /** Recessed surfaces (viewfinder, stat tiles, progress track): paper at ~ink 4%. */
    val Inset = Color(0xFFECE9E2)

    /** Ambient card shadow colour — rgba(35,31,27,0.18). */
    val Shadow = Color(0x2E231F1B)

    /** Disabled controls: 40% opacity (spec §5). */
    const val DisabledAlpha = 0.40f
}

/** Radii from the spec: viewfinder 28, card and mode card 24, button and tile 16. */
object MmShapes {
    val Viewfinder: Shape = RoundedCornerShape(28.dp)
    val Card: Shape = RoundedCornerShape(24.dp)
    val Button: Shape = RoundedCornerShape(16.dp)
    val Tile: Shape = RoundedCornerShape(16.dp)
    val Progress: Shape = RoundedCornerShape(percent = 50)
}

/*
 * The bundled font instances (static TTFs generated from the variable fonts):
 *  - Fraunces 400 at optical size 88 — the one big number
 *  - Fraunces 500 at optical size 40 — the home headline
 *  - Fraunces 500 at optical size 18 — wordmark and titles
 *  - Inter 400 / 500 / 600 — everything else
 */
val FrauncesNumber = FontFamily(Font(R.font.fraunces_400, FontWeight.Normal))
val FrauncesHeadline = FontFamily(Font(R.font.fraunces_500, FontWeight.Medium))
val FrauncesText = FontFamily(Font(R.font.fraunces_500_text, FontWeight.Medium))
val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
)

/** Named text styles — one per role in the spec's typography table (§3). */
object MmType {
    private const val TABULAR = "tnum"

    /** Home headline — Fraunces 40 / 42, weight 500, tracking -0.02em. */
    val headline = TextStyle(
        fontFamily = FrauncesHeadline, fontSize = 40.sp, lineHeight = 42.sp,
        fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, color = Mm.Ink,
    )

    /** Result number — Fraunces 88 / 88, weight 400, tabular figures. */
    val resultNumber = TextStyle(
        fontFamily = FrauncesNumber, fontSize = 88.sp, lineHeight = 88.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR, color = Mm.Ink,
    )

    /** Wordmark and screen titles — Fraunces 18 / 24, weight 500. */
    val wordmark = TextStyle(
        fontFamily = FrauncesText, fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, color = Mm.Ink,
    )

    /** Body — Inter 15 / 22, weight 400. */
    val body = TextStyle(
        fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, color = Mm.Ink,
    )

    /** Secondary body — Inter 14 / 20, weight 400. */
    val body2 = TextStyle(
        fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, color = Mm.Ink2,
    )

    /** Caption — Inter 13 / 18 (privacy line). */
    val caption = TextStyle(
        fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = Mm.Ink2,
    )

    /** Label — Inter 13 / 18, weight 500. Sentence case only. */
    val label = TextStyle(
        fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Medium, color = Mm.Ink2,
    )

    /** Buttons — Inter 15, weight 500, tracking -0.005em. */
    val button = TextStyle(
        fontFamily = Inter, fontSize = 15.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium, letterSpacing = (-0.005).em,
    )

    /** Unit beside the result number — small, Inter. */
    val unit = TextStyle(
        fontFamily = Inter, fontSize = 20.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Medium, color = Mm.Ink2,
    )

    /** Imperial + range line under the result number — tabular so digits align. */
    val resultLine = TextStyle(
        fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp,
        fontFeatureSettings = TABULAR, color = Mm.Ink2,
    )

    /** Quality row score on the right — Inter 14, ink-2. */
    val qualityScore = TextStyle(
        fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp,
        fontFeatureSettings = TABULAR, color = Mm.Ink2,
    )

    /** Stat tile value — tabular. */
    val statValue = TextStyle(
        fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR, color = Mm.Ink,
    )
}

/** The app theme: one fixed palette, no default Material colours. */
@Composable
fun MeasureMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Mm.Accent,
            onPrimary = Mm.OnAccent,
            background = Mm.Paper,
            onBackground = Mm.Ink,
            surface = Mm.Surface,
            onSurface = Mm.Ink,
            surfaceVariant = Mm.Inset,
            onSurfaceVariant = Mm.Ink2,
            outline = Mm.LineStrong,
            outlineVariant = Mm.Line,
            error = Mm.Accent,
            onError = Mm.OnAccent,
        ),
        typography = Typography(),
        content = content,
    )
}
