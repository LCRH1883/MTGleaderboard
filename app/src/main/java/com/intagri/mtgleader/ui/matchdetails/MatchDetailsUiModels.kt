package com.intagri.mtgleader.ui.matchdetails

data class MatchDetailsUiModel(
    val tabletopType: String,
    val playersCount: Int,
    val durationLabel: String,
    val pendingSync: Boolean,
    val winnerLabel: String,
    val placements: List<MatchPlacementUiModel>,
)

data class MatchPlacementUiModel(
    val placeLabel: String,
    val displayName: String,
    val eliminationLabel: String,
    val turnStatsLabel: String,
)
