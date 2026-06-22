package com.moe.puzzle.feature.puzzle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UseCasesTest {

    private val grid = GridSpec(2, 2)
    private val config = PuzzleConfig(
        campaignId = CampaignId("test"),
        grid = grid,
        progress = PuzzleProgress(emptySet()),
    )

    @Test
    fun generatePiecesUseCase_producesCorrectCount() {
        val pieces = GeneratePiecesUseCase()(config)
        assertEquals(4, pieces.size)
    }

    @Test
    fun isCampaignCompleteUseCase_falseWhenPartial() {
        assertFalse(IsCampaignCompleteUseCase()(PuzzleProgress(setOf(0, 1)), grid))
    }

    @Test
    fun isCampaignCompleteUseCase_trueWhenAll() {
        assertTrue(IsCampaignCompleteUseCase()(PuzzleProgress(setOf(0, 1, 2, 3)), grid))
    }

    @Test
    fun computeUnlockDeltaUseCase_delegatesToEngine() {
        val engine = UnlockEngine(totalPieces = 4)
        val uc = ComputeUnlockDeltaUseCase(engine)
        val delta = uc(PuzzleProgress(setOf(0, 1)))
        assertEquals(setOf(0, 1), delta.newlyUnlocked)
        assertFalse(delta.isComplete)
    }
}
