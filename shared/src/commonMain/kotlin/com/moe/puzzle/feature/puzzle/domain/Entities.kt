package com.moe.puzzle.feature.puzzle.domain

@kotlin.jvm.JvmInline
value class CampaignId(val value: String)

data class GridSpec(val rows: Int, val cols: Int) {
    val totalPieces get() = rows * cols
    fun cellOf(id: Int) = GridCell(id / cols, id % cols)
    fun idOf(cell: GridCell) = cell.row * cols + cell.col
}

data class GridCell(val row: Int, val col: Int)

enum class EdgeType { FLAT, TAB, BLANK }

data class EdgeProfile(
    val top: EdgeType,
    val right: EdgeType,
    val bottom: EdgeType,
    val left: EdgeType,
)

data class PuzzlePiece(
    val id: Int,
    val cell: GridCell,
    val edges: EdgeProfile,
)

/** How a piece animates in when it lands on the board. */
enum class PlacementStyle { FADE, FADE_AND_POP }

data class RewardDisplay(val label: String, val ctaText: String = "Claim")

data class PuzzleProgress(val unlockedPieceIds: Set<Int>)

data class PuzzleConfig(
    val campaignId: CampaignId,
    val grid: GridSpec,
    val progress: PuzzleProgress,
    val reward: RewardDisplay? = null,
    val edgeStrategy: EdgeStrategy = EdgeStrategy.Rectangular,
    val edgeSeed: Long = campaignId.value.hashCode().toLong(),
    val placementStyle: PlacementStyle = PlacementStyle.FADE,
)
