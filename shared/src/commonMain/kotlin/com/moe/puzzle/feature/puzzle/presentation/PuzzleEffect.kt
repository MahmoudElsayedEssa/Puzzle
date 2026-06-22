package com.moe.puzzle.feature.puzzle.presentation

import com.moe.puzzle.feature.puzzle.domain.PuzzleEvent

sealed interface PuzzleEffect {
    data class Emit(val event: PuzzleEvent) : PuzzleEffect
}
