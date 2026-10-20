package com.measureme.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.MeasureMeViewModel.Mode
import com.measureme.app.ui.components.MmChip
import com.measureme.app.ui.components.MmPrimaryButton
import com.measureme.app.ui.components.MmSegmentedControl
import com.measureme.app.ui.components.MmTopBar
import com.measureme.app.ui.components.PrivacyLine
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType

/**
 * Home (UI Spec §6.1): ruler icon + wordmark, the headline, a contextual
 * segmented control replacing the mode cards, a one-line mode context note,
 * optional Sex/Age details, Continue, then the privacy line.
 * Headline and support sentence are the spec's exact copy (§7).
 *
 * All spacing follows the 8 pt grid.
 */
@Composable
fun HomeScreen(
    mode: Mode,
    onSelectMode: (Mode) -> Unit,
    onContinue: () -> Unit,
    onSetSex: (String?) -> Unit,
    onSetAge: (Int?) -> Unit,
) {
    val showDetailsState = remember { mutableStateOf(false) }
    var showDetails by showDetailsState
    val sexState = remember { mutableStateOf<Set<String>>(emptySet<String>()) }
    val ageTextState = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MmSpace.MD),
    ) {
        Spacer(Modifier.height(MmSpace.SM))

        MmTopBar(title = "MeasureMe", leadingIcon = MmIcons.Ruler)

        Spacer(Modifier.height(MmSpace.LG))

        Text("Your height, from one photo.", style = MmType.headline)

        Spacer(Modifier.height(MmSpace.SM))

        Text(
            "Stand tall, take one photo, and get an estimate in seconds. No tape needed.",
            style = MmType.body.copy(color = Mm.Ink2),
            modifier = Modifier.widthIn(max = 300.dp),
        )

        Spacer(Modifier.height(MmSpace.LG))

        // ---- contextual segmented control ----------------------------------
        MmSegmentedControl(
            options = listOf(Mode.PROPORTIONS, Mode.HORIZON, Mode.REFERENCE),
            selected = mode,
            onSelect = onSelectMode,
            labelFor = { it.label },
        )

        Spacer(Modifier.height(MmSpace.SM))

        // mode context: what this mode needs and how accurate it typically is
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (mode) {
                Mode.PROPORTIONS -> Icon(
                    MmIcons.Person, contentDescription = null,
                    tint = Mm.Ink2, modifier = Modifier.size(20.dp),
                )
                Mode.HORIZON -> Icon(
                    MmIcons.PhoneTilt, contentDescription = null,
                    tint = Mm.Ink2, modifier = Modifier.size(20.dp),
                )
                Mode.REFERENCE -> Icon(
                    MmIcons.Page, contentDescription = null,
                    tint = Mm.Ink2, modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(MmSpace.XS))
            Text(
                text = modeNote(mode),
                style = MmType.body2,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(MmSpace.MD))

        // ---- optional Sex / Age details -----------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetails = !showDetails },
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
            Spacer(Modifier.height(MmSpace.SM))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Mm.Inset)
                    .padding(MmSpace.SM),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Sex", style = MmType.label)
                    Spacer(Modifier.weight(1f))
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
                }
                Spacer(Modifier.height(MmSpace.SM))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Age (years)", style = MmType.label)
                    Spacer(Modifier.weight(1f))
                    TextField(
                        value = ageTextState.value,
                        onValueChange = {
                            ageTextState.value = it.filter { it.isDigit() }.take(3)
                        },
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                    if (ageTextState.value.isNotEmpty()) {
                        Spacer(Modifier.width(MmSpace.XS))
                        val ageVal = ageTextState.value.toIntOrNull()
                        val invalid = ageVal == null || ageVal !in 1..120
                        if (invalid) {
                            Text(
                                text = "1-120",
                                style = MmType.caption.copy(color = Mm.Accent),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(MmSpace.XS))
                Text(
                    text = "Saved on your phone. Not used in this estimate yet.",
                    style = MmType.caption,
                )
            }
            // Persist on change
            LaunchedEffect(sexState.value, ageTextState.value) {
                val ageVal = ageTextState.value.toIntOrNull()
                val finalAge = if (ageVal == null || ageVal !in 1..120) null else ageVal
                val isPreferNotToSay = "Prefer not to say" in sexState.value
                val finalSex = if (isPreferNotToSay) null else sexState.value.firstOrNull()
                onSetSex(finalSex)
                onSetAge(finalAge)
            }
        }

        Spacer(Modifier.height(MmSpace.LG))

        MmPrimaryButton(
            label = "Continue",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(MmSpace.SM))

        PrivacyLine()

        Spacer(Modifier.height(MmSpace.LG))
    }
}

/** What each mode needs and how accurate it typically is (PRD §5 content). */
private fun modeNote(mode: Mode): String = when (mode) {
    Mode.PROPORTIONS -> "One photo of you, head to toe. Expect a wide range."
    Mode.HORIZON -> "One photo plus your camera's height. A tighter range."
    Mode.REFERENCE -> "An A4 sheet beside your feet. The tightest range."
}
