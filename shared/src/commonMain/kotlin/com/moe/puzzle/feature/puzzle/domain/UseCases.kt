package com.moe.puzzle.feature.puzzle.domain

class GeneratePiecesUseCase {
    operator fun invoke(config: PuzzleConfig): List<PuzzlePiece> =
        generatePieces(config.grid, config.edgeStrategy, config.edgeSeed)
}

class ComputeUnlockDeltaUseCase(private val engine: UnlockEngine) {
    operator fun invoke(progress: PuzzleProgress): UnlockDelta = engine.update(progress)
}

class IsCampaignCompleteUseCase {
    operator fun invoke(progress: PuzzleProgress, grid: GridSpec): Boolean =
        progress.unlockedPieceIds.size == grid.totalPieces
}
