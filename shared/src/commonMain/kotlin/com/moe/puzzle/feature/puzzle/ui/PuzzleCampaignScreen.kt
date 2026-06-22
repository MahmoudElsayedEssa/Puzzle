package com.moe.puzzle.feature.puzzle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.moe.puzzle.feature.puzzle.domain.GridCell
import com.moe.puzzle.feature.puzzle.presentation.PuzzleIntent
import com.moe.puzzle.feature.puzzle.presentation.PuzzleUiState
import com.moe.puzzle.feature.puzzle.presentation.PuzzleViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Test tags — usable in UI tests via onNodeWithTag.
const val TAG_PUZZLE_BOARD = "puzzle_board"
const val TAG_REWARD_CTA = "puzzle_reward_cta"
const val TAG_PROGRESS_HEADER = "puzzle_progress_header"

/** Root entry composable that collects ViewModel state and delegates to the stateless screen. */
@Composable
fun PuzzleCampaignScreen(
    viewModel: PuzzleViewModel,
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    PuzzleCampaignScreenContent(
        state = state,
        image = image,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

/**
 * Stateless screen: board card on top, piece tray below.
 *
 * Two ways to place a piece:
 *  - Tap a tray piece to select it, then tap its slot on the board.
 *  - Long-press a tray piece and drag it onto its slot.
 *
 * Drag state (which piece, current finger position) is held here as plain UI state; the actual
 * placement still flows through [PuzzleIntent.SelectPiece] + [PuzzleIntent.PlaceAt] so the
 * ViewModel remains the single source of truth.
 */
@Composable
fun PuzzleCampaignScreenContent(
    state: PuzzleUiState,
    image: ImageBitmap?,
    onIntent: (PuzzleIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressDescription = "${state.placedCount} of ${state.total} pieces placed"

    // Drag-and-drop state, all in *root* coordinates.
    var boxRoot by remember { mutableStateOf(Offset.Zero) }
    var boardRect by remember { mutableStateOf<Rect?>(null) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    // Transient wrong-placement flash target (a cell id); auto-clears so it can re-trigger.
    var wrongCellId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(wrongCellId) {
        if (wrongCellId != null) {
            delay(500)
            wrongCellId = null
        }
    }

    val haptics = LocalHapticFeedback.current

    // Single placement path for tap, drag, and a11y — gives immediate feedback then forwards
    // to the ViewModel (which re-validates and is the source of truth).
    fun attemptPlace(id: Int, cell: GridCell) {
        val correct = state.grid.idOf(cell) == id
        if (correct) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            wrongCellId = state.grid.idOf(cell)
        }
        onIntent(PuzzleIntent.PlacePiece(id, cell))
    }

    fun endDrag() {
        val id = draggingId
        val rect = boardRect
        if (id != null && rect != null && rect.contains(dragPos)) {
            val col = ((dragPos.x - rect.left) / (rect.width / state.grid.cols))
                .toInt().coerceIn(0, state.grid.cols - 1)
            val row = ((dragPos.y - rect.top) / (rect.height / state.grid.rows))
                .toInt().coerceIn(0, state.grid.rows - 1)
            attemptPlace(id, GridCell(row, col))
        }
        draggingId = null
    }

    Box(modifier = modifier.onGloballyPositioned { boxRoot = it.positionInRoot() }) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Progress header ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        stateDescription = progressDescription
                    },
            ) {
                Text(
                    text = progressDescription,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.End)
                        .semantics { contentDescription = progressDescription },
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (state.total > 0) state.placedCount.toFloat() / state.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Puzzle board card ────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Puzzle Board",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    PuzzleBoard(
                        grid = state.grid,
                        pieces = state.pieces,
                        placed = state.placed,
                        // Highlight the target slot for both the selected piece and the dragged piece.
                        highlightPieceId = draggingId ?: state.selectedPieceId,
                        placementStyle = state.placementStyle,
                        image = image,
                        wrongCellId = wrongCellId,
                        onCellTap = { cell ->
                            state.selectedPieceId?.let { attemptPlace(it, cell) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .onGloballyPositioned { boardRect = it.boundsInRoot() }
                            .semantics {
                                stateDescription = progressDescription
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "Puzzle board. $progressDescription"
                            },
                    )
                }
            }

            // ── Reward CTA (on completion) ───────────────────────────────────────
            if (state.isComplete && state.reward != null) {
                Button(
                    onClick = { onIntent(PuzzleIntent.RewardTapped) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Claim reward: ${state.reward.label}" },
                ) {
                    Text(text = state.reward.ctaText)
                }
            }

            // ── Piece tray card ──────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Drag or Tap a Piece",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            draggingId != null -> "Drop it on its slot"
                            state.selectedPieceId != null -> "Now tap its slot on the board"
                            else -> "Long-press to drag, or tap then tap its slot"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    PieceTray(
                        grid = state.grid,
                        pieces = state.pieces,
                        unlocked = state.unlocked,
                        placed = state.placed,
                        selectedPieceId = state.selectedPieceId,
                        image = image,
                        onSelect = { onIntent(PuzzleIntent.SelectPiece(it)) },
                        onDragStart = { id, pos -> draggingId = id; dragPos = pos },
                        onDrag = { pos -> dragPos = pos },
                        onDragEnd = { endDrag() },
                        onPlaceAccessible = { id -> attemptPlace(id, state.grid.cellOf(id)) },
                        draggingId = draggingId,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ── Floating piece that follows the finger during a drag ─────────────────
        val id = draggingId
        val draggedPiece = id?.let { dragged -> state.pieces.firstOrNull { it.id == dragged } }
        if (draggedPiece != null) {
            val halfPx = with(LocalDensity.current) { TRAY_ITEM_SIZE.toPx() } / 2f
            Box(
                modifier = Modifier
                    // dragPos/boxRoot are read inside the lambda so only the layout phase re-runs
                    // each frame — the composition does not recompose while dragging.
                    .offset {
                        val local = dragPos - boxRoot
                        IntOffset(
                            (local.x - halfPx).roundToInt(),
                            (local.y - halfPx).roundToInt(),
                        )
                    }
                    .size(TRAY_ITEM_SIZE),
            ) {
                JigsawPieceImage(
                    grid = state.grid,
                    piece = draggedPiece,
                    image = image,
                    locked = false,
                    elevated = true,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
        }
    }
}
