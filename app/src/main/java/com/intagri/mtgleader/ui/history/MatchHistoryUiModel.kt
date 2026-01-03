package com.intagri.mtgleader.ui.history

data class MatchHistoryUiModel(
    val localMatchId: String,
    val title: String,
    val subtitle: String,
    val playersCount: Int,
    val winnerNames: List<String>,
    val durationLabel: String,
    val pendingSync: Boolean,
)
