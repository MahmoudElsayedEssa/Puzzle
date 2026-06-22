package com.moe.puzzle.feature.puzzle.presentation

import com.moe.puzzle.feature.puzzle.domain.CampaignEventSink
import com.moe.puzzle.feature.puzzle.domain.CampaignId
import com.moe.puzzle.feature.puzzle.domain.GridCell
import com.moe.puzzle.feature.puzzle.domain.GridSpec
import com.moe.puzzle.feature.puzzle.domain.PuzzleConfig
import com.moe.puzzle.feature.puzzle.domain.PuzzleEvent
import com.moe.puzzle.feature.puzzle.domain.PuzzleProgress
import com.moe.puzzle.feature.puzzle.domain.RewardDisplay
import com.moe.puzzle.feature.puzzle.domain.RewardHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PuzzleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val emittedEvents = mutableListOf<PuzzleEvent>()
    private val completedCampaigns = mutableListOf<CampaignId>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        emittedEvents.clear()
        completedCampaigns.clear()
    }

    private fun makeVm(
        rows: Int = 2,
        cols: Int = 2,
        unlocked: Set<Int> = setOf(0, 1, 2, 3),
        reward: RewardDisplay? = null,
    ) = PuzzleViewModel(
        config = PuzzleConfig(
            campaignId = CampaignId("test"),
            grid = GridSpec(rows, cols),
            progress = PuzzleProgress(unlocked),
            reward = reward,
        ),
        eventSink = CampaignEventSink { emittedEvents += it },
        rewardHandler = RewardHandler { completedCampaigns += it },
    )

    @Test
    fun initialState_matchesConfig() = runTest {
        val vm = makeVm(unlocked = setOf(0, 1))
        val state = vm.state.value
        assertEquals(setOf(0, 1), state.unlocked.toSet())
        assertTrue(state.placed.isEmpty())
        assertEquals(0, state.placedCount)
        assertEquals(4, state.total)
        assertFalse(state.isComplete)
        assertNull(state.selectedPieceId)
    }

    @Test
    fun unlockNext_addsLowestLockedPieceToTray() = runTest {
        val vm = makeVm(unlocked = setOf(0))
        vm.onIntent(PuzzleIntent.UnlockNext)
        advanceUntilIdle()
        assertEquals(setOf(0, 1), vm.state.value.unlocked.toSet())
    }

    @Test
    fun selectPiece_onlyUnlockedSelectable() = runTest {
        val vm = makeVm(unlocked = setOf(0))
        vm.onIntent(PuzzleIntent.SelectPiece(3)) // locked
        advanceUntilIdle()
        assertNull(vm.state.value.selectedPieceId)

        vm.onIntent(PuzzleIntent.SelectPiece(0))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.selectedPieceId)
    }

    @Test
    fun selectPiece_tappingSelectedAgainDeselects() = runTest {
        val vm = makeVm()
        vm.onIntent(PuzzleIntent.SelectPiece(2))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.selectedPieceId)
        vm.onIntent(PuzzleIntent.SelectPiece(2))
        advanceUntilIdle()
        assertNull(vm.state.value.selectedPieceId)
    }

    @Test
    fun placeAt_correctSlot_placesPieceAndClearsSelection() = runTest {
        val vm = makeVm()
        vm.onIntent(PuzzleIntent.SelectPiece(0))
        vm.onIntent(PuzzleIntent.PlaceAt(GridCell(0, 0))) // id 0
        advanceUntilIdle()
        assertTrue(0 in vm.state.value.placed)
        assertEquals(1, vm.state.value.placedCount)
        assertNull(vm.state.value.selectedPieceId)
    }

    @Test
    fun placeAt_wrongSlot_keepsSelectionAndDoesNotPlace() = runTest {
        val vm = makeVm()
        vm.onIntent(PuzzleIntent.SelectPiece(0))
        vm.onIntent(PuzzleIntent.PlaceAt(GridCell(1, 1))) // id 3, wrong
        advanceUntilIdle()
        assertTrue(vm.state.value.placed.isEmpty())
        assertEquals(0, vm.state.value.selectedPieceId)
    }

    @Test
    fun placeAt_withoutSelection_isNoOp() = runTest {
        val vm = makeVm()
        vm.onIntent(PuzzleIntent.PlaceAt(GridCell(0, 0)))
        advanceUntilIdle()
        assertTrue(vm.state.value.placed.isEmpty())
    }

    @Test
    fun placingAllPieces_completesBoard() = runTest {
        val vm = makeVm()
        listOf(GridCell(0, 0), GridCell(0, 1), GridCell(1, 0), GridCell(1, 1)).forEach { cell ->
            vm.onIntent(PuzzleIntent.SelectPiece(2 * cell.row + cell.col))
            vm.onIntent(PuzzleIntent.PlaceAt(cell))
            advanceUntilIdle()
        }
        assertTrue(vm.state.value.isComplete)
        assertEquals(4, vm.state.value.placedCount)
        assertEquals(1, completedCampaigns.size)
    }

    @Test
    fun placePiece_correctSlot_placesWithoutPriorSelection() = runTest {
        val vm = makeVm()
        vm.onIntent(PuzzleIntent.PlacePiece(2, GridCell(1, 0))) // id 2 = (1,0)
        advanceUntilIdle()
        assertTrue(2 in vm.state.value.placed)
        assertEquals(1, vm.state.value.placedCount)
    }

    @Test
    fun placePiece_wrongSlot_isNoOp() = runTest {
        val vm = makeVm()
        vm.onIntent(PuzzleIntent.PlacePiece(2, GridCell(0, 0))) // wrong cell
        advanceUntilIdle()
        assertTrue(vm.state.value.placed.isEmpty())
    }

    @Test
    fun placePiece_lockedPiece_isNoOp() = runTest {
        val vm = makeVm(unlocked = setOf(0))
        vm.onIntent(PuzzleIntent.PlacePiece(3, GridCell(1, 1))) // 3 is locked
        advanceUntilIdle()
        assertTrue(vm.state.value.placed.isEmpty())
    }

    /** Regression: dragging a piece that was already tap-selected must still place it. */
    @Test
    fun placePiece_ofAlreadySelectedPiece_stillPlaces() = runTest {
        val vm = makeVm()
        vm.onIntent(PuzzleIntent.SelectPiece(0))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.selectedPieceId)

        vm.onIntent(PuzzleIntent.PlacePiece(0, GridCell(0, 0)))
        advanceUntilIdle()
        assertTrue(0 in vm.state.value.placed)
        assertNull(vm.state.value.selectedPieceId)
    }

    @Test
    fun reset_clearsPlacedAndSelection() = runTest {
        val vm = makeVm()
        vm.onIntent(PuzzleIntent.SelectPiece(0))
        vm.onIntent(PuzzleIntent.PlaceAt(GridCell(0, 0)))
        advanceUntilIdle()
        vm.onIntent(PuzzleIntent.Reset)
        advanceUntilIdle()
        assertEquals(0, vm.state.value.placedCount)
        assertFalse(vm.state.value.isComplete)
        assertTrue(vm.state.value.placed.isEmpty())
        assertNull(vm.state.value.selectedPieceId)
    }

    @Test
    fun effects_pieceUnlockedEmitted() = runTest {
        val vm = makeVm(unlocked = setOf(0))
        val effects = mutableListOf<PuzzleEffect>()
        val job = launch { vm.effects.collect { effects += it } }

        vm.onIntent(PuzzleIntent.UnlockNext)
        advanceUntilIdle()

        val events = effects.filterIsInstance<PuzzleEffect.Emit>().map { it.event }
        assertTrue(events.any { it is PuzzleEvent.PieceUnlocked })
        job.cancel()
    }

    @Test
    fun effects_piecePlacedAndCompletedEmitted() = runTest {
        val vm = makeVm()
        val effects = mutableListOf<PuzzleEffect>()
        val job = launch { vm.effects.collect { effects += it } }

        listOf(GridCell(0, 0), GridCell(0, 1), GridCell(1, 0), GridCell(1, 1)).forEach { cell ->
            vm.onIntent(PuzzleIntent.SelectPiece(2 * cell.row + cell.col))
            vm.onIntent(PuzzleIntent.PlaceAt(cell))
            advanceUntilIdle()
        }

        val events = effects.filterIsInstance<PuzzleEffect.Emit>().map { it.event }
        assertTrue(events.any { it is PuzzleEvent.PiecePlaced })
        assertTrue(events.any { it is PuzzleEvent.PuzzleCompleted })
        job.cancel()
    }

    @Test
    fun effects_wrongPlacementEmitted() = runTest {
        val vm = makeVm()
        val effects = mutableListOf<PuzzleEffect>()
        val job = launch { vm.effects.collect { effects += it } }

        vm.onIntent(PuzzleIntent.SelectPiece(0))
        vm.onIntent(PuzzleIntent.PlaceAt(GridCell(1, 1)))
        advanceUntilIdle()

        val events = effects.filterIsInstance<PuzzleEffect.Emit>().map { it.event }
        assertTrue(events.any { it is PuzzleEvent.WrongPlacement })
        job.cancel()
    }

    @Test
    fun effects_rewardTappedEmitted() = runTest {
        val vm = makeVm()
        val effects = mutableListOf<PuzzleEffect>()
        val job = launch { vm.effects.collect { effects += it } }

        vm.onIntent(PuzzleIntent.RewardTapped)
        advanceUntilIdle()

        val events = effects.filterIsInstance<PuzzleEffect.Emit>().map { it.event }
        assertTrue(events.any { it is PuzzleEvent.RewardTapped })
        job.cancel()
    }
}
