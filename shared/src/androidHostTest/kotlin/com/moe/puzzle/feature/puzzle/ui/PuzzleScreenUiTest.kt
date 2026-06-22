package com.moe.puzzle.feature.puzzle.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.moe.puzzle.feature.puzzle.domain.GridSpec
import com.moe.puzzle.feature.puzzle.domain.PlacementStyle
import com.moe.puzzle.feature.puzzle.domain.PuzzlePiece
import com.moe.puzzle.feature.puzzle.domain.RewardDisplay
import com.moe.puzzle.feature.puzzle.presentation.PuzzleUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests for [PuzzleCampaignScreenContent], run on the JVM via Robolectric
 * (no device needed). [GraphicsMode.Mode.NATIVE] lets Compose actually render.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Suppress("DEPRECATION") // createComposeRule v1; v2 changes dispatcher semantics — migrate later.
class PuzzleScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val grid = GridSpec(rows = 2, cols = 2)
    private val emptyPieces = persistentListOf<PuzzlePiece>()

    private fun baseState() = PuzzleUiState(
        grid = grid,
        pieces = emptyPieces,
        unlocked = persistentSetOf(),
        placed = persistentSetOf(),
        selectedPieceId = null,
        placementStyle = PlacementStyle.FADE,
        placedCount = 0,
        total = 4,
        isComplete = false,
        reward = null,
    )

    private fun partialState() = baseState().copy(
        unlocked = persistentSetOf(0, 1, 2, 3),
        placed = persistentSetOf(0, 1),
        placedCount = 2,
    )

    private fun completeState(reward: RewardDisplay? = RewardDisplay("10 GB", "Claim")) =
        baseState().copy(
            unlocked = persistentSetOf(0, 1, 2, 3),
            placed = persistentSetOf(0, 1, 2, 3),
            placedCount = 4,
            isComplete = true,
            reward = reward,
        )

    @Test
    fun lockedState_showsZeroProgress() {
        composeRule.setContent {
            PuzzleCampaignScreenContent(state = baseState(), image = null, onIntent = {})
        }
        composeRule.onNodeWithText("0 of 4 pieces placed").assertIsDisplayed()
    }

    @Test
    fun partialState_showsCorrectProgressText() {
        composeRule.setContent {
            PuzzleCampaignScreenContent(state = partialState(), image = null, onIntent = {})
        }
        composeRule.onNodeWithText("2 of 4 pieces placed").assertIsDisplayed()
    }

    @Test
    fun completeState_showsAllPiecesPlaced() {
        composeRule.setContent {
            PuzzleCampaignScreenContent(state = completeState(), image = null, onIntent = {})
        }
        composeRule.onNodeWithText("4 of 4 pieces placed").assertIsDisplayed()
    }

    @Test
    fun rewardCta_notShownWhenIncomplete() {
        composeRule.setContent {
            PuzzleCampaignScreenContent(state = baseState(), image = null, onIntent = {})
        }
        composeRule.onNodeWithText("Claim").assertDoesNotExist()
    }

    @Test
    fun rewardCta_appearsOnCompletion() {
        composeRule.setContent {
            PuzzleCampaignScreenContent(
                state = completeState(reward = RewardDisplay("10 GB", "Claim")),
                image = null,
                onIntent = {},
            )
        }
        composeRule.onNodeWithText("Claim").assertIsDisplayed()
    }

    @Test
    fun rewardCta_hasCorrectContentDescription() {
        composeRule.setContent {
            PuzzleCampaignScreenContent(
                state = completeState(reward = RewardDisplay("10 GB", "Claim")),
                image = null,
                onIntent = {},
            )
        }
        composeRule.onNodeWithContentDescription("Claim reward: 10 GB").assertIsDisplayed()
    }

    @Test
    fun rewardCta_notShownWhenCompleteButNullReward() {
        composeRule.setContent {
            PuzzleCampaignScreenContent(state = completeState(reward = null), image = null, onIntent = {})
        }
        composeRule.onNodeWithText("Claim").assertDoesNotExist()
    }

    @Test
    fun boardSemantics_hasA11yDescription() {
        composeRule.setContent {
            PuzzleCampaignScreenContent(state = partialState(), image = null, onIntent = {})
        }
        composeRule.onNodeWithContentDescription("Puzzle board. 2 of 4 pieces placed").assertIsDisplayed()
    }
}
