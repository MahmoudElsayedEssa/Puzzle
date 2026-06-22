package com.moe.puzzle.feature.puzzle.presentation

import com.moe.puzzle.feature.puzzle.domain.GridCell

sealed interface PuzzleIntent {
    /** Progressive unlock: make the next locked piece available in the tray. */
    data object UnlockNext : PuzzleIntent

    /** Tap a tray piece to select it (tapping the selected piece again deselects). */
    data class SelectPiece(val id: Int) : PuzzleIntent

    /** Tap a board slot to place the currently selected piece (tap-to-place flow). */
    data class PlaceAt(val cell: GridCell) : PuzzleIntent

    /** Place a specific piece at a slot, independent of selection (drag-and-drop flow). */
    data class PlacePiece(val id: Int, val cell: GridCell) : PuzzleIntent

    data object Reset : PuzzleIntent
    data object RewardTapped : PuzzleIntent
}
