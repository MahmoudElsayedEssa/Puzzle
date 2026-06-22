package com.moe.puzzle.feature.puzzle.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moe.puzzle.feature.puzzle.domain.CampaignEventSink
import com.moe.puzzle.feature.puzzle.domain.GeneratePiecesUseCase
import com.moe.puzzle.feature.puzzle.domain.GridCell
import com.moe.puzzle.feature.puzzle.domain.PuzzleConfig
import com.moe.puzzle.feature.puzzle.domain.PuzzleEvent
import com.moe.puzzle.feature.puzzle.domain.PuzzleProgress
import com.moe.puzzle.feature.puzzle.domain.UnlockEngine
import com.moe.puzzle.feature.puzzle.domain.RewardHandler
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
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
 * Tap-to-place jigsaw puzzle ViewModel.
 *
 * Two independent piece sets:
 *  - [PuzzleUiState.unlocked] — pieces made available in the tray via progressive [PuzzleIntent.UnlockNext].
 *  - [PuzzleUiState.placed]   — pieces the user has tapped into their correct board slot.
 *
 * Flow: UnlockNext → piece appears in tray → SelectPiece → PlaceAt(correct cell) → placed.
 * Completion (and the reward) fires when every piece is placed.
 */
class PuzzleViewModel(
    private val config: PuzzleConfig,
    private val eventSink: CampaignEventSink,
    private val rewardHandler: RewardHandler,
    private val generatePieces: GeneratePiecesUseCase = GeneratePiecesUseCase(),
) : ViewModel() {

    // Tracks the unlock progression so duplicate unlocks don't re-emit events.
    private val unlockEngine = UnlockEngine(
        totalPieces = config.grid.totalPieces,
        initial = config.progress.unlockedPieceIds,
    )

    private val pieces = generatePieces(config).toImmutableList()

    private val _state = MutableStateFlow(
        PuzzleUiState(
            grid = config.grid,
            pieces = pieces,
            unlocked = config.progress.unlockedPieceIds.toImmutableSet(),
            placed = persistentSetOf(),
            selectedPieceId = null,
            placementStyle = config.placementStyle,
            placedCount = 0,
            total = config.grid.totalPieces,
            isComplete = false,
            reward = config.reward,
        )
    )
    val state: StateFlow<PuzzleUiState> = _state.asStateFlow()

    private val _effects = Channel<PuzzleEffect>(Channel.BUFFERED)
    val effects: Flow<PuzzleEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: PuzzleIntent) {
        when (intent) {
            is PuzzleIntent.UnlockNext -> unlockNext()
            is PuzzleIntent.SelectPiece -> selectPiece(intent.id)
            is PuzzleIntent.PlaceAt -> _state.value.selectedPieceId?.let { place(it, intent.cell) }
            is PuzzleIntent.PlacePiece -> place(intent.id, intent.cell)
            is PuzzleIntent.Reset -> reset()
            is PuzzleIntent.RewardTapped -> handleRewardTapped()
        }
    }

    private fun unlockNext() {
        val current = _state.value
        if (current.unlocked.size >= config.grid.totalPieces) return
        val nextId = (0 until config.grid.totalPieces).first { it !in current.unlocked }
        val newUnlocked = current.unlocked + nextId

        val delta = unlockEngine.update(PuzzleProgress(newUnlocked))
        _state.update { it.copy(unlocked = newUnlocked.toImmutableSet()) }

        emit {
            delta.newlyUnlocked.forEach { id ->
                _effects.send(emitEvent(PuzzleEvent.PieceUnlocked(id)))
                eventSink.emit(PuzzleEvent.PieceUnlocked(id))
            }
        }
    }

    private fun selectPiece(id: Int) {
        val current = _state.value
        // Only unlocked, not-yet-placed pieces can be selected.
        if (id !in current.unlocked || id in current.placed) return
        val newSelection = if (current.selectedPieceId == id) null else id
        _state.update { it.copy(selectedPieceId = newSelection) }
    }

    /**
     * Attempts to place [id] at [cell]. Shared by both the tap flow (via the current selection)
     * and the drag flow (the dragged piece). A piece only lands in its own canonical slot;
     * anything else emits [PuzzleEvent.WrongPlacement] and leaves state untouched.
     */
    private fun place(id: Int, cell: GridCell) {
        val current = _state.value
        if (id !in current.unlocked || id in current.placed) return
        val targetId = config.grid.idOf(cell)

        if (targetId != id) {
            emit {
                _effects.send(emitEvent(PuzzleEvent.WrongPlacement(id, targetId)))
                eventSink.emit(PuzzleEvent.WrongPlacement(id, targetId))
            }
            return
        }

        val newPlaced = current.placed + id
        val complete = newPlaced.size == config.grid.totalPieces
        _state.update {
            it.copy(
                placed = newPlaced.toImmutableSet(),
                placedCount = newPlaced.size,
                selectedPieceId = if (it.selectedPieceId == id) null else it.selectedPieceId,
                isComplete = complete,
            )
        }

        emit {
            _effects.send(emitEvent(PuzzleEvent.PiecePlaced(id)))
            _effects.send(emitEvent(PuzzleEvent.ProgressChanged(newPlaced.size, config.grid.totalPieces)))
            eventSink.emit(PuzzleEvent.PiecePlaced(id))
            if (complete) {
                _effects.send(emitEvent(PuzzleEvent.PuzzleCompleted))
                eventSink.emit(PuzzleEvent.PuzzleCompleted)
                rewardHandler.onCompleted(config.campaignId)
            }
        }
    }

    private fun reset() {
        unlockEngine.reset()
        _state.update {
            it.copy(
                unlocked = config.progress.unlockedPieceIds.toImmutableSet(),
                placed = persistentSetOf(),
                placedCount = 0,
                selectedPieceId = null,
                isComplete = false,
            )
        }
    }

    private fun handleRewardTapped() {
        emit {
            _effects.send(emitEvent(PuzzleEvent.RewardTapped(config.campaignId.value)))
            eventSink.emit(PuzzleEvent.RewardTapped(config.campaignId.value))
        }
    }

    private fun emitEvent(event: PuzzleEvent) = PuzzleEffect.Emit(event)

    private fun emit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
