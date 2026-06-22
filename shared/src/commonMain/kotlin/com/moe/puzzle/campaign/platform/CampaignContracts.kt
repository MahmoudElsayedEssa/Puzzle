package com.moe.puzzle.campaign.platform

import androidx.compose.runtime.Composable
import com.moe.puzzle.feature.puzzle.domain.AnalyticsSink
import com.moe.puzzle.feature.puzzle.domain.CampaignEventSink
import com.moe.puzzle.feature.puzzle.domain.ImageProvider
import com.moe.puzzle.feature.puzzle.domain.RewardHandler

enum class GameType { PUZZLE, SCRATCH, SPIN, TREASURE }

interface CampaignServices {
    val images: ImageProvider
    val analytics: AnalyticsSink
    val rewards: RewardHandler
    val events: CampaignEventSink
}

interface CampaignGame {
    val type: GameType

    @Composable
    fun Content(services: CampaignServices)
}
