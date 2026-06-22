package com.moe.puzzle.feature.puzzle.domain

import androidx.compose.ui.graphics.ImageBitmap

fun interface ImageProvider {
    /** Returns null while the image is loading; board renders ghosts meanwhile. */
    fun provide(imageRef: String): ImageBitmap?
}

fun interface AnalyticsSink {
    fun track(name: String, props: Map<String, Any?>)
}

fun interface CampaignEventSink {
    fun emit(event: PuzzleEvent)
}

fun interface RewardHandler {
    fun onCompleted(campaignId: CampaignId)
}
