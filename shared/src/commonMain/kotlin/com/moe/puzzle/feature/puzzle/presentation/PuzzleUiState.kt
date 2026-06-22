package com.moe.puzzle.feature.puzzle.presentation

import androidx.compose.runtime.Immutable
import com.moe.puzzle.feature.puzzle.domain.GridSpec
import com.moe.puzzle.feature.puzzle.domain.PuzzlePiece
import com.moe.puzzle.feature.puzzle.domain.RewardDisplay
import com.moe.puzzle.feature.puzzle.domain.PlacementStyle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

@Immutable
data class PuzzleUiState(
    val grid: GridSpec,
    val pieces: ImmutableList<PuzzlePiece>,
    /** Pieces available in the tray (progressive unlock). */
    val unlocked: ImmutableSet<Int>,
    /** Pieces already placed on the board. */
    val placed: ImmutableSet<Int>,
    /** The tray piece currently selected for placement, if any. */
    val selectedPieceId: Int?,
    val placementStyle: PlacementStyle,
    val placedCount: Int,
    val total: Int,
    val isComplete: Boolean,
    val reward: RewardDisplay?,
)
