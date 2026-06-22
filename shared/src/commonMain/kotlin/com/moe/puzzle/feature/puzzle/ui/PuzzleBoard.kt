package com.moe.puzzle.feature.puzzle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.moe.puzzle.feature.puzzle.domain.GridCell
import com.moe.puzzle.feature.puzzle.domain.GridSpec
import com.moe.puzzle.feature.puzzle.domain.PuzzlePiece
import com.moe.puzzle.feature.puzzle.domain.PlacementStyle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.launch

private const val PLACE_DURATION_MS = 350
private const val FLASH_DURATION_MS = 450

private val GhostStroke = Color(0xFF9E9E9E)
private val TargetHighlight = Color(0xFF34C759)
private val WrongFlash = Color(0xFFE53935)

/**
 * Single-Canvas jigsaw board.
 *
 * Every cell renders a jigsaw ghost outline. [placed] pieces render their image slice
 * (animated in on placement). When a tray piece is [selectedPieceId], its correct slot is
 * highlighted so the user knows where to tap. Tapping a cell reports it via [onCellTap].
 *
 * Placement animation is driven by per-piece Animatable read in the draw lambda, so only the
 * draw phase re-runs per frame. The [placed] set at first composition is captured into
 * [prevPlacedRef] so pre-placed pieces (resume path) don't re-animate.
 */
@Composable
fun PuzzleBoard(
    grid: GridSpec,
    pieces: ImmutableList<PuzzlePiece>,
    placed: ImmutableSet<Int>,
    highlightPieceId: Int?,
    placementStyle: PlacementStyle,
    image: ImageBitmap?,
    onCellTap: (GridCell) -> Unit,
    modifier: Modifier = Modifier,
    wrongCellId: Int? = null,
) {
    val alphaMap = remember(grid) { HashMap<Int, Animatable<Float, *>>() }
    val prevPlacedRef = remember(grid) { placed.toMutableSet() }
    val piecePaths = remember(pieces) { pieces.associate { it.id to buildPiecePath(it.edges) } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(placed) {
        val delta = placed - prevPlacedRef
        // Rebuild (not just add) so a Reset clears the set and re-placed pieces animate again.
        prevPlacedRef.clear()
        prevPlacedRef.addAll(placed)
        delta.forEach { id ->
            val anim = Animatable(0f)
            alphaMap[id] = anim
            scope.launch { anim.animateTo(1f, animationSpec = tween(PLACE_DURATION_MS)) }
        }
    }

    // Red flash on a wrong drop/tap; fades out on its own.
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(wrongCellId) {
        if (wrongCellId != null) {
            flashAlpha.snapTo(1f)
            flashAlpha.animateTo(0f, animationSpec = tween(FLASH_DURATION_MS))
        }
    }

    Canvas(
        modifier = modifier.pointerInput(grid) {
            detectTapGestures { offset ->
                val cellW = size.width.toFloat() / grid.cols
                val cellH = size.height.toFloat() / grid.rows
                val col = (offset.x / cellW).toInt().coerceIn(0, grid.cols - 1)
                val row = (offset.y / cellH).toInt().coerceIn(0, grid.rows - 1)
                onCellTap(GridCell(row, col))
            }
        }
    ) {
        val cellW = size.width / grid.cols
        val cellH = size.height / grid.rows
        val ghostStroke = 0.022f
        val outlineStroke = 0.012f

        // Faint preview of the whole picture so the board reads as a target, not a blank box.
        if (image != null) {
            drawBoardImage(image, size.width.toInt(), size.height.toInt(), alpha = 0.12f)
        }

        pieces.forEach { piece ->
            val path = piecePaths[piece.id] ?: return@forEach
            val isPlaced = piece.id in placed
            val isTargetSlot = highlightPieceId != null && piece.id == highlightPieceId

            withTransform({
                translate(piece.cell.col * cellW, piece.cell.row * cellH)
                scale(cellW, cellH, pivot = Offset.Zero)
            }) {
                if (isPlaced) {
                    val a = alphaMap[piece.id]?.value ?: 1f
                    val pop = if (placementStyle == PlacementStyle.FADE_AND_POP) 0.9f + a * 0.1f else 1f
                    if (pop != 1f) {
                        withTransform({ scale(pop, pop, pivot = Offset(0.5f, 0.5f)) }) {
                            drawPlaced(grid, piece, path, image, a, outlineStroke)
                        }
                    } else {
                        drawPlaced(grid, piece, path, image, a, outlineStroke)
                    }
                } else {
                    // Empty slot — ghost outline; highlighted when it's the selected piece's home.
                    if (isTargetSlot) {
                        drawPath(path, color = TargetHighlight, alpha = 0.12f)
                        drawPath(path, color = TargetHighlight, style = Stroke(width = ghostStroke * 1.4f))
                    } else {
                        drawPath(path, color = GhostStroke, style = Stroke(width = ghostStroke))
                    }
                }
            }
        }

        // Wrong-placement flash on the targeted slot.
        if (wrongCellId != null && flashAlpha.value > 0f) {
            val path = piecePaths[wrongCellId]
            val cell = grid.cellOf(wrongCellId)
            if (path != null) {
                withTransform({
                    translate(cell.col * cellW, cell.row * cellH)
                    scale(cellW, cellH, pivot = Offset.Zero)
                }) {
                    drawPath(path, color = WrongFlash, alpha = 0.5f * flashAlpha.value)
                    drawPath(
                        path,
                        color = WrongFlash,
                        alpha = flashAlpha.value,
                        style = Stroke(width = ghostStroke * 1.4f),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPlaced(
    grid: GridSpec,
    piece: PuzzlePiece,
    path: Path,
    image: ImageBitmap?,
    alpha: Float,
    outlineStroke: Float,
) {
    if (image != null) {
        drawPieceFragment(grid, piece, image, path, alpha)
    } else {
        clipPath(path) {
            drawRect(color = Color(0xFF9E9E9E), alpha = alpha * 0.6f)
        }
    }
    drawPath(path, color = Color.White, alpha = 0.7f * alpha, style = Stroke(width = outlineStroke))
}
