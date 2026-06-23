package com.moe.puzzle.feature.puzzle.domain

import kotlin.math.sqrt

/**
 * Maps a desired piece count to the most square-ish rectangular grid.
 *
 * Picks the factor pair closest to a square: 4→2×2, 6→2×3, 8→2×4, 9→3×3, 12→3×4, 16→4×4.
 * Primes have no balanced factorization, so they fall back to a 1×N strip — the UI should
 * steer users toward curated counts (see [NICE_PIECE_COUNTS]) to avoid that.
 */
fun gridForCount(count: Int): GridSpec {
    require(count >= 1) { "count must be >= 1" }
    var rows = 1
    val limit = sqrt(count.toDouble()).toInt()
    for (r in 1..limit) {
        if (count % r == 0) rows = r
    }
    return GridSpec(rows = rows, cols = count / rows)
}

/** Counts that resolve to clean, near-square grids — safe to expose as UI presets. */
val NICE_PIECE_COUNTS = listOf(4, 6, 9, 12, 16)
