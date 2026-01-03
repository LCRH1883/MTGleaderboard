package com.intagri.mtgleader.persistence.stats.local

data class LocalStatsSummary(
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
)

data class LocalHeadToHead(
    val opponentType: String,
    val opponentId: String,
    val opponentDisplayName: String,
    val wins: Int,
    val losses: Int,
)
