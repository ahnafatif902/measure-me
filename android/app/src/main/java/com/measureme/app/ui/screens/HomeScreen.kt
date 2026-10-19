package com.measureme.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.MeasureMeViewModel.Mode
import com.measureme.app.ui.components.MmPrimaryButton
import com.measureme.app.ui.components.MmTopBar
import com.measureme.app.ui.components.ModeCard
import com.measureme.app.ui.components.PrivacyLine
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmType

/**
 * Home (UI Spec §6.1): ruler icon + wordmark, the headline, three mode cards
 * in a radio group, a full-width Continue button, then the privacy line.
 * Headline and support sentence are the spec's exact copy (§7).
 */
@Composable
fun HomeScreen(
    mode: Mode,
    onSelectMode: (Mode) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        MmTopBar(title = "MeasureMe", leadingIcon = MmIcons.Ruler)

        Spacer(Modifier.height(36.dp))

        Text("Your height, from one photo.", style = MmType.headline)

        Spacer(Modifier.height(14.dp))

        Text(
            "Stand tall, take one photo, and get an estimate in seconds. No tape needed.",
            style = MmType.body.copy(color = Mm.Ink2),
            modifier = Modifier.widthIn(max = 300.dp),
        )

        Spacer(Modifier.height(32.dp))

        ModeCard(
            title = "Proportions",
            note = "One photo of you, head to toe. Expect a wide range.",
            icon = MmIcons.Person,
            selected = mode == Mode.PROPORTIONS,
            onClick = { onSelectMode(Mode.PROPORTIONS) },
        )
        Spacer(Modifier.height(12.dp))
        ModeCard(
            title = "Horizon",
            note = "One photo plus your camera's height. A tighter range.",
            icon = MmIcons.PhoneTilt,
            selected = mode == Mode.HORIZON,
            onClick = { onSelectMode(Mode.HORIZON) },
        )
        Spacer(Modifier.height(12.dp))
        ModeCard(
            title = "Reference",
            note = "An A4 sheet beside your feet. The tightest range.",
            icon = MmIcons.Page,
            selected = mode == Mode.REFERENCE,
            onClick = { onSelectMode(Mode.REFERENCE) },
        )

        Spacer(Modifier.height(36.dp))

        MmPrimaryButton(
            label = "Continue",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        PrivacyLine()

        Spacer(Modifier.height(28.dp))
    }
}
