package com.intagri.mtgleader.ui.stats

data class StatsOwnerOption(
    val label: String,
    val ownerType: String,
    val ownerId: String,
)

data class StatsSummaryUiModel(
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val winPercentLabel: String,
    val subtitle: String?,
)
