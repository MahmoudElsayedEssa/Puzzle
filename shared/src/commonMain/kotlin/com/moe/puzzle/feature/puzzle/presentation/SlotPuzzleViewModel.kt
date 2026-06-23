package com.moe.puzzle.feature.puzzle.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moe.puzzle.feature.puzzle.domain.CampaignEventSink
import com.moe.puzzle.feature.puzzle.domain.CampaignId
import com.moe.puzzle.feature.puzzle.domain.PlacementStyle
import com.moe.puzzle.feature.puzzle.domain.PuzzleEvent
import com.moe.puzzle.feature.puzzle.domain.PuzzleProgress
import com.moe.puzzle.feature.puzzle.domain.RewardDisplay
import com.moe.puzzle.feature.puzzle.domain.RewardHandler
import com.moe.puzzle.feature.puzzle.domain.UnlockEngine
import com.moe.puzzle.feature.puzzle.domain.slot.SlotLayout
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Layout-agnostic puzzle ViewModel — drives any [SlotLayout] (grid, 2×2 + center, …).
 *
 * Placement is resolved by *nearest slot anchor* rather than a grid cell, so the same logic works
 * for irregular layouts. A piece only lands in its own slot (`id == targetSlotId`); anything else
 * emits [PuzzleEvent.WrongPlacement] and leaves state untouched. Completion fires when every slot is
 * filled. Reuses the existing [UnlockEngine], [PuzzleEvent] and ports.
 */
class SlotPuzzleViewModel(
    val layout: SlotLayout,
    private val campaignId: CampaignId,
    initialUnlocked: Set<Int>,
    placementStyle: PlacementStyle,
    reward: RewardDisplay?,
    private val eventSink: CampaignEventSink,
    private val rewardHandler: RewardHandler,
) : ViewModel() {

    private val total = layout.totalPieces
    private val initial = initialUnlocked.toSet()
    private val unlockEngine = UnlockEngine(totalPieces = total, initial = initial)

    private val _state = MutableStateFlow(
        SlotPuzzleUiState(
            unlocked = initial.toImmutableSet(),
            placed = persistentSetOf(),
            selectedPieceId = null,
            placementStyle = placementStyle,
            placedCount = 0,
            total = total,
            isComplete = false,
            reward = reward,
        )
    )
    val state: StateFlow<SlotPuzzleUiState> = _state.asStateFlow()

    private val _effects = Channel<PuzzleEffect>(Channel.BUFFERED)
    val effects: Flow<PuzzleEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: SlotIntent) {
        when (intent) {
            is SlotIntent.UnlockNext -> unlockNext()
            is SlotIntent.Select -> select(intent.id)
            is SlotIntent.PlaceSelectedAt ->
                _state.value.selectedPieceId?.let { place(it, layout.nearestSlotId(intent.nx, intent.ny)) }
            is SlotIntent.PlacePieceAt -> place(intent.id, layout.nearestSlotId(intent.nx, intent.ny))
            is SlotIntent.Reset -> reset()
            is SlotIntent.RewardTapped -> emit {
                _effects.send(PuzzleEffect.Emit(PuzzleEvent.RewardTapped(campaignId.value)))
                eventSink.emit(PuzzleEvent.RewardTapped(campaignId.value))
            }
        }
    }

    private fun unlockNext() {
        val current = _state.value
        if (current.unlocked.size >= total) return
        val nextId = (0 until total).first { it !in current.unlocked }
        val newUnlocked = current.unlocked + nextId
        val delta = unlockEngine.update(PuzzleProgress(newUnlocked))
        _state.update { it.copy(unlocked = newUnlocked.toImmutableSet()) }
        emit {
            delta.newlyUnlocked.forEach { id ->
                _effects.send(PuzzleEffect.Emit(PuzzleEvent.PieceUnlocked(id)))
                eventSink.emit(PuzzleEvent.PieceUnlocked(id))
            }
        }
    }

    private fun select(id: Int) {
        val current = _state.value
        if (id !in current.unlocked || id in current.placed) return
        _state.update { it.copy(selectedPieceId = if (current.selectedPieceId == id) null else id) }
    }

    private fun place(id: Int, targetSlotId: Int) {
        val current = _state.value
        if (id !in current.unlocked || id in current.placed) return

        if (targetSlotId != id) {
            emit {
                _effects.send(PuzzleEffect.Emit(PuzzleEvent.WrongPlacement(id, targetSlotId)))
                eventSink.emit(PuzzleEvent.WrongPlacement(id, targetSlotId))
            }
            return
        }

        val newPlaced = current.placed + id
        val complete = newPlaced.size == total
        _state.update {
            it.copy(
                placed = newPlaced.toImmutableSet(),
                placedCount = newPlaced.size,
                selectedPieceId = if (it.selectedPieceId == id) null else it.selectedPieceId,
                isComplete = complete,
            )
        }
        emit {
            _effects.send(PuzzleEffect.Emit(PuzzleEvent.PiecePlaced(id)))
            _effects.send(PuzzleEffect.Emit(PuzzleEvent.ProgressChanged(newPlaced.size, total)))
            eventSink.emit(PuzzleEvent.PiecePlaced(id))
            if (complete) {
                _effects.send(PuzzleEffect.Emit(PuzzleEvent.PuzzleCompleted))
                eventSink.emit(PuzzleEvent.PuzzleCompleted)
                rewardHandler.onCompleted(campaignId)
            }
        }
    }

    private fun reset() {
        unlockEngine.reset()
        _state.update {
            it.copy(
                unlocked = initial.toImmutableSet(),
                placed = persistentSetOf(),
                placedCount = 0,
                selectedPieceId = null,
                isComplete = false,
            )
        }
    }

    private fun emit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Immutable
data class SlotPuzzleUiState(
    val unlocked: ImmutableSet<Int>,
    val placed: ImmutableSet<Int>,
    val selectedPieceId: Int?,
    val placementStyle: PlacementStyle,
    val placedCount: Int,
    val total: Int,
    val isComplete: Boolean,
    val reward: RewardDisplay?,
)

sealed interface SlotIntent {
    /** Progressive unlock: make the next locked piece available in the tray. */
    data object UnlockNext : SlotIntent

    /** Tap a tray piece to select it (tapping the selected piece again deselects). */
    data class Select(val id: Int) : SlotIntent

    /** Tap the board at a normalized point to place the selected piece (tap-to-place flow). */
    data class PlaceSelectedAt(val nx: Float, val ny: Float) : SlotIntent

    /** Drop a specific piece at a normalized board point (drag-and-drop flow). */
    data class PlacePieceAt(val id: Int, val nx: Float, val ny: Float) : SlotIntent

    data object Reset : SlotIntent
    data object RewardTapped : SlotIntent
}
