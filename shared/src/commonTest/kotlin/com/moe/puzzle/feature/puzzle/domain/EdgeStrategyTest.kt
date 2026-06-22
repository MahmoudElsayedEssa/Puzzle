package com.moe.puzzle.feature.puzzle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EdgeStrategyTest {

    private val grid4x4 = GridSpec(rows = 4, cols = 4)
    private val seed = 42L

    // ---- Rectangular ----

    @Test
    fun rectangular_allEdgesFlat() {
        val pieces = generatePieces(grid4x4, EdgeStrategy.Rectangular, seed)
        pieces.forEach { p ->
            assertEquals(EdgeType.FLAT, p.edges.top,    "piece ${p.id} top")
            assertEquals(EdgeType.FLAT, p.edges.right,  "piece ${p.id} right")
            assertEquals(EdgeType.FLAT, p.edges.bottom, "piece ${p.id} bottom")
            assertEquals(EdgeType.FLAT, p.edges.left,   "piece ${p.id} left")
        }
    }

    // ---- Jigsaw ----

    @Test
    fun jigsaw_bordersAreFlat() {
        val pieces = generatePieces(grid4x4, EdgeStrategy.Jigsaw, seed)
        val byId = pieces.associateBy { it.id }
        // Top border: row 0
        for (col in 0 until grid4x4.cols) {
            val p = byId[grid4x4.idOf(GridCell(0, col))]!!
            assertEquals(EdgeType.FLAT, p.edges.top, "top-border piece at col=$col")
        }
        // Bottom border: last row
        for (col in 0 until grid4x4.cols) {
            val p = byId[grid4x4.idOf(GridCell(grid4x4.rows - 1, col))]!!
            assertEquals(EdgeType.FLAT, p.edges.bottom, "bottom-border piece at col=$col")
        }
        // Left border: col 0
        for (row in 0 until grid4x4.rows) {
            val p = byId[grid4x4.idOf(GridCell(row, 0))]!!
            assertEquals(EdgeType.FLAT, p.edges.left, "left-border piece at row=$row")
        }
        // Right border: last col
        for (row in 0 until grid4x4.rows) {
            val p = byId[grid4x4.idOf(GridCell(row, grid4x4.cols - 1))]!!
            assertEquals(EdgeType.FLAT, p.edges.right, "right-border piece at row=$row")
        }
    }

    @Test
    fun jigsaw_horizontalNeighborComplementarity() {
        val pieces = generatePieces(grid4x4, EdgeStrategy.Jigsaw, seed)
        val byId = pieces.associateBy { it.id }
        for (row in 0 until grid4x4.rows - 1) {
            for (col in 0 until grid4x4.cols) {
                val top = byId[grid4x4.idOf(GridCell(row, col))]!!
                val bottom = byId[grid4x4.idOf(GridCell(row + 1, col))]!!
                assertComplementary(top.edges.bottom, bottom.edges.top,
                    "row=$row/col=$col vertical shared edge")
            }
        }
    }

    @Test
    fun jigsaw_verticalNeighborComplementarity() {
        val pieces = generatePieces(grid4x4, EdgeStrategy.Jigsaw, seed)
        val byId = pieces.associateBy { it.id }
        for (row in 0 until grid4x4.rows) {
            for (col in 0 until grid4x4.cols - 1) {
                val left = byId[grid4x4.idOf(GridCell(row, col))]!!
                val right = byId[grid4x4.idOf(GridCell(row, col + 1))]!!
                assertComplementary(left.edges.right, right.edges.left,
                    "row=$row/col=$col horizontal shared edge")
            }
        }
    }

    @Test
    fun jigsaw_interiorEdgesNeverFlat() {
        val pieces = generatePieces(grid4x4, EdgeStrategy.Jigsaw, seed)
        val byId = pieces.associateBy { it.id }
        for (row in 0 until grid4x4.rows) {
            for (col in 0 until grid4x4.cols) {
                val p = byId[grid4x4.idOf(GridCell(row, col))]!!
                if (row > 0) assertNotEquals(EdgeType.FLAT, p.edges.top, "interior top row=$row/col=$col")
                if (row < grid4x4.rows - 1) assertNotEquals(EdgeType.FLAT, p.edges.bottom, "interior bottom row=$row/col=$col")
                if (col > 0) assertNotEquals(EdgeType.FLAT, p.edges.left, "interior left row=$row/col=$col")
                if (col < grid4x4.cols - 1) assertNotEquals(EdgeType.FLAT, p.edges.right, "interior right row=$row/col=$col")
            }
        }
    }

    @Test
    fun jigsaw_deterministicPerSeed() {
        val a = generatePieces(grid4x4, EdgeStrategy.Jigsaw, 99L)
        val b = generatePieces(grid4x4, EdgeStrategy.Jigsaw, 99L)
        assertEquals(a, b)
    }

    @Test
    fun jigsaw_differentSeedsProduceDifferentLayouts() {
        val a = generatePieces(grid4x4, EdgeStrategy.Jigsaw, 1L)
        val b = generatePieces(grid4x4, EdgeStrategy.Jigsaw, 2L)
        assertNotEquals(a, b)
    }

    // ---- Canonical id ----

    @Test
    fun pieceIdIsRowMajor() {
        val pieces = generatePieces(GridSpec(3, 3), EdgeStrategy.Rectangular, 0L)
        pieces.forEach { p ->
            assertEquals(p.cell.row * 3 + p.cell.col, p.id)
        }
    }

    @Test
    fun totalPiecesMatchGridSpec() {
        val grid = GridSpec(5, 6)
        assertEquals(30, generatePieces(grid, EdgeStrategy.Rectangular, 0L).size)
    }

    // ---- Helpers ----

    private fun assertComplementary(a: EdgeType, b: EdgeType, msg: String) {
        assertTrue(
            (a == EdgeType.TAB && b == EdgeType.BLANK) || (a == EdgeType.BLANK && b == EdgeType.TAB),
            "Expected TAB/BLANK complementarity at $msg but got $a/$b",
        )
    }
}
