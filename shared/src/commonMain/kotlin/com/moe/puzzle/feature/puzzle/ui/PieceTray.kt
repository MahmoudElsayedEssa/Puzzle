package com.moe.puzzle.feature.puzzle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moe.puzzle.feature.puzzle.domain.GridSpec
import com.moe.puzzle.feature.puzzle.domain.PuzzlePiece
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

const val TAG_PIECE_TRAY = "puzzle_piece_tray"

val TRAY_ITEM_SIZE = 84.dp

/**
 * Horizontal tray of pieces beneath the board.
 *
 *  - Placed pieces are removed from the tray.
 *  - Unlocked-and-unplaced pieces show their image slice in a jigsaw thumbnail.
 *      - Tap to select (then tap a board slot to place).
 *      - Long-press and drag onto the board to drop directly.
 *  - Locked pieces show a greyed shape with a lock badge.
 *
 * Drag positions are reported in *root* coordinates so the caller can hit-test the board.
 */
@Composable
fun PieceTray(
    grid: GridSpec,
    pieces: ImmutableList<PuzzlePiece>,
    unlocked: ImmutableSet<Int>,
    placed: ImmutableSet<Int>,
    selectedPieceId: Int?,
    image: ImageBitmap?,
    onSelect: (Int) -> Unit,
    onDragStart: (id: Int, rootPos: Offset) -> Unit,
    onDrag: (rootPos: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPlaceAccessible: (Int) -> Unit,
    draggingId: Int?,
    modifier: Modifier = Modifier,
) {
    val visible = remember(pieces, placed) { pieces.filter { it.id !in placed } }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(visible, key = { it.id }) { piece ->
            TrayItem(
                grid = grid,
                piece = piece,
                image = image,
                unlocked = piece.id in unlocked,
                selected = piece.id == selectedPieceId,
                hidden = piece.id == draggingId,
                onSelect = { onSelect(piece.id) },
                onDragStart = { pos -> onDragStart(piece.id, pos) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onPlaceAccessible = { onPlaceAccessible(piece.id) },
            )
        }
    }
}

@Composable
private fun TrayItem(
    grid: GridSpec,
    piece: PuzzlePiece,
    image: ImageBitmap?,
    unlocked: Boolean,
    selected: Boolean,
    hidden: Boolean,
    onSelect: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPlaceAccessible: () -> Unit,
) {
    val borderColor = when {
        selected -> Color(0xFF34C759)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    var itemRoot by remember { mutableStateOf(Offset.Zero) }

    Surface(
        modifier = Modifier
            .size(TRAY_ITEM_SIZE)
            .onGloballyPositioned { itemRoot = it.positionInRoot() }
            .semantics {
                contentDescription = if (unlocked) {
                    "Piece ${piece.id}${if (selected) ", selected" else ""}"
                } else {
                    "Piece ${piece.id}, locked"
                }
                if (unlocked) {
                    // Screen-reader equivalent of dragging the piece to its slot.
                    customActions = listOf(
                        CustomAccessibilityAction("Place on board") { onPlaceAccessible(); true },
                    )
                }
            }
            .then(
                if (unlocked) Modifier.pointerInput(piece.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start -> onDragStart(itemRoot + start) },
                        onDrag = { change, _ -> change.consume(); onDrag(itemRoot + change.position) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                } else Modifier,
            )
            .clickable(enabled = unlocked, onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        color = if (unlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (selected) 2.5.dp else 1.dp, borderColor),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            // While this piece is being dragged, its tray slot shows empty.
            if (!hidden) {
                JigsawPieceImage(
                    grid = grid,
                    piece = piece,
                    image = image,
                    locked = !unlocked,
                    modifier = Modifier.fillMaxSize().aspectRatio(1f),
                )
                if (!unlocked) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LockBadge(size = 22.dp)
                        Text(
                            text = "Locked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws a single piece (jigsaw shape + its image slice) with margin for the bulbs.
 * When [elevated] (e.g. while dragging) a soft piece-shaped shadow is drawn beneath it.
 */
@Composable
fun JigsawPieceImage(
    grid: GridSpec,
    piece: PuzzlePiece,
    image: ImageBitmap?,
    locked: Boolean,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val path = remember(piece) { buildPiecePath(piece.edges) }
    Canvas(modifier = modifier) {
        // Map the [-0.4, 1.4] range (unit square + bulb overhang) into the canvas.
        val span = 1.8f
        val s = minOf(size.width, size.height) / span
        withTransform({
            translate(0.4f * s, 0.4f * s)
            scale(s, s, pivot = Offset.Zero)
        }) {
            if (elevated) {
                // Piece-shaped drop shadow, offset down-right in unit space.
                withTransform({ translate(0.05f, 0.07f) }) {
                    drawPath(path, color = Color.Black, alpha = 0.28f)
                }
            }
            if (locked) {
                drawPath(path, color = Color(0xFFBDBDBD), alpha = 0.5f)
            } else if (image != null) {
                drawPieceFragment(grid, piece, image, path, alpha = 1f)
                drawPath(path, color = Color.White, alpha = 0.8f, style = Stroke(width = 0.02f))
            } else {
                drawPath(path, color = Color(0xFF9E9E9E))
            }
        }
    }
}

/** Minimal padlock glyph drawn with primitives (avoids an icons dependency). */
@Composable
private fun LockBadge(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bodyTop = h * 0.45f
        val bodyLeft = w * 0.22f
        val bodyRight = w * 0.78f
        val stroke = w * 0.10f
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.32f, h * 0.18f),
            size = Size(w * 0.36f, h * 0.40f),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyRight - bodyLeft, h * 0.42f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
        )
    }
}
