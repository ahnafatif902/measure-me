package com.measureme.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.measureme.app.R

/*
 * MeasureStudio design tokens — byte-verified against the reference build
 * (design/tokens.css and web/src/index.css @theme). Four colors, two font
 * families and one card radius; every other value in the UI is one of these
 * or a Tailwind-style opacity mix of them.
 *
 * The reference is a single fixed light theme — there is no dark variant,
 * and this port keeps it that way on purpose.
 */
object Mm {
    val Bg = Color(0xFFEFE8DC)      // page background (warm cream)
    val Surface = Color(0xFFFBF7F0) // cards, secondary buttons, inputs
    val Ink = Color(0xFF1C1712)     // text, photo frame, label outlines
    val Primary = Color(0xFFB8431F) // actions, glyph, markers, meter fill

    // Opacity mixes the reference relies on (text-ink/60, border-ink/15, …).
    val Ink80 = Ink.copy(alpha = 0.80f)
    val Ink75 = Ink.copy(alpha = 0.75f)
    val Ink70 = Ink.copy(alpha = 0.70f)
    val Ink60 = Ink.copy(alpha = 0.60f)
    val Ink30 = Ink.copy(alpha = 0.30f)
    val Ink15 = Ink.copy(alpha = 0.15f)
    val Ink10 = Ink.copy(alpha = 0.10f)
    val Ink5 = Ink.copy(alpha = 0.05f)
    val Surface80 = Surface.copy(alpha = 0.80f)
    val Surface75 = Surface.copy(alpha = 0.75f)
    val Primary90 = Primary.copy(alpha = 0.90f)

    /** The reference's disabled state: 45% opacity. */
    const val DisabledAlpha = 0.45f

    /** Card radius — the single shape token (rounded-3xl / radius-card). */
    val CardRadius = androidx.compose.ui.unit.Dp(24f)
}

/** Fraunces (display): the reference loads only the 560 and 650 instances. */
val DisplayFont = FontFamily(
    Font(R.font.fraunces_560, FontWeight.Medium),   // CSS 400–560 resolves here
    Font(R.font.fraunces_650, FontWeight.SemiBold), // CSS 600+ resolves here
)

/** Outfit (UI): exactly the three weights the reference uses: 400 / 500 / 600. */
val SansFont = FontFamily(
    Font(R.font.outfit_400, FontWeight.Normal),
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
)

/*
 * Named text styles, one per role in the reference CSS (design/app.css).
 * Line heights: text-sm is 14/20 (UI lines) or 14/22.75 (prose, leading
 * 1.625); text-base is 16/26; display styles carry the reference's leading.
 */
object MmType {
    private const val TABULAR = "tnum"

    val kicker = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, color = Mm.Ink60)
    val title = TextStyle(fontFamily = DisplayFont, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp, letterSpacing = (-0.025).em, color = Mm.Ink)
    val lede = TextStyle(fontFamily = SansFont, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp, color = Mm.Ink80)

    // Buttons
    val btnMd = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    val btnLg = TextStyle(fontFamily = SansFont, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp)

    // Photo frame
    val frameTitle = TextStyle(fontFamily = DisplayFont, fontSize = 24.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp, color = Mm.Surface)
    val frameCopy = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.75.sp, color = Mm.Surface75)

    // Cards
    val panelTitle = TextStyle(fontFamily = DisplayFont, fontSize = 24.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp, color = Mm.Ink)
    val eyebrow = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, color = Mm.Ink60)

    val bigNumber = TextStyle(fontFamily = DisplayFont, fontSize = 60.sp, fontWeight = FontWeight.SemiBold, lineHeight = 60.sp, fontFeatureSettings = TABULAR, color = Mm.Ink)
    val unit = TextStyle(fontFamily = SansFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp, color = Mm.Ink70)
    val imperial = TextStyle(fontFamily = SansFont, fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp, fontFeatureSettings = TABULAR, color = Mm.Ink80)

    val qualityLabel = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, color = Mm.Ink)
    val qualityScore = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, fontFeatureSettings = TABULAR, color = Mm.Ink70)

    val note70 = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.75.sp, color = Mm.Ink70)
    val note75 = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.75.sp, color = Mm.Ink75)
    val note80 = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.75.sp, color = Mm.Ink80)
    val body14 = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.75.sp, color = Mm.Ink)
    val formulaSym = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 22.75.sp, color = Mm.Ink)
    val formulaSub = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.75.sp, fontFeatureSettings = TABULAR, color = Mm.Ink80)

    val tileLabel = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, color = Mm.Ink60)
    val tileValue = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, fontFeatureSettings = TABULAR, color = Mm.Ink)

    val status = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, color = Mm.Ink60)
    val hint = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.75.sp, color = Mm.Ink80)
    val summary = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, color = Mm.Ink)
    val fieldText = TextStyle(fontFamily = SansFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, fontFeatureSettings = TABULAR, color = Mm.Ink)
}

private val LightColors = lightColorScheme(
    primary = Mm.Primary,
    onPrimary = Mm.Surface,
    background = Mm.Bg,
    onBackground = Mm.Ink,
    surface = Mm.Surface,
    onSurface = Mm.Ink,
    surfaceVariant = Mm.Bg,
    onSurfaceVariant = Mm.Ink70,
    outline = Mm.Ink15,
    outlineVariant = Mm.Ink10,
    error = Mm.Primary,
    onError = Mm.Surface,
)

/** The app theme: one fixed palette, exactly like the reference. */
@Composable
fun MeasureMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content,
    )
}
