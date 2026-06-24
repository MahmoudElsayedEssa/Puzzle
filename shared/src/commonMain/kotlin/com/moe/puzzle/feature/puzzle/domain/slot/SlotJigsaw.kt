package com.moe.puzzle.feature.puzzle.domain.slot

import kotlin.math.hypot

/**
 * Polyomino jigsaw generator — turns a fine-grid piece assignment into knobbed [PieceSlot]s.
 *
 * Every layout is described by assigning each fine cell to a piece id ([PieceMap]). The generator
 * traces each piece's outline; an outline edge shared with another piece gets a classic jigsaw knob
 * (the same silhouette used by the grid puzzle), and edges on the board border stay flat. A shared
 * edge is cut once and rendered identically from both sides — one piece sees a TAB, its neighbor the
 * matching BLANK — so pieces interlock seamlessly by construction.
 *
 * This single engine produces the grid, the 2×2 + center "5-piece", and the prime layouts
 * (7 = 2×3 + center, 11 = 3×3 + 2, 13 = 3×4 + 1) — the only thing that changes is the assignment.
 */

private const val DEFAULT_SEED = 0x5EEDL

// ── Public layouts ────────────────────────────────────────────────────────────

/** The 2×2 grid + 1 center piece = 5 pieces (ids 0..3 corners, 4 center). */
fun centerFiveLayout(seed: Long = DEFAULT_SEED): SlotLayout =
    gridPlusOverlaysLayout(baseRows = 2, baseCols = 2, overlays = listOf(1 to 1), seed = seed)

/** Lower / upper bounds for a user-typed piece count. */
const val MIN_PIECE_COUNT = 2
const val MAX_PIECE_COUNT = 49

/**
 * Maps *any* desired piece count to a knobbed layout. Composite, near-square counts are a plain grid;
 * other counts (primes included) are a base grid plus center pieces (the "grid + center overlay"
 * family). The count is clamped to [[MIN_PIECE_COUNT], [MAX_PIECE_COUNT]].
 */
fun slotLayoutForCount(n: Int, seed: Long = DEFAULT_SEED): SlotLayout {
    val plan = planFor(n.coerceIn(MIN_PIECE_COUNT, MAX_PIECE_COUNT))
    return gridPlusOverlaysLayout(plan.rows, plan.cols, plan.overlays, seed)
}

/** Handy presets for quick selection in the demo. */
val SLOT_PIECE_COUNTS = listOf(5, 7, 9, 11, 13)

private data class Plan(val rows: Int, val cols: Int, val overlays: List<Pair<Int, Int>>)

/**
 * Chooses a base grid + overlay placement for [n], preferring a **symmetric** result. Overlays are
 * placed as a single centered band on the grid's middle row, which (a) keeps every base piece
 * connected — a single-row band can never bite all four corners off a cell — and (b) reads as a
 * balanced strip of center pieces rather than a lopsided cluster.
 *
 * Scoring rewards near-square aspect, an even row count (so the band sits on the exact center line),
 * and a horizontally centered band.
 */
private fun planFor(n: Int): Plan {
    var best: Plan? = null
    var bestScore = Float.MAX_VALUE
    for (rows in 1..n) {
        for (cols in 1..n) {
            val area = rows * cols
            if (area > n) break
            val plan = candidate(rows, cols, n - area) ?: continue
            val score = scoreOf(plan)
            if (score < bestScore) {
                bestScore = score
                best = plan
            }
        }
    }
    return best ?: Plan(1, n, emptyList())
}

/** A base [rows]×[cols] grid with [k] overlays as a centered band on the middle row, or null. */
private fun candidate(rows: Int, cols: Int, k: Int): Plan? {
    if (k == 0) return Plan(rows, cols, emptyList())
    if (rows < 2) return null              // need an interior row to host a center piece
    if (cols - 1 < k) return null          // band must fit within one interior row
    val row = rows / 2                     // middle interior row (the exact center line when rows is even)
    val startCol = 1 + ((cols - 1) - k) / 2
    return Plan(rows, cols, (0 until k).map { row to (startCol + it) })
}

private fun scoreOf(plan: Plan): Float {
    val aspectExcess = maxOf(plan.rows, plan.cols).toFloat() / minOf(plan.rows, plan.cols) - 1f
    val k = plan.overlays.size
    val verticalAsym = if (k == 0 || plan.rows % 2 == 0) 0f else 1f        // even rows → band on center line
    val horizontalAsym = if (k == 0 || ((plan.cols - 1) - k) % 2 == 0) 0f else 1f
    return aspectExcess * 2.5f + verticalAsym * 3.5f + horizontalAsym * 1.5f + k * 0.3f
}

/**
 * Builds a layout from a base [baseRows]×[baseCols] grid plus [overlays] center pieces, each sitting
 * on an interior base vertex `(vr, vc)` and stealing the nearest cell from its four neighbors. Works
 * on a 2×-finer cell grid so each overlay is a 2×2 block straddling the vertex.
 */
fun gridPlusOverlaysLayout(
    baseRows: Int,
    baseCols: Int,
    overlays: List<Pair<Int, Int>>,
    seed: Long = DEFAULT_SEED,
): SlotLayout {
    val fineR = baseRows * 2
    val fineC = baseCols * 2
    val pieceOf = IntArray(fineR * fineC) { idx ->
        val fr = idx / fineC
        val fc = idx % fineC
        (fr / 2) * baseCols + (fc / 2)   // base piece id
    }
    val baseCount = baseRows * baseCols
    overlays.forEachIndexed { k, (vr, vc) ->
        val centerId = baseCount + k
        // The 2×2 fine block centered on base vertex (vr, vc).
        listOf(
            (2 * vr - 1) to (2 * vc - 1), (2 * vr - 1) to (2 * vc),
            (2 * vr) to (2 * vc - 1), (2 * vr) to (2 * vc),
        ).forEach { (fr, fc) -> pieceOf[fr * fineC + fc] = centerId }
    }
    return polyominoLayout(fineR, fineC, pieceOf, seed)
}

// ── Generator core ────────────────────────────────────────────────────────────

private fun polyominoLayout(rows: Int, cols: Int, pieceOf: IntArray, seed: Long): SlotLayout {
    val ids = pieceOf.toSet().sorted()
    val slots = ids.map { id -> buildSlot(id, rows, cols, pieceOf, seed) }
    return SlotLayout(slots)
}

private fun cellAt(rows: Int, cols: Int, pieceOf: IntArray, r: Int, c: Int): Int =
    if (r in 0 until rows && c in 0 until cols) pieceOf[r * cols + c] else -1

/** A boundary half-edge of a piece: from [start] to [end] (vertex indices), interior on the right. */
private data class DirEdge(
    val start: Pair<Int, Int>,
    val end: Pair<Int, Int>,
    val outX: Float,
    val outY: Float,
    val other: Int,   // piece on the far side, or -1 for the board border
)

private fun buildSlot(id: Int, rows: Int, cols: Int, pieceOf: IntArray, seed: Long): PieceSlot {
    // Collect this piece's clockwise boundary half-edges (interior on the right).
    val edges = ArrayList<DirEdge>()
    var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
    var sumCx = 0f; var sumCy = 0f; var cellCount = 0

    for (r in 0 until rows) for (c in 0 until cols) {
        if (pieceOf[r * cols + c] != id) continue
        cellCount++
        val x0 = c.toFloat() / cols; val x1 = (c + 1).toFloat() / cols
        val y0 = r.toFloat() / rows; val y1 = (r + 1).toFloat() / rows
        minX = minOf(minX, x0); maxX = maxOf(maxX, x1)
        minY = minOf(minY, y0); maxY = maxOf(maxY, y1)
        sumCx += (x0 + x1) / 2f; sumCy += (y0 + y1) / 2f

        val tl = c to r; val tr = (c + 1) to r; val br = (c + 1) to (r + 1); val bl = c to (r + 1)
        // top / right / bottom / left, each emitted only if the neighbor is a different piece.
        cellAt(rows, cols, pieceOf, r - 1, c).let { if (it != id) edges += DirEdge(tl, tr, 0f, -1f, it) }
        cellAt(rows, cols, pieceOf, r, c + 1).let { if (it != id) edges += DirEdge(tr, br, 1f, 0f, it) }
        cellAt(rows, cols, pieceOf, r + 1, c).let { if (it != id) edges += DirEdge(br, bl, 0f, 1f, it) }
        cellAt(rows, cols, pieceOf, r, c - 1).let { if (it != id) edges += DirEdge(bl, tl, -1f, 0f, it) }
    }

    // Chain the half-edges into a single ordered loop (start → end → next.start == end …).
    val byStart = edges.associateBy { it.start }
    val ordered = ArrayList<DirEdge>(edges.size)
    var cur = edges.first()
    val startVertex = cur.start
    do {
        ordered += cur
        cur = byStart[cur.end] ?: error("open boundary for piece $id")
    } while (cur.start != startVertex)
    check(ordered.size == edges.size) { "piece $id boundary is not a single loop" }

    fun vx(v: Pair<Int, Int>) = v.first.toFloat() / cols
    fun vy(v: Pair<Int, Int>) = v.second.toFloat() / rows

    val segments = ArrayList<Seg>()
    ordered.forEach { e ->
        val sx = vx(e.start); val sy = vy(e.start)
        val ex = vx(e.end); val ey = vy(e.end)
        if (e.other < 0) {
            segments += Seg.Line(NormPoint(ex, ey))
        } else {
            val horizontal = e.start.second == e.end.second
            val ui = if (horizontal) minOf(e.start.first, e.end.first) else e.start.first
            val uj = if (horizontal) e.start.second else minOf(e.start.second, e.end.second)
            val bulbPositive = seedBit(seed, horizontal, ui, uj) == 1
            val outwardPositive = if (horizontal) e.outY > 0f else e.outX > 0f
            val tab = outwardPositive == bulbPositive
            segments += knobSegs(sx, sy, ex, ey, e.outX, e.outY, tab)
        }
    }

    return PieceSlot(
        id = id,
        contour = Contour(NormPoint(vx(startVertex), vy(startVertex)), segments),
        bounds = NormRect(minX, minY, maxX, maxY),
        anchor = NormPoint(sumCx / cellCount, sumCy / cellCount),
    )
}

/**
 * Classic jigsaw knob along the edge S→E with the given outward unit normal. A TAB bulges outward,
 * a BLANK indents inward; offsets scale with edge length so knobs stay proportional. Mirrors the
 * grid puzzle's [com.moe.puzzle.feature.puzzle.ui] knob so both look identical.
 */
private fun knobSegs(
    sx: Float, sy: Float, ex: Float, ey: Float,
    nx: Float, ny: Float, tab: Boolean,
): List<Seg> {
    val len = hypot((ex - sx).toDouble(), (ey - sy).toDouble()).toFloat()
    val sign = if (tab) 1f else -1f
    val dx = ex - sx; val dy = ey - sy
    fun pt(t: Float, o: Float) = NormPoint(sx + dx * t + nx * (o * sign * len), sy + dy * t + ny * (o * sign * len))
    return listOf(
        Seg.Line(pt(0.40f, 0f)),
        Seg.Cubic(pt(0.42f, 0.10f), pt(0.32f, 0.12f), pt(0.32f, 0.22f)),
        Seg.Cubic(pt(0.32f, 0.32f), pt(0.42f, 0.37f), pt(0.50f, 0.37f)),
        Seg.Cubic(pt(0.58f, 0.37f), pt(0.68f, 0.32f), pt(0.68f, 0.22f)),
        Seg.Cubic(pt(0.68f, 0.12f), pt(0.58f, 0.10f), pt(0.60f, 0f)),
        Seg.Line(pt(1f, 0f)),
    )
}

/** Deterministic 0/1 per unit edge — decides which side a shared knob bulges toward. */
private fun seedBit(seed: Long, horizontal: Boolean, i: Int, j: Int): Int {
    var h = seed
    h = h * 1_000_003L + (if (horizontal) 1 else 0)
    h = h * 1_000_003L + i
    h = h * 1_000_003L + j
    h = h xor (h ushr 31)
    h *= -0x61c8864680b583ebL
    return ((h ushr 33).toInt() and 1)
}
