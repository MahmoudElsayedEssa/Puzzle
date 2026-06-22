package com.moe.puzzle.feature.puzzle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlockEngineTest {

    private fun engine(total: Int, initial: Set<Int> = emptySet()) =
        UnlockEngine(totalPieces = total, initial = initial)

    @Test
    fun firstUpdate_allIdsAreNewlyUnlocked() {
        val e = engine(4)
        val delta = e.update(PuzzleProgress(setOf(0, 1, 2)))
        assertEquals(setOf(0, 1, 2), delta.newlyUnlocked)
        assertFalse(delta.isComplete)
    }

    @Test
    fun secondUpdate_onlyDeltaIsNewlyUnlocked() {
        val e = engine(4)
        e.update(PuzzleProgress(setOf(0, 1)))
        val delta = e.update(PuzzleProgress(setOf(0, 1, 2)))
        assertEquals(setOf(2), delta.newlyUnlocked)
        assertFalse(delta.isComplete)
    }

    @Test
    fun sameProgressTwice_emptyDelta_noReEmission() {
        val e = engine(4)
        e.update(PuzzleProgress(setOf(0, 1)))
        val delta = e.update(PuzzleProgress(setOf(0, 1)))
        assertTrue(delta.newlyUnlocked.isEmpty())
        assertFalse(delta.isComplete)
    }

    @Test
    fun completionExactlyAtTotalSize() {
        val e = engine(3)
        e.update(PuzzleProgress(setOf(0, 1)))
        val delta = e.update(PuzzleProgress(setOf(0, 1, 2)))
        assertEquals(setOf(2), delta.newlyUnlocked)
        assertTrue(delta.isComplete)
    }

    @Test
    fun shrinkingSet_emptyDelta_nocrash() {
        val e = engine(4)
        e.update(PuzzleProgress(setOf(0, 1, 2)))
        val delta = e.update(PuzzleProgress(setOf(0, 1)))
        assertTrue(delta.newlyUnlocked.isEmpty())
        assertFalse(delta.isComplete)
    }

    @Test
    fun seededInitial_firstDeltaIsEmpty_resumePath() {
        val e = engine(4, initial = setOf(0, 1, 2))
        val delta = e.update(PuzzleProgress(setOf(0, 1, 2)))
        assertTrue(delta.newlyUnlocked.isEmpty())
        assertFalse(delta.isComplete)
    }

    @Test
    fun reset_thenNextUpdateReportsAll() {
        val e = engine(3, initial = setOf(0, 1))
        e.update(PuzzleProgress(setOf(0, 1, 2)))
        e.reset()
        val delta = e.update(PuzzleProgress(setOf(0, 1, 2)))
        assertEquals(setOf(0, 1, 2), delta.newlyUnlocked)
        assertTrue(delta.isComplete)
    }
}
