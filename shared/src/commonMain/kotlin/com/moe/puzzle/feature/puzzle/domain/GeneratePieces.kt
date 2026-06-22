package com.moe.puzzle.feature.puzzle.domain

fun generatePieces(
    grid: GridSpec,
    strategy: EdgeStrategy,
    seed: Long,
): List<PuzzlePiece> = buildList {
    for (row in 0 until grid.rows) {
        for (col in 0 until grid.cols) {
            val cell = GridCell(row, col)
            val id = grid.idOf(cell)
            add(PuzzlePiece(id = id, cell = cell, edges = strategy.edgesFor(cell, grid, seed)))
        }
    }
}
