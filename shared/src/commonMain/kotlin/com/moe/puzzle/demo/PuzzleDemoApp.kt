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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moe.puzzle.feature.puzzle.domain.CampaignEventSink
import com.moe.puzzle.feature.puzzle.domain.CampaignId
import com.moe.puzzle.feature.puzzle.domain.EdgeStrategy
import com.moe.puzzle.feature.puzzle.domain.GridSpec
import com.moe.puzzle.feature.puzzle.domain.PuzzleConfig
import com.moe.puzzle.feature.puzzle.domain.PuzzleProgress
import com.moe.puzzle.feature.puzzle.domain.PlacementStyle
import com.moe.puzzle.feature.puzzle.domain.RewardDisplay
import com.moe.puzzle.feature.puzzle.domain.RewardHandler
import com.moe.puzzle.feature.puzzle.presentation.PuzzleEffect
import com.moe.puzzle.feature.puzzle.presentation.PuzzleIntent
import com.moe.puzzle.feature.puzzle.presentation.PuzzleViewModel
import com.moe.puzzle.feature.puzzle.ui.PuzzleCampaignScreenContent

private val demoConfig = PuzzleConfig(
    campaignId = CampaignId("demo_campaign"),
    grid = GridSpec(rows = 2, cols = 2),
    // Start with the first row unlocked so the tray is immediately playable.
    progress = PuzzleProgress((0 until 3).toSet()),
    reward = RewardDisplay(label = "10 GB Free Data", ctaText = "Claim Reward"),
    edgeStrategy = EdgeStrategy.Jigsaw,
    placementStyle = PlacementStyle.FADE_AND_POP,
)

private val noOpEventSink = CampaignEventSink { /* no-op in demo */ }
private val noOpRewardHandler = RewardHandler { /* no-op in demo */ }

/**
 * Self-contained demo wiring [PuzzleViewModel] with no-op ports.
 * Controls below the puzzle drive progressive unlock + reset.
 */
@Composable
fun PuzzleDemoApp() {
    val imageProvider = rememberDemoImageProvider()

    val viewModel = viewModel {
        PuzzleViewModel(
            config = demoConfig,
            eventSink = noOpEventSink,
            rewardHandler = noOpRewardHandler,
        )
    }

    val state by viewModel.state.collectAsState()

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

        PuzzleCampaignScreenContent(
            state = state,
            image = imageProvider.provide("demo"),
            onIntent = viewModel::onIntent,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        // Demo controls — progressive unlock.
        val allUnlocked = state.unlocked.size >= state.total
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = { viewModel.onIntent(PuzzleIntent.UnlockNext) },
                enabled = !allUnlocked,
                modifier = Modifier.weight(1f),
            ) { Text("Unlock next") }

            Button(
                onClick = {
                    repeat(state.total - state.unlocked.size) {
                        viewModel.onIntent(PuzzleIntent.UnlockNext)
                    }
                },
                enabled = !allUnlocked,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) { Text("Unlock all") }

            OutlinedButton(
                onClick = { viewModel.onIntent(PuzzleIntent.Reset) },
                modifier = Modifier.weight(1f),
            ) { Text("Reset") }
        }
    }
}
