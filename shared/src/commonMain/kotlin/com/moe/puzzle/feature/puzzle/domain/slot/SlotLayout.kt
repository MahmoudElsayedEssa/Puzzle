package com.moe.puzzle.feature.puzzle.domain.slot

/**
 * Free-form puzzle layout — the engine that replaces the rigid grid.
 *
 * Every piece is a [PieceSlot] with its own outline ([Contour]) and target position, expressed in
 * normalized board space `[0,1]²` (independent of pixel size / density). A grid is just one way to
 * produce slots; non-grid layouts (e.g. the 2×2 + center "5-piece") are produced the same way.
 *
 * Pure Kotlin — no Compose, no platform. The UI converts a [Contour] into a draw path.
 */

/** A point in normalized board space `[0,1]²`. */
data class NormPoint(val x: Float, val y: Float)

/** An axis-aligned box in normalized board space — a slot's image-sampling bounds. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
}

/** One segment of a closed outline. Lines for straight cuts; cubics for jigsaw knobs later. */
sealed interface Seg {
    data class Line(val to: NormPoint) : Seg
    data class Cubic(val c1: NormPoint, val c2: NormPoint, val to: NormPoint) : Seg
}

/** A closed outline: a start point followed by segments back to (implicitly) the start. */
data class Contour(val start: NormPoint, val segments: List<Seg>)

/**
 * A single puzzle piece slot.
 *
 * @param id        canonical id; a piece belongs in the slot with the same id.
 * @param contour   outline in board space (used to clip the image fragment + draw the ghost).
 * @param bounds    bounding box of the slot (used to frame its tray thumbnail).
 * @param anchor    representative center, used for nearest-slot hit-testing / snap.
 */
data class PieceSlot(
    val id: Int,
    val contour: Contour,
    val bounds: NormRect,
    val anchor: NormPoint,
)

data class SlotLayout(val slots: List<PieceSlot>) {
    val totalPieces get() = slots.size

    fun slotById(id: Int): PieceSlot = slots.first { it.id == id }

    /**
     * The id of the slot whose [anchor] is closest to a normalized board point — this is how a
     * drop position / tap maps to a target slot, replacing the grid's `idOf(cell)`.
     */
    fun nearestSlotId(x: Float, y: Float): Int =
        slots.minByOrNull { val dx = it.anchor.x - x; val dy = it.anchor.y - y; dx * dx + dy * dy }!!.id
}

// Layout generators (knobbed jigsaw pieces) live in SlotJigsaw.kt.
