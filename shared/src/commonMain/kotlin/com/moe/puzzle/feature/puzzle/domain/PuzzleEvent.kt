package com.moe.puzzle.feature.puzzle.domain

sealed interface PuzzleEvent {
    /** A piece became available in the tray (progressive unlock). */
    data class PieceUnlocked(val pieceId: Int) : PuzzleEvent

    /** A piece was correctly placed onto the board. */
    data class PiecePlaced(val pieceId: Int) : PuzzleEvent

    /** A selected piece was tapped onto the wrong slot. */
    data class WrongPlacement(val pieceId: Int, val cellId: Int) : PuzzleEvent

    data class ProgressChanged(val placed: Int, val total: Int) : PuzzleEvent
    data object PuzzleCompleted : PuzzleEvent
    data class RewardTapped(val campaignId: String) : PuzzleEvent
}
