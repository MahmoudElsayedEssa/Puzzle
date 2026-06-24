package com.moe.puzzle.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moe.puzzle.feature.puzzle.domain.CampaignEventSink
import com.moe.puzzle.feature.puzzle.domain.CampaignId
import com.moe.puzzle.feature.puzzle.domain.PlacementStyle
import com.moe.puzzle.feature.puzzle.domain.RewardDisplay
import com.moe.puzzle.feature.puzzle.domain.RewardHandler
import com.moe.puzzle.feature.puzzle.domain.slot.MAX_PIECE_COUNT
import com.moe.puzzle.feature.puzzle.domain.slot.MIN_PIECE_COUNT
import com.moe.puzzle.feature.puzzle.domain.slot.SLOT_PIECE_COUNTS
import com.moe.puzzle.feature.puzzle.domain.slot.slotLayoutForCount
import com.moe.puzzle.feature.puzzle.presentation.PuzzleEffect
import com.moe.puzzle.feature.puzzle.presentation.SlotIntent
import com.moe.puzzle.feature.puzzle.presentation.SlotPuzzleViewModel
import com.moe.puzzle.feature.puzzle.ui.SlotPuzzleScreen

private val noOpEventSink = CampaignEventSink { /* no-op in demo */ }
private val noOpRewardHandler = RewardHandler { /* no-op in demo */ }

/**
 * Demo: the user picks an image from their device and types any piece count; the slot engine builds
 * a knobbed jigsaw (a grid for composite counts, grid + centered overlay pieces otherwise).
 */
@Composable
fun PuzzleDemoApp() {
    // Device image picking (Android); falls back to the bundled demo image until the user picks one.
    val picker = rememberImagePicker()
    val demoImage = rememberDemoImageProvider().provide("demo")
    val image = picker.image ?: demoImage

    var countText by remember { mutableStateOf(SLOT_PIECE_COUNTS.first().toString()) }
    val typed = countText.toIntOrNull()
    val pieceCount = (typed ?: SLOT_PIECE_COUNTS.first()).coerceIn(MIN_PIECE_COUNT, MAX_PIECE_COUNT)
    val layout = remember(pieceCount) { slotLayoutForCount(pieceCount) }

    val viewModel = viewModel(key = "slot_$pieceCount") {
        SlotPuzzleViewModel(
            layout = layout,
            campaignId = CampaignId("demo_campaign"),
            // Start with everything unlocked so all pieces are immediately in the tray.
            initialUnlocked = (0 until layout.totalPieces).toSet(),
            placementStyle = PlacementStyle.FADE_AND_POP,
            reward = RewardDisplay(label = "10 GB Free Data", ctaText = "Claim Reward"),
            eventSink = noOpEventSink,
            rewardHandler = noOpRewardHandler,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PuzzleEffect.Emit -> println("[PuzzleDemo] event: ${effect.event}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Puzzle Demo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        // ── Image source ─────────────────────────────────────────────────────
        Button(onClick = { picker.pick() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (picker.image != null) "Change image" else "Choose image from device")
        }

        // ── Piece count (type any number) ────────────────────────────────────
        OutlinedTextField(
            value = countText,
            onValueChange = { input -> countText = input.filter { it.isDigit() }.take(2) },
            label = { Text("Number of pieces ($MIN_PIECE_COUNT–$MAX_PIECE_COUNT)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                Text("Building $pieceCount pieces${if (typed != null && typed != pieceCount) " (clamped)" else ""}")
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Quick presets.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SLOT_PIECE_COUNTS.forEach { count ->
                val selected = count == pieceCount
                if (selected) {
                    Button(onClick = { countText = count.toString() }, modifier = Modifier.weight(1f)) {
                        Text("$count")
                    }
                } else {
                    OutlinedButton(onClick = { countText = count.toString() }, modifier = Modifier.weight(1f)) {
                        Text("$count")
                    }
                }
            }
        }

        SlotPuzzleScreen(
            viewModel = viewModel,
            image = image,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = { viewModel.onIntent(SlotIntent.Reset) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset") }
    }
}
