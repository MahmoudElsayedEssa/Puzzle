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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moe.puzzle.feature.puzzle.domain.CampaignEventSink
import com.moe.puzzle.feature.puzzle.domain.CampaignId
import com.moe.puzzle.feature.puzzle.domain.EdgeStrategy
import com.moe.puzzle.feature.puzzle.domain.NICE_PIECE_COUNTS
import com.moe.puzzle.feature.puzzle.domain.PuzzleConfig
import com.moe.puzzle.feature.puzzle.domain.PuzzleProgress
import com.moe.puzzle.feature.puzzle.domain.PlacementStyle
import com.moe.puzzle.feature.puzzle.domain.RewardDisplay
import com.moe.puzzle.feature.puzzle.domain.RewardHandler
import com.moe.puzzle.feature.puzzle.domain.gridForCount
import com.moe.puzzle.feature.puzzle.domain.slot.SLOT_PIECE_COUNTS
import com.moe.puzzle.feature.puzzle.domain.slot.slotLayoutForCount
import com.moe.puzzle.feature.puzzle.presentation.PuzzleEffect
import com.moe.puzzle.feature.puzzle.presentation.PuzzleIntent
import com.moe.puzzle.feature.puzzle.presentation.PuzzleViewModel
import com.moe.puzzle.feature.puzzle.presentation.SlotIntent
import com.moe.puzzle.feature.puzzle.presentation.SlotPuzzleViewModel
import com.moe.puzzle.feature.puzzle.ui.PuzzleCampaignScreenContent
import com.moe.puzzle.feature.puzzle.ui.SlotPuzzleScreen

/** Builds a demo config for the chosen piece count; the count resolves to a near-square grid. */
private fun demoConfig(pieceCount: Int): PuzzleConfig {
    val grid = gridForCount(pieceCount)
    return PuzzleConfig(
        campaignId = CampaignId("demo_campaign"),
        grid = grid,
        // Start with everything unlocked so the tray is immediately playable at any count.
        progress = PuzzleProgress((0 until grid.totalPieces).toSet()),
        reward = RewardDisplay(label = "10 GB Free Data", ctaText = "Claim Reward"),
        edgeStrategy = EdgeStrategy.Jigsaw,
        placementStyle = PlacementStyle.FADE_AND_POP,
    )
}

private val noOpEventSink = CampaignEventSink { /* no-op in demo */ }
private val noOpRewardHandler = RewardHandler { /* no-op in demo */ }

private enum class DemoMode { GRID, CENTER }

/** Self-contained demo: switch between the rectangular grid puzzle and the 5-piece center layout. */
@Composable
fun PuzzleDemoApp() {
    var mode by remember { mutableStateOf(DemoMode.GRID) }

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

        // ── Layout mode toggle ───────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ModeButton("Grid", mode == DemoMode.GRID, Modifier.weight(1f)) { mode = DemoMode.GRID }
            ModeButton("Center (5)", mode == DemoMode.CENTER, Modifier.weight(1f)) { mode = DemoMode.CENTER }
        }

        when (mode) {
            DemoMode.GRID -> GridPuzzleDemo()
            DemoMode.CENTER -> CenterPuzzleDemo()
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

/** The original rectangular-grid puzzle, with a user-controlled piece count. */
@Composable
private fun GridPuzzleDemo() {
    val imageProvider = rememberDemoImageProvider()

    var pieceCount by remember { mutableStateOf(NICE_PIECE_COUNTS.first()) }
    val grid = remember(pieceCount) { gridForCount(pieceCount) }

    val viewModel = viewModel(key = "grid_$pieceCount") {
        PuzzleViewModel(
            config = demoConfig(pieceCount),
            eventSink = noOpEventSink,
            rewardHandler = noOpRewardHandler,
        )
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PuzzleEffect.Emit -> println("[PuzzleDemo] grid event: ${effect.event}")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Pieces: $pieceCount  ·  ${grid.rows}×${grid.cols}",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NICE_PIECE_COUNTS.forEach { count ->
                ModeButton("$count", count == pieceCount, Modifier.weight(1f)) { pieceCount = count }
            }
        }

        PuzzleCampaignScreenContent(
            state = state,
            image = imageProvider.provide("demo"),
            onIntent = viewModel::onIntent,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        val allUnlocked = state.unlocked.size >= state.total
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.onIntent(PuzzleIntent.UnlockNext) },
                enabled = !allUnlocked,
                modifier = Modifier.weight(1f),
            ) { Text("Unlock next") }
            Button(
                onClick = { repeat(state.total - state.unlocked.size) { viewModel.onIntent(PuzzleIntent.UnlockNext) } },
                enabled = !allUnlocked,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text("Unlock all") }
            OutlinedButton(
                onClick = { viewModel.onIntent(PuzzleIntent.Reset) },
                modifier = Modifier.weight(1f),
            ) { Text("Reset") }
        }
    }
}

private fun slotLabel(n: Int): String = when (n) {
    9 -> "9 · 3×3 grid"
    else -> "$n · grid + center"
}

/** The slot-engine puzzle: knobbed jigsaw layouts including primes (5, 7, 11, 13) via center overlays. */
@Composable
private fun CenterPuzzleDemo() {
    val imageProvider = rememberDemoImageProvider()

    var pieceCount by remember { mutableStateOf(SLOT_PIECE_COUNTS.first()) }
    val layout = remember(pieceCount) { slotLayoutForCount(pieceCount) }

    val viewModel = viewModel(key = "slot_$pieceCount") {
        SlotPuzzleViewModel(
            layout = layout,
            campaignId = CampaignId("demo_center"),
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
                is PuzzleEffect.Emit -> println("[PuzzleDemo] slot event: ${effect.event}")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = slotLabel(pieceCount) + "  ·  slot engine",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SLOT_PIECE_COUNTS.forEach { count ->
                ModeButton("$count", count == pieceCount, Modifier.weight(1f)) { pieceCount = count }
            }
        }

        SlotPuzzleScreen(
            viewModel = viewModel,
            image = imageProvider.provide("demo"),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = { viewModel.onIntent(SlotIntent.Reset) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset") }
    }
}
