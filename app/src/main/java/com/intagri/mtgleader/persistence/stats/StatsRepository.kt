package com.intagri.mtgleader.persistence.stats

class StatsRepository(
    private val statsApi: StatsApi
) {
    suspend fun getSummary(): StatsSummaryDto {
        return statsApi.getSummary()
    }

    suspend fun getHeadToHead(opponentId: String): HeadToHeadDto {
        return statsApi.getHeadToHead(opponentId)
    }
}
