package com.moe.puzzle.feature.puzzle.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.moe.puzzle.feature.puzzle.domain.EdgeProfile
import com.moe.puzzle.feature.puzzle.domain.EdgeType
import com.moe.puzzle.feature.puzzle.domain.GridSpec
import com.moe.puzzle.feature.puzzle.domain.PuzzlePiece

/**
 * Builds a Path for a puzzle piece in normalized [0,1]² space.
 * FLAT edge = straight line; TAB protrudes outward; BLANK indents inward.
 *
 * Caller scales the path to screen pixels via withTransform or Matrix.transform.
 */
internal fun buildPiecePath(edges: EdgeProfile): Path = Path().apply {
    moveTo(0f, 0f)
    // Top: (0,0)→(1,0), outward normal (0,-1)
    addEdge(this, 0f, 0f, 1f, 0f, 0f, -1f, edges.top)
    // Right: (1,0)→(1,1), outward normal (1,0)
    addEdge(this, 1f, 0f, 1f, 1f, 1f, 0f, edges.right)
    // Bottom: (1,1)→(0,1), outward normal (0,1)
    addEdge(this, 1f, 1f, 0f, 1f, 0f, 1f, edges.bottom)
    // Left: (0,1)→(0,0), outward normal (-1,0)
    addEdge(this, 0f, 1f, 0f, 0f, -1f, 0f, edges.left)
    close()
}

/**
 * Classic jigsaw edge: flat shoulders, a pinched neck, then a round bulb that overhangs the
 * neck — the silhouette of a real puzzle knob. A TAB protrudes outward; a BLANK is the same
 * shape mirrored inward (so a neighbour's TAB fits exactly).
 *
 * Geometry is expressed as (t, o) pairs — `t` is the fraction along the edge, `o` the outward
 * offset — then mapped onto the actual edge via the start point + along-vector + outward normal.
 * The control points sit at t < neck / t > neck (e.g. 0.30 vs neck at 0.42) which is what gives
 * the bulb its overhanging, interlocking shoulders.
 */
private fun addEdge(
    path: Path,
    sx: Float, sy: Float,
    ex: Float, ey: Float,
    nx: Float, ny: Float,   // outward unit normal
    edgeType: EdgeType,
) {
    if (edgeType == EdgeType.FLAT) {
        path.lineTo(ex, ey)
        return
    }
    val sign = if (edgeType == EdgeType.TAB) 1f else -1f
    val dx = ex - sx; val dy = ey - sy

    // Map an along/outward coordinate onto the edge in unit-square space.
    fun px(t: Float, o: Float) = sx + dx * t + nx * (o * sign)
    fun py(t: Float, o: Float) = sy + dy * t + ny * (o * sign)
    fun curve(t1: Float, o1: Float, t2: Float, o2: Float, t3: Float, o3: Float) =
        path.cubicTo(px(t1, o1), py(t1, o1), px(t2, o2), py(t2, o2), px(t3, o3), py(t3, o3))

    // Left shoulder.
    path.lineTo(px(0.40f, 0f), py(0.40f, 0f))
    // Flare up out of the neck into the left side of the bulb (control t < 0.40 → overhang).
    curve(0.42f, 0.10f, 0.32f, 0.12f, 0.32f, 0.22f)
    // Over the top to the apex.
    curve(0.32f, 0.32f, 0.42f, 0.37f, 0.50f, 0.37f)
    // Apex down the right side of the bulb.
    curve(0.58f, 0.37f, 0.68f, 0.32f, 0.68f, 0.22f)
    // Back into the neck (control t > 0.60 → overhang on the right).
    curve(0.68f, 0.12f, 0.58f, 0.10f, 0.60f, 0f)
    // Right shoulder to the corner.
    path.lineTo(ex, ey)
}

/** The largest centered square of [image] — used to map any aspect ratio onto a square board. */
private fun squareCrop(image: ImageBitmap): Pair<IntOffset, IntSize> {
    val side = minOf(image.width, image.height)
    val offset = IntOffset((image.width - side) / 2, (image.height - side) / 2)
    return offset to IntSize(side, side)
}

/**
 * Draws the slice of [image] belonging to [piece], clipped to its jigsaw [path].
 *
 * Assumes the current [DrawScope] transform maps the unit square `[0,1]²` onto the piece's
 * cell (e.g. via `withTransform { translate(...); scale(cellW, cellH, pivot = Offset.Zero) }`).
 * The image is centre-cropped to a square (so non-square assets don't distort), drawn across the
 * whole board span, and the clip restricts it to this piece.
 */
internal fun DrawScope.drawPieceFragment(
    grid: GridSpec,
    piece: PuzzlePiece,
    image: ImageBitmap,
    path: Path,
    alpha: Float,
) {
    val (srcOffset, srcSize) = squareCrop(image)
    clipPath(path) {
        drawImage(
            image = image,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstOffset = IntOffset(-piece.cell.col, -piece.cell.row),
            dstSize = IntSize(grid.cols, grid.rows),
            alpha = alpha,
        )
    }
}

/** Draws the centre-cropped whole image to fill the current board-sized draw area. */
internal fun DrawScope.drawBoardImage(image: ImageBitmap, widthPx: Int, heightPx: Int, alpha: Float) {
    val (srcOffset, srcSize) = squareCrop(image)
    drawImage(
        image = image,
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(widthPx, heightPx),
        alpha = alpha,
    )
}
