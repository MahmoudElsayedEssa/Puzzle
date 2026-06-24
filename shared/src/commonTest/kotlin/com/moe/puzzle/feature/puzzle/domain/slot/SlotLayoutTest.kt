package com.moe.puzzle.feature.puzzle.domain.slot

import kotlin.test.Test
import kotlin.test.assertEquals

class SlotLayoutTest {

    private val layout = centerFiveLayout()

    @Test
    fun hasFivePieces() {
        assertEquals(5, layout.totalPieces)
    }

    @Test
    fun dropOnCenterResolvesToCenterPiece() {
        // A drop right at the board center must pick the center piece (4), not a corner.
        assertEquals(4, layout.nearestSlotId(0.5f, 0.5f))
    }

    @Test
    fun dropNearCornersResolvesToCornerPieces() {
        assertEquals(0, layout.nearestSlotId(0.08f, 0.08f)) // top-left
        assertEquals(1, layout.nearestSlotId(0.92f, 0.08f)) // top-right
        assertEquals(2, layout.nearestSlotId(0.08f, 0.92f)) // bottom-left
        assertEquals(3, layout.nearestSlotId(0.92f, 0.92f)) // bottom-right
    }

    @Test
    fun eachSlotsAnchorResolvesToItself() {
        // The nearest-slot mapping must be self-consistent: a piece's own anchor picks its own slot.
        layout.slots.forEach { slot ->
            assertEquals(slot.id, layout.nearestSlotId(slot.anchor.x, slot.anchor.y))
        }
    }

    @Test
    fun primeLayoutsBuildWithExpectedPieceCounts() {
        // Construction traces each piece's boundary; a broken polyomino would throw here.
        assertEquals(7, slotLayoutForCount(7).totalPieces)
        assertEquals(9, slotLayoutForCount(9).totalPieces)
        assertEquals(11, slotLayoutForCount(11).totalPieces)
        assertEquals(13, slotLayoutForCount(13).totalPieces)
    }

    @Test
    fun primeLayoutAnchorsAreSelfConsistent() {
        SLOT_PIECE_COUNTS.forEach { n ->
            val l = slotLayoutForCount(n)
            l.slots.forEach { slot ->
                assertEquals(slot.id, l.nearestSlotId(slot.anchor.x, slot.anchor.y), "count=$n slot=${slot.id}")
            }
        }
    }

    @Test
    fun anyTypedCountBuildsExactlyThatManyPieces() {
        // The general planner must place overlays without disconnecting a base piece (which would
        // throw during boundary tracing) for every count a user could type.
        for (n in MIN_PIECE_COUNT..MAX_PIECE_COUNT) {
            assertEquals(n, slotLayoutForCount(n).totalPieces, "count=$n")
        }
    }

    @Test
    fun typedCountIsClamped() {
        assertEquals(MIN_PIECE_COUNT, slotLayoutForCount(0).totalPieces)
        assertEquals(MAX_PIECE_COUNT, slotLayoutForCount(999).totalPieces)
    }
}
