package com.measureme.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.measureme.app.MeasureMeViewModel.Mode
import com.measureme.app.ui.components.ModeCard
import com.measureme.app.ui.components.MmChip
import com.measureme.app.ui.components.MmPrimaryButton
import com.measureme.app.ui.components.MmTopBar
import com.measureme.app.ui.components.PrivacyLine
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.MeasureMeTheme
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType

/**
 * Home (UI Spec §6.1): ruler icon + wordmark, the headline, a supporting
 * sentence, three premium mode cards in a radio group, optional Sex/Age
 * details, then a fixed Continue + privacy footer.
 *
 * Safe areas: the root consumes the top and horizontal insets; the footer
 * consumes the bottom and horizontal insets. No hardcoded status-bar heights.
 */
@Composable
fun HomeScreen(
    mode: Mode,
    onSelectMode: (Mode) -> Unit,
    onContinue: () -> Unit,
    onSetSex: (String?) -> Unit,
    onSetAge: (Int?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mm.Paper)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MmSpace.Screen),
        ) {
            MmTopBar(
                title = "MeasureMe",
                leadingIcon = MmIcons.Ruler,
                modifier = Modifier.height(56.dp),
            )

            Spacer(Modifier.height(MmSpace.SM))

            Text("Your height, from one photo.", style = MmType.headline)

            Spacer(Modifier.height(MmSpace.XS))

            Text(
                "Stand tall, take one photo, and get an estimate in seconds. No tape needed.",
                style = MmType.subtitle,
                modifier = Modifier.widthIn(max = 300.dp),
            )

            Spacer(Modifier.height(MmSpace.MD))

            // ---- mode cards (radio group) ----------------------------------
            ModeCard(
                title = "Proportions",
                note = modeNote(Mode.PROPORTIONS),
                icon = MmIcons.Person,
                selected = mode == Mode.PROPORTIONS,
                onClick = { onSelectMode(Mode.PROPORTIONS) },
            )
            Spacer(Modifier.height(MmSpace.Gap))
            ModeCard(
                title = "Horizon",
                note = modeNote(Mode.HORIZON),
                icon = MmIcons.PhoneTilt,
                selected = mode == Mode.HORIZON,
                onClick = { onSelectMode(Mode.HORIZON) },
            )
            Spacer(Modifier.height(MmSpace.Gap))
            ModeCard(
                title = "Reference",
                note = modeNote(Mode.REFERENCE),
                icon = MmIcons.Page,
                selected = mode == Mode.REFERENCE,
                onClick = { onSelectMode(Mode.REFERENCE) },
            )

            Spacer(Modifier.height(MmSpace.MD))

            OptionalDetails(onSetSex = onSetSex, onSetAge = onSetAge)

            Spacer(Modifier.height(MmSpace.MD))
        }

        // ---- fixed footer: background first, then bottom insets, then
        // content padding. Continue is always fully visible. ----------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mm.Paper)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(horizontal = MmSpace.Screen, vertical = MmSpace.SM),
            verticalArrangement = Arrangement.spacedBy(MmSpace.Gap),
        ) {
            MmPrimaryButton(
                label = "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
            PrivacyLine()
        }
    }
}

/** Sex / age hints, collapsed by default. Stored on-device, not used by the
 *  current math (approved scope). */
@Composable
private fun OptionalDetails(
    onSetSex: (String?) -> Unit,
    onSetAge: (Int?) -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    val sexState = remember { mutableStateOf<Set<String>>(emptySet()) }
    val ageTextState = remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetails = !showDetails },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Optional details", style = MmType.label)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (showDetails) "▲" else "▼",
                style = MmType.label.copy(color = Mm.Ink2),
                modifier = Modifier.size(20.dp),
            )
        }

        if (showDetails) {
            Spacer(Modifier.height(MmSpace.XS))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Mm.Inset)
                    .padding(MmSpace.SM),
            ) {
                Text(text = "Sex", style = MmType.label)
                Spacer(Modifier.height(MmSpace.XS))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val sexes = listOf("Female", "Male", "Prefer not to say")
                    sexes.forEachIndexed { index, s ->
                        val isSelected = s in sexState.value
                        MmChip(
                            label = s,
                            selected = isSelected,
                            onClick = {
                                val newSex = if (isSelected) emptySet<String>() else setOf(s)
                                sexState.value = newSex
                                onSetSex(if (s == "Prefer not to say") null else s)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (index < sexes.lastIndex) Spacer(Modifier.width(MmSpace.XS))
                    }
                }
                Spacer(Modifier.height(MmSpace.Gap))
                Text(text = "Age (years)", style = MmType.label)
                Spacer(Modifier.height(MmSpace.XS))
                TextField(
                    value = ageTextState.value,
                    onValueChange = {
                        ageTextState.value = it.filter { ch -> ch.isDigit() }.take(3)
                    },
                    label = { Text("Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )
                val ageVal = ageTextState.value.toIntOrNull()
                if (ageTextState.value.isNotEmpty() && (ageVal == null || ageVal !in 1..120)) {
                    Spacer(Modifier.height(MmSpace.XXS))
                    Text(
                        text = "Enter an age from 1 to 120.",
                        style = MmType.caption.copy(color = Mm.Accent),
                    )
                }
                Spacer(Modifier.height(MmSpace.XS))
                Text(
                    text = "Saved on your phone. Not used in this estimate yet.",
                    style = MmType.caption,
                )
            }

            LaunchedEffect(sexState.value, ageTextState.value) {
                val enteredAge = ageTextState.value.toIntOrNull()
                val finalAge = if (enteredAge == null || enteredAge !in 1..120) null else enteredAge
                val isPreferNotToSay = "Prefer not to say" in sexState.value
                val finalSex = if (isPreferNotToSay) null else sexState.value.firstOrNull()
                onSetSex(finalSex)
                onSetAge(finalAge)
            }
        }
    }
}

/** Short, honest range copy — each note fits two lines at font scale 1.3. */
private fun modeNote(mode: Mode): String = when (mode) {
    Mode.PROPORTIONS -> "Just a photo. Widest range."
    Mode.HORIZON -> "Photo plus camera height. Tighter."
    Mode.REFERENCE -> "Photo plus an A4 sheet. Tightest."
}

@Preview(
    showSystemUi = true,
    name = "Home — 3 button / font 1.0",
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun HomePreview() {
    MeasureMeTheme {
        HomeScreen(
            mode = Mode.REFERENCE,
            onSelectMode = {},
            onContinue = {},
            onSetSex = {},
            onSetAge = {},
        )
    }
}
