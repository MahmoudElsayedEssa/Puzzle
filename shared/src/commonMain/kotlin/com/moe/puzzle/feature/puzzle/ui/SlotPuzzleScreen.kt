package com.moe.puzzle.feature.puzzle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.moe.puzzle.feature.puzzle.domain.PlacementStyle
import com.moe.puzzle.feature.puzzle.domain.slot.Contour
import com.moe.puzzle.feature.puzzle.domain.slot.PieceSlot
import com.moe.puzzle.feature.puzzle.domain.slot.Seg
import com.moe.puzzle.feature.puzzle.domain.slot.SlotLayout
import com.moe.puzzle.feature.puzzle.presentation.SlotIntent
import com.moe.puzzle.feature.puzzle.presentation.SlotPuzzleUiState
import com.moe.puzzle.feature.puzzle.presentation.SlotPuzzleViewModel
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

const val TAG_SLOT_BOARD = "slot_puzzle_board"

private const val PLACE_DURATION_MS = 350

private val GhostStroke = Color(0xFF9E9E9E)
private val TargetHighlight = Color(0xFF34C759)

// ── Geometry → draw helpers ──────────────────────────────────────────────────

/** Converts a domain [Contour] (normalized board space) into a Compose [Path]. */
internal fun Contour.toPath(): Path = Path().apply {
    moveTo(start.x, start.y)
    segments.forEach { seg ->
        when (seg) {
            is Seg.Line -> lineTo(seg.to.x, seg.to.y)
            is Seg.Cubic -> cubicTo(seg.c1.x, seg.c1.y, seg.c2.x, seg.c2.y, seg.to.x, seg.to.y)
        }
    }
    close()
}

/** Largest centered square of [image] — maps any aspect onto a square board. */
private fun slotSquareCrop(image: ImageBitmap): Pair<IntOffset, IntSize> {
    val side = minOf(image.width, image.height)
    return IntOffset((image.width - side) / 2, (image.height - side) / 2) to IntSize(side, side)
}

/**
 * Draws the image clipped to [path], assuming the current transform maps board space `[0,1]²` onto
 * the draw area (i.e. a `scale(boardPx)` is in effect). The whole image fills the board; the clip
 * restricts it to this slot.
 */
private fun DrawScope.drawSlotFragment(image: ImageBitmap, path: Path, alpha: Float) {
    val (srcOffset, srcSize) = slotSquareCrop(image)
    clipPath(path) {
        drawImage(
            image = image,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(1, 1),
            alpha = alpha,
        )
    }
}

// ── Board ────────────────────────────────────────────────────────────────────

/**
 * Single-Canvas board for an arbitrary [SlotLayout]. Empty slots show a ghost outline (the target
 * slot for the selected/dragged piece is highlighted); placed slots show their image fragment,
 * animated in via a per-slot [Animatable] read in the draw lambda (draw-phase only, no recompose).
 * Taps report the normalized hit point via [onTapNorm].
 */
@Composable
fun SlotPuzzleBoard(
    layout: SlotLayout,
    placed: Set<Int>,
    highlightPieceId: Int?,
    placementStyle: PlacementStyle,
    image: ImageBitmap?,
    onTapNorm: (nx: Float, ny: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paths = remember(layout) { layout.slots.associate { it.id to it.contour.toPath() } }
    val alphaMap = remember(layout) { HashMap<Int, Animatable<Float, *>>() }
    val prevPlaced = remember(layout) { placed.toMutableSet() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(placed) {
        val delta = placed - prevPlaced
        prevPlaced.clear()
        prevPlaced.addAll(placed)
        delta.forEach { id ->
            val anim = Animatable(0f)
            alphaMap[id] = anim
            scope.launch { anim.animateTo(1f, animationSpec = tween(PLACE_DURATION_MS)) }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(layout) {
            detectTapGestures { offset ->
                onTapNorm(offset.x / size.width, offset.y / size.height)
            }
        },
    ) {
        val s = size.minDimension
        // Faint preview of the whole picture (drawn unscaled, in px).
        if (image != null) drawBoardImage(image, s.toInt(), s.toInt(), alpha = 0.12f)

        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            val ghostStroke = 0.012f
            val outlineStroke = 0.008f
            layout.slots.forEach { slot ->
                val path = paths[slot.id] ?: return@forEach
                val isPlaced = slot.id in placed
                val isTarget = highlightPieceId != null && slot.id == highlightPieceId

                if (isPlaced) {
                    val a = alphaMap[slot.id]?.value ?: 1f
                    val pop = if (placementStyle == PlacementStyle.FADE_AND_POP) 0.9f + a * 0.1f else 1f
                    if (pop != 1f) {
                        withTransform({
                            scale(pop, pop, pivot = Offset(slot.bounds.centerX, slot.bounds.centerY))
                        }) {
                            drawPlacedSlot(image, path, a, outlineStroke)
                        }
                    } else {
                        drawPlacedSlot(image, path, a, outlineStroke)
                    }
                } else if (isTarget) {
                    drawPath(path, color = TargetHighlight, alpha = 0.12f)
                    drawPath(path, color = TargetHighlight, style = Stroke(width = ghostStroke * 1.4f))
                } else {
                    drawPath(path, color = GhostStroke, style = Stroke(width = ghostStroke))
                }
            }
        }
    }
}

private fun DrawScope.drawPlacedSlot(image: ImageBitmap?, path: Path, alpha: Float, outlineStroke: Float) {
    if (image != null) {
        drawSlotFragment(image, path, alpha)
    } else {
        clipPath(path) { drawRect(color = Color(0xFF9E9E9E), alpha = alpha * 0.6f) }
    }
    drawPath(path, color = Color.White, alpha = 0.7f * alpha, style = Stroke(width = outlineStroke))
}

// ── A single slot piece (tray thumbnail / floating drag piece) ───────────────

@Composable
fun SlotPieceImage(
    slot: PieceSlot,
    image: ImageBitmap?,
    locked: Boolean,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val path = remember(slot) { slot.contour.toPath() }
    Canvas(modifier = modifier) {
        val b = slot.bounds
        val maxDim = max(b.width, b.height)
        val scale = size.minDimension * 0.86f / maxDim
        val ox = (size.width - b.width * scale) / 2f - b.left * scale
        val oy = (size.height - b.height * scale) / 2f - b.top * scale
        withTransform({
            translate(ox, oy)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            if (elevated) {
                withTransform({ translate(0.02f, 0.03f) }) {
                    drawPath(path, color = Color.Black, alpha = 0.28f)
                }
            }
            when {
                locked -> drawPath(path, color = Color(0xFFBDBDBD), alpha = 0.5f)
                image != null -> {
                    drawSlotFragment(image, path, alpha = 1f)
                    drawPath(path, color = Color.White, alpha = 0.8f, style = Stroke(width = 0.012f))
                }
                else -> drawPath(path, color = Color(0xFF9E9E9E))
            }
        }
    }
}

// ── Tray ─────────────────────────────────────────────────────────────────────

@Composable
private fun SlotPieceTray(
    layout: SlotLayout,
    unlocked: Set<Int>,
    placed: Set<Int>,
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
    val visible = remember(layout, placed) { layout.slots.filter { it.id !in placed } }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(visible, key = { it.id }) { slot ->
            SlotTrayItem(
                slot = slot,
                image = image,
                unlocked = slot.id in unlocked,
                selected = slot.id == selectedPieceId,
                hidden = slot.id == draggingId,
                onSelect = { onSelect(slot.id) },
                onDragStart = { pos -> onDragStart(slot.id, pos) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onPlaceAccessible = { onPlaceAccessible(slot.id) },
            )
        }
    }
}

@Composable
private fun SlotTrayItem(
    slot: PieceSlot,
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
    val borderColor = if (selected) Color(0xFF34C759) else MaterialTheme.colorScheme.outlineVariant
    var itemRoot by remember { mutableStateOf(Offset.Zero) }

    Surface(
        modifier = Modifier
            .size(TRAY_ITEM_SIZE)
            .onGloballyPositioned { itemRoot = it.positionInRoot() }
            .semantics {
                contentDescription = if (unlocked) {
                    "Piece ${slot.id}${if (selected) ", selected" else ""}"
                } else {
                    "Piece ${slot.id}, locked"
                }
                if (unlocked) {
                    customActions = listOf(
                        CustomAccessibilityAction("Place on board") { onPlaceAccessible(); true },
                    )
                }
            }
            .then(
                if (unlocked) Modifier.pointerInput(slot.id) {
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
            if (!hidden) {
                SlotPieceImage(
                    slot = slot,
                    image = image,
                    locked = !unlocked,
                    modifier = Modifier.fillMaxSize().aspectRatio(1f),
                )
            }
        }
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

/** Root entry: collects ViewModel state and delegates to the stateless content. */
@Composable
fun SlotPuzzleScreen(
    viewModel: SlotPuzzleViewModel,
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    SlotPuzzleScreenContent(
        layout = viewModel.layout,
        state = state,
        image = image,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
fun SlotPuzzleScreenContent(
    layout: SlotLayout,
    state: SlotPuzzleUiState,
    image: ImageBitmap?,
    onIntent: (SlotIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressDescription = "${state.placedCount} of ${state.total} pieces placed"

    var boxRoot by remember { mutableStateOf(Offset.Zero) }
    var boardRect by remember { mutableStateOf<Rect?>(null) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    val haptics = LocalHapticFeedback.current

    fun feedback(targetId: Int, pieceId: Int) {
        if (targetId == pieceId) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        else haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    fun endDrag() {
        val id = draggingId
        val rect = boardRect
        if (id != null && rect != null && rect.contains(dragPos)) {
            val nx = (dragPos.x - rect.left) / rect.width
            val ny = (dragPos.y - rect.top) / rect.height
            feedback(layout.nearestSlotId(nx, ny), id)
            onIntent(SlotIntent.PlacePieceAt(id, nx, ny))
        }
        draggingId = null
    }

    Box(modifier = modifier.onGloballyPositioned { boxRoot = it.positionInRoot() }) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Progress header
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
                    modifier = Modifier.align(Alignment.End),
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (state.total > 0) state.placedCount.toFloat() / state.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Board card
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
                    SlotPuzzleBoard(
                        layout = layout,
                        placed = state.placed,
                        highlightPieceId = draggingId ?: state.selectedPieceId,
                        placementStyle = state.placementStyle,
                        image = image,
                        onTapNorm = { nx, ny ->
                            state.selectedPieceId?.let { id ->
                                feedback(layout.nearestSlotId(nx, ny), id)
                                onIntent(SlotIntent.PlaceSelectedAt(nx, ny))
                            }
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

            // Reward CTA
            if (state.isComplete && state.reward != null) {
                Button(
                    onClick = { onIntent(SlotIntent.RewardTapped) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Claim reward: ${state.reward.label}" },
                ) {
                    Text(text = state.reward.ctaText)
                }
            }

            // Tray card
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
                    SlotPieceTray(
                        layout = layout,
                        unlocked = state.unlocked,
                        placed = state.placed,
                        selectedPieceId = state.selectedPieceId,
                        image = image,
                        onSelect = { onIntent(SlotIntent.Select(it)) },
                        onDragStart = { id, pos -> draggingId = id; dragPos = pos },
                        onDrag = { pos -> dragPos = pos },
                        onDragEnd = { endDrag() },
                        onPlaceAccessible = { id ->
                            val slot = layout.slotById(id)
                            feedback(id, id)
                            onIntent(SlotIntent.PlacePieceAt(id, slot.anchor.x, slot.anchor.y))
                        },
                        draggingId = draggingId,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Floating piece that follows the finger during a drag
        val id = draggingId
        val draggedSlot = id?.let { layout.slots.firstOrNull { s -> s.id == it } }
        if (draggedSlot != null) {
            val halfPx = with(LocalDensity.current) { TRAY_ITEM_SIZE.toPx() } / 2f
            Box(
                modifier = Modifier
                    .offset {
                        val local = dragPos - boxRoot
                        IntOffset((local.x - halfPx).roundToInt(), (local.y - halfPx).roundToInt())
                    }
                    .size(TRAY_ITEM_SIZE),
            ) {
                SlotPieceImage(
                    slot = draggedSlot,
                    image = image,
                    locked = false,
                    elevated = true,
                    modifier = Modifier.fillMaxSize().aspectRatio(1f),
                )
            }
        }
    }
}
