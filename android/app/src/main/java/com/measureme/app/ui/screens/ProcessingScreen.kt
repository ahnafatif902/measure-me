package com.measureme.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.measureme.app.ui.components.MmProgressBar
import com.measureme.app.ui.components.StepRow
import com.measureme.app.ui.components.StepState

/**
 * The processing view (PRD §5, UI Spec §6.2): progress bar and three steps —
 * find person, locate head and feet, work out the scale. Announced politely
 * to screen readers. Steps reflect real pipeline stages once they exist.
 */
@Composable
fun ProcessingScreen(step: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        MmProgressBar(progress = step / 3f)

        Spacer(Modifier.height(24.dp))

        StepRow("Find person", state = stepState(index = 1, step = step))
        Spacer(Modifier.height(16.dp))
        StepRow("Locate head and feet", state = stepState(index = 2, step = step))
        Spacer(Modifier.height(16.dp))
        StepRow("Work out the scale", state = stepState(index = 3, step = step))

        Spacer(Modifier.height(8.dp))
    }
}

private fun stepState(index: Int, step: Int): StepState = when {
    step >= index -> StepState.Done
    step == index - 1 -> StepState.Active
    else -> StepState.Pending
}
