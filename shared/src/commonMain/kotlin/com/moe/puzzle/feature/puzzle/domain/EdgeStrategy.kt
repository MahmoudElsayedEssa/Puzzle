package com.moe.puzzle.feature.puzzle.domain

fun interface EdgeStrategy {
    fun edgesFor(cell: GridCell, grid: GridSpec, seed: Long): EdgeProfile

    object Rectangular : EdgeStrategy {
        override fun edgesFor(cell: GridCell, grid: GridSpec, seed: Long) =
            EdgeProfile(EdgeType.FLAT, EdgeType.FLAT, EdgeType.FLAT, EdgeType.FLAT)
    }

    object Jigsaw : EdgeStrategy {
        override fun edgesFor(cell: GridCell, grid: GridSpec, seed: Long): EdgeProfile {
            val rng = SeededRandom(seed)
            // Pre-generate the shared edge table so neighbors are complementary.
            // Horizontal shared edges: hEdges[row][col] is the edge between (row,col) bottom
            // and (row+1,col) top. Size: (rows-1) x cols.
            val hEdges = Array(grid.rows - 1) { row ->
                Array(grid.cols) { col ->
                    rng.nextEdgePair(seed, row, col, horizontal = true)
                }
            }
            // Vertical shared edges: vEdges[row][col] is the edge between (row,col) right
            // and (row,col+1) left. Size: rows x (cols-1).
            val vEdges = Array(grid.rows) { row ->
                Array(grid.cols - 1) { col ->
                    rng.nextEdgePair(seed, row, col, horizontal = false)
                }
            }

            val r = cell.row
            val c = cell.col
            val top = if (r == 0) EdgeType.FLAT else hEdges[r - 1][c].second   // below top neighbor
            val bottom = if (r == grid.rows - 1) EdgeType.FLAT else hEdges[r][c].first
            val left = if (c == 0) EdgeType.FLAT else vEdges[r][c - 1].second  // right of left neighbor
            val right = if (c == grid.cols - 1) EdgeType.FLAT else vEdges[r][c].first
            return EdgeProfile(top, right, bottom, left)
        }
    }
}

// Deterministic TAB/BLANK generation seeded per edge position.
private class SeededRandom(private val globalSeed: Long) {
    fun nextEdgePair(seed: Long, row: Int, col: Int, horizontal: Boolean): Pair<EdgeType, EdgeType> {
        val mix = seed xor (row.toLong() * 1_000_003L) xor (col.toLong() * 999_983L) xor
                if (horizontal) 0x5A5A5A5AL else 0xA5A5A5A5L
        val hash = lcg(mix)
        return if (hash and 1L == 0L) Pair(EdgeType.TAB, EdgeType.BLANK)
        else Pair(EdgeType.BLANK, EdgeType.TAB)
    }

    private fun lcg(seed: Long): Long {
        // Park-Miller LCG — cheap, sufficient for visual seeding
        return (seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L)
    }
}
