package com.moe.puzzle.feature.puzzle.domain

/** The pieces newly added to the unlocked set this update, plus whether everything is now unlocked. */
data class UnlockDelta(val newlyUnlocked: Set<Int>, val isComplete: Boolean)

/**
 * Diffs successive unlock states so each piece is reported exactly once. Holds the previously-seen
 * set and returns only the additions — this is what keeps animations/events from re-firing when the
 * same progress is applied twice.
 */
class UnlockEngine(private val totalPieces: Int, initial: Set<Int> = emptySet()) {
    private var previous: Set<Int> = initial

    fun update(progress: PuzzleProgress): UnlockDelta {
        val now = progress.unlockedPieceIds
        val newly = now - previous
        previous = now
        return UnlockDelta(newly, now.size == totalPieces)
    }

    fun reset() {
        previous = emptySet()
    }
}
